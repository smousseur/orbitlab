package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.scene.body.BodyView;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * MVC presenter that drives a {@link BodyView} by applying a spacecraft's position and orientation
 * from mission propagation data. Unlike {@link
 * com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter} which fetches positions from
 * ephemeris, this presenter receives them directly from the mission's propagation output.
 *
 * <p>Orientation is driven by the velocity vector: the model's nose is aligned with the direction
 * of travel, interpolated frame-to-frame with a slerp to avoid jitter. When the velocity is ~0 the
 * last orientation is preserved. On top of it the primary can be turned over for a retrograde burn:
 * a pitch through its local vertical, smoothed apart from the attitude it is applied to.
 */
public final class SpacecraftPresenter {

  /** Smoothing half-life in seconds for the orientation slerp. */
  private static final float ROTATION_SMOOTHING_HALF_LIFE = 0.15f;

  /** Minimum squared velocity (m/s)^2 below which rotation is not updated. */
  private static final double MIN_VELOCITY_NORM_SQ = 1e-6;

  /**
   * Fixed correction applied to the {@link Quaternion#lookAt} result so that a launcher model —
   * whose nose points along its own +Y axis — is aligned with the direction of travel. {@code
   * lookAt} orients the local +Z along the given direction, so we rotate the model 90° around its
   * local X to bring +Y onto +Z.
   *
   * <p>One correction for every spacecraft, deliberately: the +Y nose is a convention every mesh
   * listed in {@link LauncherAssets} is checked against, rather than something re-derived per
   * launcher. The payload meshes, authored without it, are turned onto it as they load (see {@link
   * PayloadMounts}).
   */
  private static final Quaternion MODEL_FORWARD_CORRECTION =
      new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X);

  /**
   * How close, in radians, the smoothed turn has to come to its target before it is set on it: the
   * exponential smoothing only creeps toward its target, and an unturned primary must get back the
   * very rotation it had before any turn, not one a ten-thousandth of a radian off.
   */
  private static final float TURN_SNAP_RADIANS = 1e-4f;

  /**
   * The smallest squared norm of {@code nose x radial} that still gives a turn axis: a nose along
   * the vertical has none, and keeps the last axis it had.
   */
  private static final float MIN_TURN_AXIS_NORM_SQ = 1e-12f;

  private final String id;
  private final BodyView view;
  private final Quaternion currentRotation = new Quaternion();
  private final Quaternion targetRotation = new Quaternion();
  private final Quaternion turnedRotation = new Quaternion();
  private final Vector3f turnAxis = new Vector3f();
  private boolean rotationInitialized;
  private boolean turnAxisSet;
  private boolean turnInitialized;
  private float turnAngle;

  public SpacecraftPresenter(String id, BodyView view) {
    this.id = Objects.requireNonNull(id, "id");
    this.view = Objects.requireNonNull(view, "view");
  }

  /**
   * Returns the unique identifier of this presenter.
   *
   * @return the presenter id
   */
  public String id() {
    return id;
  }

  /**
   * Returns the underlying body view driven by this presenter.
   *
   * @return the body view
   */
  public BodyView view() {
    return view;
  }

  /**
   * Sets the visibility of the spacecraft view.
   *
   * @param v {@code true} to make the spacecraft visible, {@code false} to hide it
   */
  public void setVisible(boolean v) {
    view.setVisible(v);
  }

  /**
   * Updates the spacecraft's pose in the view. The position is provided in GCRF meters (geocentric,
   * Earth at origin). Since GCRF is geocentric and aligned on ICRF axes, only render-unit scaling
   * and ICRF-to-JME axis conversion are applied.
   *
   * <p>Rotation is derived from the velocity vector and smoothed with a spherical linear
   * interpolation. When the velocity magnitude is below a small epsilon the last computed rotation
   * is preserved — the position is still applied.
   *
   * @param positionGcrfMeters the spacecraft position in GCRF frame, in meters
   * @param velocityGcrf the spacecraft velocity in GCRF frame, in m/s (may be zero)
   * @param tpf the frame time in seconds used to drive the rotation smoothing
   * @param ctx the render context defining the scale (typically Planet with 1 unit = 1 km)
   */
  public void updatePose(
      Vector3D positionGcrfMeters, Vector3D velocityGcrf, float tpf, RenderContext ctx) {
    updatePose(positionGcrfMeters, velocityGcrf, tpf, ctx, Vector3f.UNIT_Y);
  }

  /**
   * As {@link #updatePose(Vector3D, Vector3D, float, RenderContext)}, but with an explicit "up"
   * reference for the roll about the nose. The default is world {@code +Y}; a jettisoned booster
   * passes its own separation (fan) direction so it keeps the roll it had while mounted — its
   * marked face turned outward, toward its quadrant, as before the separation (PHY-5 / L7).
   *
   * @param upWorld the roll reference in world (JME) axes; must not be parallel to the velocity
   */
  public void updatePose(
      Vector3D positionGcrfMeters,
      Vector3D velocityGcrf,
      float tpf,
      RenderContext ctx,
      Vector3f upWorld) {
    updatePose(positionGcrfMeters, velocityGcrf, tpf, ctx, upWorld, 0.0, Vector3D.ZERO);
  }

  /**
   * As {@link #updatePose(Vector3D, Vector3D, float, RenderContext, Vector3f)}, with the primary
   * turned over by {@code turn} for a retrograde burn: the attitude it would otherwise have is
   * pitched by that angle about {@code nose x radial}, so the nose goes up through the local
   * vertical and comes down tail first at pi. The angle is smoothed with the same half-life as the
   * attitude, but apart from it, as a number: a slerp between two attitudes half a turn apart would
   * take whichever way is shortest, through the inside or about the nose, as soon as the clock runs
   * fast enough for the turn to fit in a frame or two. At a zero turn the rotation is the very one
   * the shorter overload sets.
   *
   * @param positionGcrfMeters the spacecraft position in GCRF frame, in meters
   * @param velocityGcrf the direction the nose points, in the same frame (may be zero)
   * @param tpf the frame time in seconds used to drive the smoothing
   * @param ctx the render context defining the scale
   * @param upWorld the roll reference in world (JME) axes; must not be parallel to the velocity
   * @param turn the angle the primary is turned by, from 0 (nose first) to pi (tail first)
   * @param radialGcrf the direction from the centre of the body flown around to the spacecraft, in
   *     the same frame as the velocity; zero leaves the turn axis as it was
   */
  public void updatePose(
      Vector3D positionGcrfMeters,
      Vector3D velocityGcrf,
      float tpf,
      RenderContext ctx,
      Vector3f upWorld,
      double turn,
      Vector3D radialGcrf) {
    Objects.requireNonNull(positionGcrfMeters, "positionGcrfMeters");
    Objects.requireNonNull(velocityGcrf, "velocityGcrf");
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(upWorld, "upWorld");
    Objects.requireNonNull(radialGcrf, "radialGcrf");

    // Through JmeVectorAdapter, never inlined: the floating-origin state negates the very same
    // conversion of the very same position to place the near-view origin on this spacecraft.
    view.setPositionWorld(JmeVectorAdapter.toJmeBodyRelativePosition(positionGcrfMeters, ctx));

    float alpha = 1f - (float) Math.pow(0.5, tpf / ROTATION_SMOOTHING_HALF_LIFE);
    if (velocityGcrf.getNormSq() > MIN_VELOCITY_NORM_SQ) {
      Vector3D dirIcrf = velocityGcrf.normalize();
      Vector3D dirJme = ctx.axisConvention().icrfToJme(dirIcrf);
      Vector3f forward = JmeVectorAdapter.toVector3f(dirJme);
      targetRotation.lookAt(forward, upWorld);
      targetRotation.multLocal(MODEL_FORWARD_CORRECTION);
      targetRotation.normalizeLocal();

      if (!rotationInitialized) {
        currentRotation.set(targetRotation);
        rotationInitialized = true;
      } else {
        Quaternion from = new Quaternion().set(currentRotation);
        currentRotation.slerp(from, targetRotation, alpha);
        currentRotation.normalizeLocal();
      }
      updateTurnAxis(forward, radialGcrf, ctx);
    }
    smoothTurn((float) turn, alpha);

    if (rotationInitialized) {
      view.setRotationWorld(turned());
    }
  }

  /**
   * Where a point of the model, given in its bucket's frame, has moved to because of the turn, the
   * other way round: added to the bucket's translation, it turns the model about that point instead
   * of about the bucket's origin. Zero when the primary is not turned.
   *
   * @param pivot the point to turn about, in the bucket's frame
   * @return the translation that keeps {@code pivot} where it stood unturned
   */
  public Vector3f flipCompensation(Vector3f pivot) {
    if (!isTurned()) {
      return new Vector3f();
    }
    return currentRotation.mult(pivot).subtractLocal(turnedRotation.mult(pivot));
  }

  private void updateTurnAxis(Vector3f forward, Vector3D radialGcrf, RenderContext ctx) {
    if (radialGcrf.getNormSq() == 0.0) {
      return;
    }
    Vector3f radial =
        JmeVectorAdapter.toVector3f(ctx.axisConvention().icrfToJme(radialGcrf.normalize()));
    Vector3f axis = forward.cross(radial);
    if (axis.lengthSquared() > MIN_TURN_AXIS_NORM_SQ) {
      turnAxis.set(axis).normalizeLocal();
      turnAxisSet = true;
    }
  }

  private void smoothTurn(float target, float alpha) {
    if (!turnInitialized || Math.abs(target - turnAngle) < TURN_SNAP_RADIANS) {
      turnAngle = target;
      turnInitialized = true;
    } else {
      turnAngle += (target - turnAngle) * alpha;
    }
  }

  private boolean isTurned() {
    return rotationInitialized && turnAxisSet && turnAngle != 0f;
  }

  /** The rotation to draw: the attitude itself, or the attitude turned about the turn axis. */
  private Quaternion turned() {
    if (!isTurned()) {
      return currentRotation;
    }
    return turnedRotation.fromAngleNormalAxis(turnAngle, turnAxis).multLocal(currentRotation);
  }
}
