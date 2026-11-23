package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.engine.ReentryFxConfig;
import com.smousseur.orbitlab.engine.ReentryWakeConfig;
import java.util.Objects;

/**
 * The glowing wake one piece trails while it re-enters, seen from afar: a tube streaming back from
 * where its sheath would be, against the air-relative velocity, past its body and on by the air it
 * heated in the last fraction of a second.
 *
 * <h2>Why it takes over from the sheath</h2>
 *
 * <p>The sheath shows with the piece's model and goes out with it when the piece shrinks to its
 * icon. The wake is the relay past that switch: it is what still marks a re-entry from afar — from
 * the primary the camera follows by default, a falling piece glows hundreds of kilometres away. So
 * it shows exactly where the sheath does not, while the model is not drawn, and only on the rule of
 * the trajectory ribbon, which its owner decides and hands over: with the "show debris" option, or
 * on the object being followed. Up close it stays out: a tube that wide passes by the camera there,
 * which clips it on its near plane, and seen from behind it fills the view.
 *
 * <h2>Where it starts</h2>
 *
 * <p>The sheath and the wake are one envelope: the shock standing off the nose, then the hot gas it
 * sheds streaming back. So the wake starts where the sheath ends — at its open rim, found from the
 * same drawn nose the sheath is anchored on, seat and attitude included, and the same axis — and is
 * as wide there as that rim. The body runs inside it, and the tube shows past its flanks. Starting
 * at the base instead left the whole body, unlit, between two separate glows. It hangs under the
 * piece's 3D anchor, as the bucket's sibling; its axis is a pure function of the date and is not
 * smoothed, as the sheath's.
 *
 * <h2>How large it is</h2>
 *
 * <p>Up close the tube is physical: as long as the body, plus {@code persistence × |v_rel|} beyond
 * it, and as wide at its root as the sheath's rim, whatever proportions the sheath is tuned to. Far
 * away {@code ReentryWake.vert} magnifies its length and floors its width in pixels. The mesh is a
 * unit tube the shader stretches to hundreds of kilometres once magnified, far outside its own
 * bounds, so the tube is exempt from frustum culling: the pivot alone switches it on and off, a
 * pivot culled always taking its child with it. It is a solid of revolution, not a band facing the
 * eye: seen from behind, a band fanned out flat toward the camera, which the body could not hide.
 */
public final class ReentryWakeView {

  /** Name of the node the wake hangs under the anchor by. */
  static final String PIVOT_NAME = "ReentryWake-pivot";

  private final Node pivot = new Node(PIVOT_NAME);
  private final Material material;
  private final ReentryWakeConfig wake;

  /**
   * Where the sheath's open rim lies along the axis, from the nose, in body radii: its apex stands
   * {@code standoff} ahead and the rim {@code length} behind the apex. Negative: behind the nose.
   */
  private final float rimOffsetRatio;

  /** The sheath's rim radius, in body radii: the wake's half-width at its root. */
  private final float rimRadiusRatio;

  /** The drawn piece's nose and body, measured on the loading thread; null until then. */
  private volatile PieceGeometry piece;

  /**
   * Builds a wake, put out, and hangs it under a piece's 3D anchor.
   *
   * @param anchor3d the anchor the piece's model bucket hangs under
   * @param unitTube the unit tube shared by every wake
   * @param material this wake's own material
   * @param config the re-entry tuning: the sheath's proportions, which the wake streams back from,
   *     and the wake's own
   */
  public ReentryWakeView(Node anchor3d, Mesh unitTube, Material material, ReentryFxConfig config) {
    Objects.requireNonNull(anchor3d, "anchor3d");
    this.material = Objects.requireNonNull(material, "material");
    this.wake = Objects.requireNonNull(config, "config").wake();
    this.rimOffsetRatio = config.standoffRatio() - config.lengthRatio();
    this.rimRadiusRatio = config.openingRatio();
    Geometry tube = new Geometry("ReentryWake", Objects.requireNonNull(unitTube, "unitTube"));
    tube.setMaterial(material);
    tube.setQueueBucket(RenderQueue.Bucket.Transparent);
    tube.setCullHint(Spatial.CullHint.Never);
    pivot.attachChild(tube);
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
   * Poses, sizes and lights the wake for this frame, or puts it out.
   *
   * @param intensity the piece's heat intensity now, in {@code [0, 1]}
   * @param axis the air-relative velocity's direction, in the anchor's (world) axes; zero when
   *     there is none. The wake trails against it.
   * @param speedMs the piece's air-relative speed now, in m/s
   * @param unitsPerMeter the render scale of the piece's sample
   * @param body the piece's model bucket, whose local translation is the seat and local rotation
   *     the body's attitude
   * @param bodyVisible whether the piece's model is drawn this frame
   * @param shown whether the ribbon's rule shows this object's path this frame
   */
  public void update(
      float intensity,
      Vector3f axis,
      float speedMs,
      float unitsPerMeter,
      Spatial body,
      boolean bodyVisible,
      boolean shown) {
    PieceGeometry measured = piece;
    if (!shown
        || bodyVisible
        || measured == null
        || !(intensity > 0f)
        || axis.lengthSquared() == 0f) {
      pivot.setCullHint(Spatial.CullHint.Always);
      return;
    }
    float radius = measured.bodyRadiusUnits();
    pivot.setLocalTranslation(
        rootPoint(
            body.getLocalTranslation(),
            body.getLocalRotation(),
            measured.noseHeightUnits(),
            axis,
            rimOffsetRatio * radius));
    pivot.setLocalRotation(ReentrySheathView.orientation(axis.negate()));
    pivot.setLocalScale(1f);
    material.setFloat("Intensity", intensity);
    material.setFloat(
        "LengthUnits",
        measured.noseHeightUnits() + wake.persistenceSeconds() * speedMs * unitsPerMeter);
    material.setFloat("RefLengthUnits", (float) (wake.referenceLengthMeters() * unitsPerMeter));
    material.setFloat("WidthUnits", 2f * rimRadiusRatio * radius);
    pivot.setCullHint(Spatial.CullHint.Inherit);
  }

  /**
   * Puts the wake out without waiting for the next {@link #update} — for a piece hidden whole,
   * which is not updated again until it shows: before its jettison, or while its mission is hidden.
   */
  public void putOut() {
    pivot.setCullHint(Spatial.CullHint.Always);
  }

  /**
   * The wake's root: the open rim of the sheath — the drawn nose, where the sheath is anchored,
   * moved along the sheath's axis to where its rim lies.
   *
   * @param seat the body's translation under the anchor
   * @param bodyRotation the body's rotation under the anchor
   * @param noseHeight the nose's height above the mesh origin
   * @param axis the sheath's axis, any length but zero
   * @param rimOffset how far along the axis the rim lies from the nose; negative behind it
   * @return where the wake starts, under the anchor
   */
  static Vector3f rootPoint(
      Vector3f seat, Quaternion bodyRotation, float noseHeight, Vector3f axis, float rimOffset) {
    return ReentrySheathView.anchorPoint(seat, bodyRotation, noseHeight)
        .addLocal(axis.normalize().multLocal(rimOffset));
  }
}
