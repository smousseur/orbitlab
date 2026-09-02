package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Recovers the body-fixed frame a textured sphere carries in its own geometry, from its vertex
 * positions and UV coordinates alone — no reference image, no visual adjustment.
 *
 * <p>See {@code docs/orientation-planetes/01-decoupage.md}, L0.
 */
public final class MeshFrameProbe {

  /**
   * Half-width of the band, as a fraction of the mesh's own {@code v} range, from which the pole
   * direction is averaged. Wide enough to catch the whole polar ring of a coarse sphere, narrow
   * enough to exclude the next ring down: the coarsest planetary asset in the repo (Mercury) still
   * spaces its rings far wider than this.
   */
  private static final float POLE_BAND = 0.02f;

  /** Same idea for the equatorial band from which the {@code u = 0} direction is averaged. */
  private static final float EQUATOR_BAND = 0.01f;

  /** Tolerance on {@code u} when selecting the column of lowest {@code u}. */
  private static final float COLUMN_TOLERANCE = 1e-4f;

  /**
   * Beyond this share of the variance lying along the fitted normal, the geometry is not flat and
   * has no plane worth reporting. Placed between the two measured populations rather than at a
   * round number: the repo's two rings score 9e-6 and 2e-15, its spheres about 0.2.
   */
  private static final float MAX_FLATNESS = 1e-3f;

  private MeshFrameProbe() {}

  /**
   * Measures every geometry under a loaded model, in the model root's own axes — that is, with each
   * geometry's node chain composed, so the directions are the ones the renderer will actually use
   * rather than the ones the raw mesh data happens to carry. The eleven planetary assets have
   * wildly different node chains for the same result (the Earth is a single node with an identity
   * rotation, the Moon a five-node Sketchfab chain), which is exactly why this composition has to
   * happen before anything is compared.
   *
   * <p>Only rotation is composed. Scale is uniform on every asset in the repo, and a direction does
   * not care about it; a future asset with a non-uniform scale would distort the sphere and show up
   * as a raised {@link MeshFrame#equirectangularResidualDeg()} rather than as a silent error.
   *
   * @param spatial the loaded model
   * @return one entry per geometry, in traversal order — including those nothing could be measured
   *     on, which are reported empty rather than dropped
   */
  public static List<ProbedGeometry> probe(Spatial spatial) {
    spatial.updateGeometricState();
    List<ProbedGeometry> probed = new ArrayList<>();
    spatial.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            MeshFrame frame =
                probe(geometry.getMesh())
                    .map(measured -> rotate(measured, geometry.getWorldRotation()))
                    .orElse(null);
            RingPlane ring =
                probeRing(geometry.getMesh())
                    .map(measured -> rotate(measured, geometry.getWorldRotation()))
                    .orElse(null);
            probed.add(new ProbedGeometry(geometry.getName(), frame, ring));
          }
        });
    return probed;
  }

  private static RingPlane rotate(RingPlane ring, Quaternion rotation) {
    return new RingPlane(rotation.mult(ring.normal()), ring.flatness());
  }

  private static MeshFrame rotate(MeshFrame frame, Quaternion rotation) {
    return new MeshFrame(
        rotation.mult(frame.pole()),
        rotation.mult(frame.primeMeridian()),
        frame.equirectangularResidualDeg(),
        frame.azimuthDegreesPerU());
  }

  /**
   * Measures the frame of a single mesh, in that mesh's own coordinates.
   *
   * @param mesh the mesh to probe
   * @return the measured frame, or empty when the mesh carries nothing to measure — no UV map, or a
   *     {@code v} range too degenerate to tell a pole from an equator
   */
  public static Optional<MeshFrame> probe(Mesh mesh) {
    FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
    FloatBuffer texCoords = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
    if (positions == null || texCoords == null) {
      return Optional.empty();
    }
    int count = Math.min(positions.limit() / 3, texCoords.limit() / 2);
    if (count == 0) {
      return Optional.empty();
    }

    float vMin = Float.POSITIVE_INFINITY;
    float vMax = Float.NEGATIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      float v = texCoords.get(i * 2 + 1);
      vMin = Math.min(vMin, v);
      vMax = Math.max(vMax, v);
    }
    float vRange = vMax - vMin;
    if (vRange <= COLUMN_TOLERANCE) {
      return Optional.empty();
    }

    Vector3f pole =
        meanDirection(positions, texCoords, count, vMin, vMin + POLE_BAND * vRange)
            .normalizeLocal();
    float vMid = (vMin + vMax) * 0.5f;
    float equatorLow = vMid - EQUATOR_BAND * vRange;
    float equatorHigh = vMid + EQUATOR_BAND * vRange;

    float uMin = Float.POSITIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      float v = texCoords.get(i * 2 + 1);
      if (v >= equatorLow && v <= equatorHigh) {
        uMin = Math.min(uMin, texCoords.get(i * 2));
      }
    }
    if (Float.isInfinite(uMin)) {
      return Optional.empty();
    }

    Vector3f primeMeridian = new Vector3f();
    int meridianSamples = 0;
    for (int i = 0; i < count; i++) {
      float v = texCoords.get(i * 2 + 1);
      float u = texCoords.get(i * 2);
      if (v >= equatorLow && v <= equatorHigh && Math.abs(u - uMin) <= COLUMN_TOLERANCE) {
        primeMeridian.addLocal(
            positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
        meridianSamples++;
      }
    }
    if (meridianSamples == 0) {
      return Optional.empty();
    }

    primeMeridian.normalizeLocal();
    float residual = equirectangularResidualDeg(positions, texCoords, count, pole, vMin, vRange);
    float chirality =
        azimuthDegreesPerU(
            positions, texCoords, count, pole, primeMeridian, equatorLow, equatorHigh);
    return Optional.of(new MeshFrame(pole, primeMeridian, residual, chirality));
  }

  /**
   * Measures the plane of a flat annulus — the one thing a ring can be wrong about, and the one
   * thing {@link #probe(Mesh)} cannot see, a ring having no lat/long unwrap to read.
   *
   * @param mesh the mesh to measure
   * @return its plane, or empty when the geometry is not flat enough to have one
   */
  public static Optional<RingPlane> probeRing(Mesh mesh) {
    FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
    if (positions == null) {
      return Optional.empty();
    }
    int count = positions.limit() / 3;
    if (count < 3) {
      return Optional.empty();
    }

    Vector3f centre = new Vector3f();
    for (int i = 0; i < count; i++) {
      centre.addLocal(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
    }
    centre.divideLocal(count);

    double[][] covariance = new double[3][3];
    Vector3f offset = new Vector3f();
    for (int i = 0; i < count; i++) {
      offset
          .set(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2))
          .subtractLocal(centre);
      float[] components = {offset.x, offset.y, offset.z};
      for (int row = 0; row < 3; row++) {
        for (int column = 0; column < 3; column++) {
          covariance[row][column] += (double) components[row] * components[column];
        }
      }
    }

    Vector3f normal = leastSpreadDirection(covariance);
    double alongNormal = 0;
    double total = 0;
    for (int i = 0; i < count; i++) {
      offset
          .set(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2))
          .subtractLocal(centre);
      float along = offset.dot(normal);
      alongNormal += (double) along * along;
      total += offset.lengthSquared();
    }
    if (total <= 0) {
      return Optional.empty();
    }
    float flatness = (float) (alongNormal / total);
    return flatness > MAX_FLATNESS
        ? Optional.empty()
        : Optional.of(new RingPlane(normal, flatness));
  }

  /**
   * The direction of least spread of a covariance matrix, which for a flat point set is the normal
   * of its plane.
   *
   * <p>Power iteration on {@code trace·I − C} rather than a library eigen-decomposition: the
   * dominant eigenvector of that matrix is the least one of {@code C}, the shift keeping every
   * eigenvalue non-negative, and three by three it converges in a handful of steps. It also keeps
   * this class free of a linear-algebra dependency it needs nowhere else.
   */
  private static Vector3f leastSpreadDirection(double[][] covariance) {
    double trace = covariance[0][0] + covariance[1][1] + covariance[2][2];
    // Deliberately not an axis: starting on one would be an exact eigenvector of a mesh built on
    // the axes, and power iteration cannot leave the eigenvector it starts on.
    double[] direction = {0.3, 0.5, 0.81};
    for (int iteration = 0; iteration < 128; iteration++) {
      double[] next = new double[3];
      for (int row = 0; row < 3; row++) {
        next[row] = trace * direction[row];
        for (int column = 0; column < 3; column++) {
          next[row] -= covariance[row][column] * direction[column];
        }
      }
      double norm = Math.sqrt(next[0] * next[0] + next[1] * next[1] + next[2] * next[2]);
      if (norm <= 0) {
        break;
      }
      for (int row = 0; row < 3; row++) {
        direction[row] = next[row] / norm;
      }
    }
    return new Vector3f((float) direction[0], (float) direction[1], (float) direction[2]);
  }

  /**
   * Slope of the azimuth about {@code pole} against {@code u}, taken as the median of the
   * consecutive slopes around the equatorial ring. The median rather than a fit: a seam column
   * duplicated at {@code u = 0} and {@code u = 1}, or a single malformed vertex, would drag a least
   * squares line but cannot move the middle of the distribution.
   */
  private static float azimuthDegreesPerU(
      FloatBuffer positions,
      FloatBuffer texCoords,
      int count,
      Vector3f pole,
      Vector3f primeMeridian,
      float equatorLow,
      float equatorHigh) {
    Vector3f quadrature = pole.cross(primeMeridian).normalizeLocal();
    List<float[]> ring = new ArrayList<>();
    Vector3f point = new Vector3f();
    for (int i = 0; i < count; i++) {
      float v = texCoords.get(i * 2 + 1);
      if (v < equatorLow || v > equatorHigh) {
        continue;
      }
      point.set(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
      float azimuth =
          FastMath.atan2(point.dot(quadrature), point.dot(primeMeridian)) * FastMath.RAD_TO_DEG;
      ring.add(new float[] {texCoords.get(i * 2), azimuth});
    }
    if (ring.size() < 4) {
      return Float.NaN;
    }
    ring.sort(Comparator.comparingDouble(sample -> sample[0]));

    List<Float> slopes = new ArrayList<>();
    for (int i = 1; i < ring.size(); i++) {
      float deltaU = ring.get(i)[0] - ring.get(i - 1)[0];
      if (deltaU <= COLUMN_TOLERANCE) {
        continue;
      }
      slopes.add(wrapDegrees(ring.get(i)[1] - ring.get(i - 1)[1]) / deltaU);
    }
    if (slopes.isEmpty()) {
      return Float.NaN;
    }
    Collections.sort(slopes);
    return slopes.get(slopes.size() / 2);
  }

  /**
   * Brings an angular difference back into {@code (−180, 180]}, so a seam does not read as a jump.
   */
  private static float wrapDegrees(float degrees) {
    float wrapped = degrees % 360f;
    if (wrapped > 180f) {
      wrapped -= 360f;
    } else if (wrapped <= -180f) {
      wrapped += 360f;
    }
    return wrapped;
  }

  /**
   * Mean departure, in degrees of latitude, between each vertex's actual latitude about {@code
   * pole} and the latitude an exact equirectangular map would give its {@code v}. Expressed in
   * degrees rather than in {@code v} units so the number is readable as an angle on the globe.
   */
  private static float equirectangularResidualDeg(
      FloatBuffer positions,
      FloatBuffer texCoords,
      int count,
      Vector3f pole,
      float vMin,
      float vRange) {
    Vector3f point = new Vector3f();
    double sum = 0.0;
    for (int i = 0; i < count; i++) {
      point.set(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
      float norm = point.length();
      if (norm <= COLUMN_TOLERANCE) {
        continue;
      }
      float sinLatitude = FastMath.clamp(point.dot(pole) / norm, -1f, 1f);
      float colatitudeFraction = (FastMath.HALF_PI - FastMath.asin(sinLatitude)) / FastMath.PI;
      float expectedV = vMin + colatitudeFraction * vRange;
      sum += Math.abs(texCoords.get(i * 2 + 1) - expectedV);
    }
    return (float) (sum / count * 180.0);
  }

  private static Vector3f meanDirection(
      FloatBuffer positions, FloatBuffer texCoords, int count, float vLow, float vHigh) {
    Vector3f sum = new Vector3f();
    for (int i = 0; i < count; i++) {
      float v = texCoords.get(i * 2 + 1);
      if (v >= vLow && v <= vHigh) {
        sum.addLocal(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
      }
    }
    return sum;
  }
}
