package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;

/**
 * The plasma sheath of one drawn piece: a paraboloid shell standing off its nose, facing the
 * air-relative velocity, glowing as hot as the piece's re-entry heat profile says.
 *
 * <h2>Why it sits beside the model rather than in it</h2>
 *
 * <p>The piece's mesh lives in its model bucket, and the bucket is where its orientation and its
 * render-only seat are applied. It cannot hold the sheath: it is emptied every time a model is
 * attached, and the eclipse writes its uniforms onto every geometry in it. So the sheath hangs
 * under the same 3D anchor, as the bucket's sibling, and takes its own pose each frame from the
 * bucket's: anchored on the nose the eye sees — seat plus bucket rotation times the nose height —
 * and turned onto its own axis, the air-relative velocity, which the body's inertial heading can
 * miss by several degrees. The axis is a pure function of the date and is not smoothed: scrubbing
 * the clock, the sheath faces its new direction at once and the smoothed body catches up.
 *
 * <p>It shows only when the body itself is drawn this frame — which follows the LOD switch and the
 * hiding before a jettison — once the piece has been measured, and while it is hot.
 */
public final class ReentrySheathView {

  /** Name of the node the sheath hangs under the anchor by. */
  static final String PIVOT_NAME = "ReentrySheath-pivot";

  private final Node pivot = new Node(PIVOT_NAME);
  private final Material material;

  /** The drawn piece's nose and body, measured on the loading thread; null until then. */
  private volatile PieceGeometry piece;

  /**
   * Builds a sheath, put out, and hangs it under a piece's 3D anchor.
   *
   * @param anchor3d the anchor the piece's model bucket hangs under
   * @param unitMesh the shell for a unit body radius, shared by every sheath
   * @param material this sheath's own material
   */
  public ReentrySheathView(Node anchor3d, Mesh unitMesh, Material material) {
    Objects.requireNonNull(anchor3d, "anchor3d");
    this.material = Objects.requireNonNull(material, "material");
    Geometry shell = new Geometry("ReentrySheath", Objects.requireNonNull(unitMesh, "unitMesh"));
    shell.setMaterial(material);
    shell.setQueueBucket(RenderQueue.Bucket.Transparent);
    pivot.attachChild(shell);
    pivot.setCullHint(Spatial.CullHint.Always);
    anchor3d.attachChild(pivot);
  }

  /**
   * Hands over the measure of the piece's current mesh. Safe from the loading thread.
   *
   * @param piece the nose height and body radius of the model about to be attached
   */
  public void setPieceGeometry(PieceGeometry piece) {
    this.piece = Objects.requireNonNull(piece, "piece");
  }

  /**
   * Poses and lights the sheath for this frame, or puts it out.
   *
   * @param intensity the piece's heat intensity now, in {@code [0, 1]}
   * @param axis the air-relative velocity's direction, in the anchor's (world) axes; zero when
   *     there is none
   * @param body the piece's model bucket, whose local translation is the seat and local rotation
   *     the body's attitude
   * @param bodyVisible whether the piece's model is drawn this frame
   */
  public void update(float intensity, Vector3f axis, Spatial body, boolean bodyVisible) {
    PieceGeometry measured = piece;
    if (!bodyVisible || measured == null || !(intensity > 0f) || axis.lengthSquared() == 0f) {
      pivot.setCullHint(Spatial.CullHint.Always);
      return;
    }
    pivot.setLocalTranslation(
        anchorPoint(
            body.getLocalTranslation(), body.getLocalRotation(), measured.noseHeightUnits()));
    pivot.setLocalRotation(orientation(axis));
    pivot.setLocalScale(measured.bodyRadiusUnits());
    material.setFloat("Intensity", intensity);
    pivot.setCullHint(Spatial.CullHint.Inherit);
  }

  /**
   * Puts the sheath out without waiting for the next {@link #update} — for a piece hidden whole,
   * which is not updated again until it shows: before its jettison, or while its mission is hidden.
   */
  public void putOut() {
    pivot.setCullHint(Spatial.CullHint.Always);
  }

  /**
   * The drawn nose: the body's seat, plus its attitude applied to the nose height on its {@code
   * +Y}, along which every piece mesh is authored.
   *
   * @param seat the body's translation under the anchor
   * @param bodyRotation the body's rotation under the anchor
   * @param noseHeight the nose's height above the mesh origin
   * @return where the sheath is anchored, under the anchor
   */
  static Vector3f anchorPoint(Vector3f seat, Quaternion bodyRotation, float noseHeight) {
    return bodyRotation.mult(new Vector3f(0f, noseHeight, 0f)).addLocal(seat);
  }

  /**
   * The shortest rotation turning the shell's {@code +Y} apex onto {@code axis}; a half turn about
   * {@code X} when the axis is {@code -Y}, where the shortest arc is undefined.
   *
   * @param axis the direction to face, any length but zero
   * @return the rotation
   */
  static Quaternion orientation(Vector3f axis) {
    Vector3f to = axis.normalize();
    float dot = Vector3f.UNIT_Y.dot(to);
    if (dot < -1f + 1e-6f) {
      return new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_X);
    }
    Vector3f cross = Vector3f.UNIT_Y.cross(to);
    return new Quaternion(cross.x, cross.y, cross.z, 1f + dot).normalizeLocal();
  }
}
