package com.smousseur.orbitlab.states.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

/**
 * A turntable camera orientation: the yaw and pitch the orbit camera places itself with, relative
 * to its pivot.
 *
 * <p>The orbit camera puts itself at {@code pivot + Ry(yaw)·Rx(pitch)·(0,0,distance)}, so the
 * direction from the pivot to the camera — its <em>offset direction</em> — is
 *
 * <pre>{@code (cos(pitch)·sin(yaw), −sin(pitch), cos(pitch)·cos(yaw))}</pre>
 *
 * <p>{@link #fromOffsetDirection} inverts that relation and is the only place the convention is
 * written down twice; everything else composes the two factories. Angles are in radians, and yaw is
 * deliberately not normalised — the camera accumulates it without bounds as the user drags, and
 * {@link #towards} is what copes with that.
 *
 * @param yawRad rotation about the vertical axis, unbounded
 * @param pitchRad elevation, expected within the camera's clamp of ±89°
 */
public record CameraOrientation(float yawRad, float pitchRad) {

  /** Default elevation: the camera sits above the pivot and looks 20° down at it. */
  private static final float DEFAULT_PITCH_RAD = (float) Math.toRadians(-20.0);

  /**
   * Below this length a travel vector carries no usable direction. One nanometre of a solar unit is
   * one metre — four orders of magnitude under the shortest real transition, which is a launch pad
   * to its planet's centre.
   */
  private static final float MIN_DIRECTION_LENGTH = 1e-9f;

  /**
   * The orientation the camera boots and resets to: facing the pivot from above, along {@code +Z}.
   *
   * @return the default orientation
   */
  public static CameraOrientation defaults() {
    return new CameraOrientation(0f, DEFAULT_PITCH_RAD);
  }

  /**
   * The orientation that puts the camera in a given direction from its pivot.
   *
   * @param offsetDirection where the camera should sit relative to the pivot; need not be
   *     normalised, but must not be degenerate
   * @return the matching orientation, or {@link #defaults()} if the direction carries no usable
   *     bearing
   */
  public static CameraOrientation fromOffsetDirection(Vector3f offsetDirection) {
    if (offsetDirection == null || offsetDirection.length() < MIN_DIRECTION_LENGTH) {
      return defaults();
    }
    Vector3f u = offsetDirection.normalize();
    // Straight up or down leaves the yaw free: atan2(0,0) answers 0, which is as good as any.
    float pitch = FastMath.asin(FastMath.clamp(-u.y, -1f, 1f));
    float yaw = FastMath.atan2(u.x, u.z);
    return new CameraOrientation(yaw, pitch);
  }

  /**
   * The orientation that looks along a direction of travel, so whatever lies at the far end of it
   * is straight ahead. The camera is put on the near side of the pivot — the opposite of where it
   * is heading — which is what turns a transition into a fly-to rather than a drift past.
   *
   * @param travelDirection the direction the pivot is travelling in
   * @return the matching orientation
   */
  public static CameraOrientation facingAlong(Vector3f travelDirection) {
    return travelDirection == null ? defaults() : fromOffsetDirection(travelDirection.negate());
  }

  /**
   * The direction the camera sits in, relative to its pivot. Inverse of {@link
   * #fromOffsetDirection}.
   *
   * @return a unit offset direction
   */
  public Vector3f offsetDirection() {
    float cosPitch = FastMath.cos(pitchRad);
    return new Vector3f(
        cosPitch * FastMath.sin(yawRad), -FastMath.sin(pitchRad), cosPitch * FastMath.cos(yawRad));
  }

  /**
   * Interpolates towards another orientation.
   *
   * <p>The yaw travels the short way round. It has to: the orbit camera accumulates {@code yawRad}
   * without ever wrapping it, so a camera the user has spun a couple of times sits at several
   * radians while {@link #fromOffsetDirection} always answers within {@code [−π,π]}. A plain
   * interpolation between the two would spin the view through every intermediate turn.
   *
   * @param target the orientation to head towards
   * @param progress the interpolation parameter, expected in {@code [0,1]}
   * @return the interpolated orientation
   */
  public CameraOrientation towards(CameraOrientation target, float progress) {
    float deltaYaw = wrapToPi(target.yawRad - yawRad);
    return new CameraOrientation(
        yawRad + deltaYaw * progress, pitchRad + (target.pitchRad - pitchRad) * progress);
  }

  /** Folds an angle into {@code [−π,π]}, the shortest signed way round. */
  static float wrapToPi(float angleRad) {
    float wrapped = (angleRad + FastMath.PI) % FastMath.TWO_PI;
    if (wrapped < 0f) {
      wrapped += FastMath.TWO_PI;
    }
    return wrapped - FastMath.PI;
  }
}
