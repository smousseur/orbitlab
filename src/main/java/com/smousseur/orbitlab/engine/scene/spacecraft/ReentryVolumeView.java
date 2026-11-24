package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.smousseur.orbitlab.engine.ReentryVolumeConfig;
import java.util.Objects;

/**
 * The plasma one drawn piece is wrapped in while it re-enters, up close: a box around the piece in
 * which {@code ReentryVolume.frag} ray-marches the bow shock, the shock layer, the boundary layer
 * and the turbulent wake, lit as hot as the piece's re-entry heat profile says, shaped by its speed
 * and altitude.
 *
 * <h2>Where it hangs, and which way it faces</h2>
 *
 * <p>The piece's mesh lives in its model bucket, where its orientation and its render-only seat are
 * applied; the bucket is emptied whenever a model is attached, and the eclipse writes its uniforms
 * on everything in it. So the volume hangs under the same 3D anchor, as the bucket's sibling, and
 * takes its pose from the bucket's every frame: anchored on the nose the eye sees — seat plus
 * bucket rotation times the nose, on the body's measured axis ({@link PieceGeometry}) — and turned
 * onto the air-relative velocity, its {@code +Y} being the prototype's flow axis. It is scaled by
 * the body's radius, so the shader works in body radii. The axis is a pure function of the date and
 * is not smoothed: scrubbing the clock, the volume faces its new direction at once and the smoothed
 * body catches up.
 *
 * <h2>The body inside it</h2>
 *
 * <p>The drawn body follows its inertial heading, not the air-relative velocity: the two part by up
 * to 8.8 degrees while a piece glows, measured. The shock and the wake hang on the flow. The rays
 * stop on the drawn mesh itself, read from the scene's depth ({@code ReentryVolumeFilter}), and the
 * boundary layer glows just before that stop; it fades along the body, and the wake starts past its
 * base. So the shader is told the body's own axis in the box's axes, and its length in radii, from
 * the nose down to the measured base.
 *
 * <h2>When it shows, and who draws it</h2>
 *
 * <p>It shows only when the body itself is drawn this frame — which follows the LOD switch and the
 * hiding before a jettison — once the piece has been measured, and while it is hot. The box is
 * queued in the {@link RenderQueue.Bucket#Translucent Translucent} bucket, which the near view's
 * main pass leaves alone: {@code ReentryVolumeFilter} marches it at a reduced resolution and lays
 * it over the scene, the cost of a full-resolution march being an order of magnitude over budget.
 */
public final class ReentryVolumeView {

  /** Name of the node the volume hangs under the anchor by. */
  static final String PIVOT_NAME = "ReentryVolume-pivot";

  private final Node pivot = new Node(PIVOT_NAME);
  private final Geometry box;
  private final Material material;
  private final ReentryVolumeConfig config;

  /** The drawn piece's nose and body, measured on the loading thread; null until then. */
  private volatile PieceGeometry piece;

  /** The measure the box was last built for; null until the first build. */
  private PieceGeometry boxedFor;

  /**
   * Builds a volume, put out, and hangs it under a piece's 3D anchor.
   *
   * @param anchor3d the anchor the piece's model bucket hangs under
   * @param material this volume's own material
   * @param config the box's reach and the speed of sound
   */
  public ReentryVolumeView(Node anchor3d, Material material, ReentryVolumeConfig config) {
    Objects.requireNonNull(anchor3d, "anchor3d");
    this.material = Objects.requireNonNull(material, "material");
    this.config = Objects.requireNonNull(config, "config");
    box = new Geometry("ReentryVolume", new Box(1f, 1f, 1f));
    box.setMaterial(material);
    box.setQueueBucket(RenderQueue.Bucket.Translucent);
    pivot.attachChild(box);
    pivot.setCullHint(Spatial.CullHint.Always);
    anchor3d.attachChild(pivot);
  }

  /**
   * Hands over the measure of the piece's current mesh. Safe from the loading thread: the box is
   * rebuilt for it on the render thread, by the next {@link #update}.
   *
   * @param piece the nose, base, axis and body radius of the model about to be attached
   */
  public void setPieceGeometry(PieceGeometry piece) {
    this.piece = Objects.requireNonNull(piece, "piece");
  }

  /**
   * Poses, shapes and lights the volume for this frame, or puts it out.
   *
   * @param intensity the piece's heat intensity now, in {@code [0, 1]}
   * @param axis the air-relative velocity's direction, in the anchor's (world) axes; zero when
   *     there is none
   * @param speedMs the piece's air-relative speed now, in m/s
   * @param altitudeM the piece's altitude now, in metres
   * @param body the piece's model bucket, whose local translation is the seat and local rotation
   *     the body's attitude
   * @param bodyVisible whether the piece's model is drawn this frame
   */
  public void update(
      float intensity,
      Vector3f axis,
      float speedMs,
      float altitudeM,
      Spatial body,
      boolean bodyVisible) {
    PieceGeometry measured = piece;
    if (!bodyVisible
        || measured == null
        || !(measured.bodyRadiusUnits() > 0f)
        || !(intensity > 0f)
        || axis.lengthSquared() == 0f) {
      pivot.setCullHint(Spatial.CullHint.Always);
      return;
    }
    float bodyLength = measured.bodyLengthUnits() / measured.bodyRadiusUnits();
    if (measured != boxedFor) {
      rebuildBox(bodyLength);
      boxedFor = measured;
    }
    Quaternion orientation = orientation(axis);
    pivot.setLocalTranslation(
        anchorPoint(body.getLocalTranslation(), body.getLocalRotation(), measured));
    pivot.setLocalRotation(orientation);
    pivot.setLocalScale(measured.bodyRadiusUnits());

    ReentryPlasmaShape shape = ReentryPlasmaShape.of(speedMs, altitudeM, config.soundSpeedMs());
    material.setFloat("Intensity", intensity);
    material.setFloat("XA", shape.xA());
    material.setFloat("K", shape.k());
    material.setFloat("A", shape.a());
    material.setFloat("Rc", shape.rc());
    material.setFloat("Viol", shape.violet());
    material.setFloat("Temp", shape.temperature());
    material.setVector3(
        "BodyAxis", orientation.inverse().multLocal(body.getLocalRotation().mult(Vector3f.UNIT_Y)));
    material.setFloat("BodyLength", bodyLength);
    pivot.setCullHint(Spatial.CullHint.Inherit);
  }

  /**
   * Sets the simulated time the turbulence drifts with, and the share of it the clock rate allows
   * ({@link ReentryFlowClock}).
   *
   * @param flowSeconds the simulated seconds since a fixed date of the piece's own, kept small so a
   *     float holds them to well under a frame
   * @param clockRate the simulated seconds per real second, negative when rewinding, zero when
   *     paused
   */
  public void setFlow(float flowSeconds, double clockRate) {
    material.setFloat("FlowSeconds", flowSeconds);
    material.setFloat("Turbulence", ReentryFlowClock.turbulence(clockRate));
  }

  /**
   * Puts the volume out without waiting for the next {@link #update} — for a piece hidden whole,
   * which is not updated again until it shows: before its jettison, or while its mission is hidden.
   */
  public void putOut() {
    pivot.setCullHint(Spatial.CullHint.Always);
  }

  /** The box, in body radii, from ahead of the nose to beyond the base of a body this long. */
  private void rebuildBox(float bodyLength) {
    float halfWidth = config.halfWidthRadii();
    Vector3f min = new Vector3f(-halfWidth, -(bodyLength + config.behindRadii()), -halfWidth);
    Vector3f max = new Vector3f(halfWidth, config.aheadRadii(), halfWidth);
    box.setMesh(new Box(min, max));
    box.updateModelBound();
    material.setVector3("BoxMin", min);
    material.setVector3("BoxMax", max);
  }

  /**
   * The drawn nose: the body's seat, plus its attitude applied to the nose on the body's measured
   * axis — on {@code +Y} for a launcher piece, off it for a payload mesh.
   *
   * @param seat the body's translation under the anchor
   * @param bodyRotation the body's rotation under the anchor
   * @param piece the body's measured nose height and axis, in its mesh's units
   * @return where the volume is anchored, under the anchor
   */
  static Vector3f anchorPoint(Vector3f seat, Quaternion bodyRotation, PieceGeometry piece) {
    Vector3f nose = new Vector3f(piece.axisXUnits(), piece.noseHeightUnits(), piece.axisZUnits());
    return bodyRotation.mult(nose).addLocal(seat);
  }

  /**
   * The shortest rotation turning {@code +Y} onto {@code axis}; a half turn about {@code X} when
   * the axis is {@code -Y}, where the shortest arc is undefined.
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
