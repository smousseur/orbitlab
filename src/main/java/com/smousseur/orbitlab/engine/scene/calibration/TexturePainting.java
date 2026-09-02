package com.smousseur.orbitlab.engine.scene.calibration;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Where the rendering chain actually paints a body's texture, expressed in the body-fixed
 * longitudes Orekit works in. This is L2's ruler (see {@code
 * docs/orientation-planetes/01-decoupage.md}).
 *
 * <p><b>What makes it an instrument rather than a restatement.</b> The two halves come from
 * unrelated data. Where a texture column lands is the whole render chain evaluated forward —
 * measured mesh frame, alignment, λ0, the frame conversions of {@code RenderTransform}; what
 * longitude that direction <em>is</em> comes from Orekit's rotation alone. They agree only if every
 * link is right, and the amount by which they disagree is in the same unit as the correction, so it
 * can be transcribed rather than interpreted.
 *
 * <p>Nothing here takes a camera, and that is the point: comparing a screenshot against a reference
 * image cannot prove anything, since the camera's azimuth is a free parameter that will make any
 * longitude coincide with any other. A reading that cannot see the camera cannot be fooled by it.
 *
 * <p>The relation is affine in {@code u} because an equirectangular map is, so two evaluations
 * determine it. They are taken a quarter of a map apart rather than adjacent: near-coincident
 * samples would divide the float noise of the round trip by a small number, and a quarter turn is
 * also far enough from the seam to be unambiguous when wrapped.
 *
 * @param columnZeroLongitudeDeg body-fixed longitude, in {@code (−180, 180]}, at which the chain
 *     paints the texture's column {@code u = 0}
 * @param degreesPerColumn how much body-fixed longitude one full map spans, sign included. {@code
 *     +360} for every asset in the repo: longitude runs east as {@code u} advances
 */
public record TexturePainting(double columnZeroLongitudeDeg, double degreesPerColumn) {

  /** Second sample point, a quarter of a map east of the first. */
  private static final double PROBE_COLUMN = 0.25;

  /**
   * Measures where a body's texture is being painted.
   *
   * @param frame the frame the body's mesh was measured to carry
   * @param renderRotation the rotation the renderer is applying to that mesh, as produced by {@code
   *     RenderTransform#toRenderQuaternion} — the real one, not a reconstruction, so that a defect
   *     anywhere in the chain shows up here
   * @param rotationIcrf the body's ICRF-to-body-frame rotation at the same instant
   * @return the painting
   */
  public static TexturePainting measure(
      MeshFrame frame, Quaternion renderRotation, Rotation rotationIcrf) {
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(renderRotation, "renderRotation");
    Objects.requireNonNull(rotationIcrf, "rotationIcrf");

    double atZero = longitudeOf(directionOfColumn(frame, 0.0), renderRotation, rotationIcrf);
    double atProbe =
        longitudeOf(directionOfColumn(frame, PROBE_COLUMN), renderRotation, rotationIcrf);
    return new TexturePainting(wrap(atZero), wrap(atProbe - atZero) / PROBE_COLUMN);
  }

  /**
   * The body-fixed longitude the chain paints at a given texture column.
   *
   * @param column the texture's {@code u}, not restricted to {@code [0, 1]}
   * @return the longitude, in {@code (−180, 180]}
   */
  public double longitudeOfColumn(double column) {
    return wrap(columnZeroLongitudeDeg + degreesPerColumn * column);
  }

  /**
   * The texture column the chain paints at a given body-fixed longitude — the inverse of {@link
   * #longitudeOfColumn}, brought back into {@code [0, 1)}.
   *
   * @param longitudeDeg the body-fixed longitude
   * @return the column
   */
  public double columnAtLongitude(double longitudeDeg) {
    double column = (longitudeDeg - columnZeroLongitudeDeg) / degreesPerColumn;
    return column - Math.floor(column);
  }

  /**
   * The body-fixed longitude of a direction given in ICRF axes — the physics side of the
   * comparison, which owes nothing to any mesh.
   *
   * @param directionIcrf the direction, in ICRF axes
   * @param rotationIcrf the body's ICRF-to-body-frame rotation
   * @return the longitude, in {@code (−180, 180]}
   */
  public static double bodyFixedLongitudeDeg(Vector3D directionIcrf, Rotation rotationIcrf) {
    Vector3D bodyFixed = rotationIcrf.applyTo(directionIcrf);
    return wrap(Math.toDegrees(Math.atan2(bodyFixed.getY(), bodyFixed.getX())));
  }

  /**
   * The direction, in the model's own axes, that carries a given texture column at the equator.
   *
   * @param frame the measured frame
   * @param column the texture's {@code u}
   * @return a unit direction in model axes
   */
  public static Vector3f directionOfColumn(MeshFrame frame, double column) {
    return directionOf(frame, column, 0.5);
  }

  /**
   * The direction, in the model's own axes, that carries a given point of the texture. Read
   * straight off the measured frame, so a mesh whose map runs the other way round the pole, or
   * whose {@code v = 0} edge is not on an axis, is followed rather than assumed away.
   *
   * <p>The meridian is re-derived perpendicular to the pole, for the reason {@code
   * MeshConformance#basis} gives: a real mesh does not measure exactly square, and Mercury's is
   * three degrees out.
   *
   * @param frame the measured frame
   * @param column the texture's {@code u}
   * @param row the texture's {@code v}, {@code 0} on the pole edge and {@code 1} on the other
   * @return a unit direction in model axes
   */
  public static Vector3f directionOf(MeshFrame frame, double column, double row) {
    Vector3f pole = frame.pole().normalize();
    Vector3f quadrature = pole.cross(frame.primeMeridian()).normalizeLocal();
    Vector3f meridian = quadrature.cross(pole).normalizeLocal();

    float azimuthRad = (float) (frame.azimuthDegreesPerU() * column) * FastMath.DEG_TO_RAD;
    float colatitudeRad = (float) (row * Math.PI);
    return meridian
        .multLocal(FastMath.cos(azimuthRad))
        .addLocal(quadrature.multLocal(FastMath.sin(azimuthRad)))
        .multLocal(FastMath.sin(colatitudeRad))
        .addLocal(pole.multLocal(FastMath.cos(colatitudeRad)))
        .normalizeLocal();
  }

  /**
   * Where the chain puts a direction of the model, expressed in the body-fixed axes Orekit works
   * in. The whole instrument comes down to this one round trip: forward through the renderer, back
   * through the physics.
   *
   * @param modelDirection a direction in the model's own axes
   * @param renderRotation the rotation the renderer is applying to the model
   * @param rotationIcrf the body's ICRF-to-body-frame rotation at the same instant
   * @return the same direction, in body-fixed axes
   */
  public static Vector3D bodyFixedOf(
      Vector3f modelDirection, Quaternion renderRotation, Rotation rotationIcrf) {
    Vector3f world = renderRotation.mult(modelDirection);
    Vector3D worldJme = new Vector3D(world.x, world.y, world.z);
    return rotationIcrf.applyTo(AxisConvention.ICRF_TO_JME_Y_UP.jmeToIcrf(worldJme));
  }

  private static double longitudeOf(
      Vector3f modelDirection, Quaternion renderRotation, Rotation rotationIcrf) {
    Vector3D bodyFixed = bodyFixedOf(modelDirection, renderRotation, rotationIcrf);
    return wrap(Math.toDegrees(Math.atan2(bodyFixed.getY(), bodyFixed.getX())));
  }

  /** Brings an angle back into {@code (−180, 180]}. */
  static double wrap(double degrees) {
    double wrapped = degrees % 360.0;
    if (wrapped > 180.0) {
      wrapped -= 360.0;
    } else if (wrapped <= -180.0) {
      wrapped += 360.0;
    }
    return wrapped;
  }
}
