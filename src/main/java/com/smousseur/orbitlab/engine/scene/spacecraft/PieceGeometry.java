package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Where a loaded piece's nose, base and axis are, and how thick its body is — the numbers its
 * re-entry plasma volume is placed and sized by.
 *
 * <p>Every piece flies its {@code +Y} first, so the nose is the top of the model's bounding box and
 * the base its bottom, and the body runs along the box's vertical axis. A launcher piece is
 * authored nose up, base at the origin, its body on {@code +Y}: measured, its base lies within 1.5
 * cm of the origin and its axis within 7 cm of {@code +Y} on a Falcon Heavy, 22 cm on the Ariane 64
 * core. A payload mesh is mounted onto that convention as it loads (see {@link PayloadMounts}), its
 * engine's exit plane at the origin, but its box is not centred on its engine: Landsat 8's thruster
 * ring lies 1.03 native units off the middle of a 2.46-unit box, and the GOES mast reaches 0.52 of
 * the mesh's size below its engine. Hence the base and the axis are measured rather than assumed.
 *
 * <p>The body radius is <b>not</b> the box's half-width, which on a Falcon Heavy piece spans its
 * landing legs and grid fins — 2.88 m against a 1.94 m body, measured on the booster — while on an
 * Ariane 64 piece box and body agree. One set of proportions in body radii could not fit both.
 * Instead the model is cut into {@value #SLICES} slices along its height, the widest vertex of each
 * slice is taken about the box's vertical axis, and the median over the slices that hold any vertex
 * is kept: appendages confined to a few slices fall out of it, the body that runs the whole height
 * does not.
 *
 * @param noseHeightUnits the nose's height above the model's origin, in the model's world units
 * @param bodyRadiusUnits the body's radius, in the same units
 * @param baseHeightUnits the base's height above the model's origin, in the same units; negative
 *     below it
 * @param axisXUnits where the body's axis crosses the model's {@code x}, in the same units
 * @param axisZUnits where the body's axis crosses the model's {@code z}, in the same units
 */
public record PieceGeometry(
    float noseHeightUnits,
    float bodyRadiusUnits,
    float baseHeightUnits,
    float axisXUnits,
    float axisZUnits) {

  /**
   * A piece authored on the launcher convention: its base at the origin, its body on {@code +Y}.
   *
   * @param noseHeightUnits the nose's height above the model's origin, in the model's world units
   * @param bodyRadiusUnits the body's radius, in the same units
   */
  public PieceGeometry(float noseHeightUnits, float bodyRadiusUnits) {
    this(noseHeightUnits, bodyRadiusUnits, 0f, 0f, 0f);
  }

  /**
   * How long the body is, from its base to its nose.
   *
   * @return the body's length, in the model's world units
   */
  public float bodyLengthUnits() {
    return noseHeightUnits - baseHeightUnits;
  }

  /**
   * How many slices the height is cut into. On the Falcon Heavy booster, legs and grid fins widen 6
   * slices of these 20, measured on the asset — short of the 10 it would take to move the median.
   */
  static final int SLICES = 20;

  /**
   * Measures a model. Safe on a loading thread while the model is detached: it touches nothing but
   * the model.
   *
   * @param model the loaded model, scale included, not attached to the scene
   * @return its nose height, body radius, base height and axis, in its own world units
   */
  public static PieceGeometry measure(Spatial model) {
    model.updateGeometricState();
    BoundingVolume bound = model.getWorldBound();
    Vector3f centre = bound.getCenter();
    float bottom;
    float top;
    if (bound instanceof BoundingBox box) {
      bottom = centre.y - box.getYExtent();
      top = centre.y + box.getYExtent();
    } else if (bound instanceof BoundingSphere sphere) {
      bottom = centre.y - sphere.getRadius();
      top = centre.y + sphere.getRadius();
    } else {
      return new PieceGeometry(0f, 0f);
    }
    SliceWidths widths = new SliceWidths(centre.x, centre.z, bottom, top);
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            widths.add(geometry);
          }
        });
    return new PieceGeometry(top, widths.median(), bottom, centre.x, centre.z);
  }

  /** The widest vertex of each height slice, about a vertical axis. */
  private static final class SliceWidths {

    private final float axisX;
    private final float axisZ;
    private final float bottom;
    private final float height;
    private final float[] widest = new float[SLICES];
    private final boolean[] filled = new boolean[SLICES];

    SliceWidths(float axisX, float axisZ, float bottom, float top) {
      this.axisX = axisX;
      this.axisZ = axisZ;
      this.bottom = bottom;
      this.height = top - bottom;
    }

    void add(Geometry geometry) {
      Mesh mesh = geometry.getMesh();
      VertexBuffer positions = mesh.getBuffer(VertexBuffer.Type.Position);
      if (positions == null || !(positions.getData() instanceof FloatBuffer data)) {
        return;
      }
      Transform world = geometry.getWorldTransform();
      Vector3f local = new Vector3f();
      Vector3f vertex = new Vector3f();
      for (int i = 0; i + 2 < data.limit(); i += 3) {
        local.set(data.get(i), data.get(i + 1), data.get(i + 2));
        world.transformVector(local, vertex);
        int slice = sliceOf(vertex.y);
        float radius = (float) Math.hypot(vertex.x - axisX, vertex.z - axisZ);
        widest[slice] = Math.max(widest[slice], radius);
        filled[slice] = true;
      }
    }

    private int sliceOf(float y) {
      if (!(height > 0f)) {
        return 0;
      }
      int slice = (int) ((y - bottom) / height * SLICES);
      return Math.max(0, Math.min(SLICES - 1, slice));
    }

    float median() {
      float[] kept = new float[SLICES];
      int count = 0;
      for (int i = 0; i < SLICES; i++) {
        if (filled[i]) {
          kept[count++] = widest[i];
        }
      }
      if (count == 0) {
        return 0f;
      }
      float[] sorted = Arrays.copyOf(kept, count);
      Arrays.sort(sorted);
      int middle = count / 2;
      return count % 2 == 1 ? sorted[middle] : 0.5f * (sorted[middle - 1] + sorted[middle]);
    }
  }
}
