package com.smousseur.orbitlab.states.camera;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.TransitionTarget;
import com.smousseur.orbitlab.engine.CameraTransitionConfig;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One camera transition in flight: the endpoints it interpolates between, and how far along it is.
 *
 * <p>Mutable — {@link #advance(float)} is called once per frame — and deliberately free of any JME3
 * or Orekit dependency beyond {@link Vector3f}, so the interpolation can be pinned by a plain unit
 * test. Everything that has to look at the scene graph lives behind the two pivot suppliers, which
 * {@link CameraTransitionAppState} provides and re-evaluates on every frame: the simulation clock
 * keeps running during a transition, so both endpoints are moving targets.
 *
 * <p>The orientation is the exception — it is fixed at construction. Over 2.5 real seconds the
 * bearing from one body to another moves by less than a thousandth of a degree even at ×1000, and
 * chasing a live target would only make the rotation trail behind it for nothing.
 */
final class CameraTransition {

  private final TransitionTarget target;
  private final Supplier<Vector3f> srcPivot;
  private final Supplier<Vector3f> dstPivot;
  private final float srcDistance;
  private final float dstDistance;
  private final CameraOrientation srcOrientation;
  private final CameraOrientation dstOrientation;
  private final CameraTransitionConfig config;

  private float elapsedSec;

  CameraTransition(
      TransitionTarget target,
      Supplier<Vector3f> srcPivot,
      Supplier<Vector3f> dstPivot,
      float srcDistance,
      float dstDistance,
      CameraOrientation srcOrientation,
      CameraOrientation dstOrientation,
      CameraTransitionConfig config) {
    this.target = Objects.requireNonNull(target, "target");
    this.srcPivot = Objects.requireNonNull(srcPivot, "srcPivot");
    this.dstPivot = Objects.requireNonNull(dstPivot, "dstPivot");
    this.srcDistance = srcDistance;
    this.dstDistance = dstDistance;
    this.srcOrientation = Objects.requireNonNull(srcOrientation, "srcOrientation");
    this.dstOrientation = Objects.requireNonNull(dstOrientation, "dstOrientation");
    this.config = Objects.requireNonNull(config, "config");
  }

  /** The focus state to apply once the animation completes. */
  TransitionTarget target() {
    return target;
  }

  /** The exact distance to settle on at the end, free of any interpolation drift. */
  float targetDistance() {
    return dstDistance;
  }

  /** The distance the camera started from, restored if the transition is cancelled mid-flight. */
  float sourceDistance() {
    return srcDistance;
  }

  /** The exact orientation to settle on at the end. */
  CameraOrientation targetOrientation() {
    return dstOrientation;
  }

  /** The orientation the camera started from, restored if the transition is cancelled. */
  CameraOrientation sourceOrientation() {
    return srcOrientation;
  }

  /**
   * Advances the animation by one frame, clamped so progress never overshoots.
   *
   * @param tpf the frame duration in real seconds
   */
  void advance(float tpf) {
    if (Float.isFinite(tpf) && tpf > 0f) {
      elapsedSec = Math.min(elapsedSec + tpf, config.durationSec());
    }
  }

  /** Whether the animation has reached its end and the target state should be applied. */
  boolean isFinished() {
    return elapsedSec >= config.durationSec();
  }

  /** The eased progress of the pivot and the distance, in {@code [0,1]}. */
  float easedProgress() {
    return config.easing().apply(elapsedSec / config.durationSec());
  }

  /**
   * The eased progress of the orientation, which runs on a shorter window and then holds — see
   * {@link CameraTransitionConfig#defaults()} for why it leads rather than sharing the schedule
   * above.
   *
   * @return the orientation progress, in {@code [0,1]}
   */
  float orientationProgress() {
    float window = config.durationSec() * config.orientationLeadFraction();
    return config.easing().apply(Math.min(1f, elapsedSec / window));
  }

  /**
   * The pivot for the current frame: a straight line between the two live endpoints, walked at the
   * eased pace.
   *
   * @return a freshly allocated pivot position, in the current rendered frame
   */
  Vector3f currentPivot() {
    Vector3f from = sanitize(srcPivot.get());
    Vector3f to = sanitize(dstPivot.get());
    return from.interpolateLocal(to, easedProgress());
  }

  /**
   * The camera orientation for the current frame.
   *
   * @return the interpolated orientation
   */
  CameraOrientation currentOrientation() {
    return srcOrientation.towards(dstOrientation, orientationProgress());
  }

  /**
   * The camera distance for the current frame.
   *
   * <p>Interpolated <em>geometrically</em>, not linearly: the targets span nine orders of magnitude
   * (800 units for the whole system down to {@code 5e-7} for a spacecraft, both in solar units),
   * and a linear ramp between two such numbers spends almost its entire duration in the
   * neighbourhood of the larger one before collapsing over the last few frames — the transition
   * would read as a stall followed by a jump cut. A constant ratio per unit of eased progress is
   * also what the camera's own zoom already does (see {@code OrbitCameraAppState.applyWheelZoom},
   * an exponential dolly), so a transition and a wheel zoom covering the same span now feel alike.
   *
   * @return the interpolated distance
   */
  float currentDistance() {
    float u = easedProgress();
    if (srcDistance > 0f && dstDistance > 0f) {
      double logSrc = Math.log(srcDistance);
      double logDst = Math.log(dstDistance);
      return (float) Math.exp(logSrc + u * (logDst - logSrc));
    }
    return srcDistance + (dstDistance - srcDistance) * u;
  }

  /**
   * Guards against a supplier that cannot answer — a body whose spatial is not in the graph yet, or
   * a mission whose ephemeris is being recomputed. Falling back to the origin keeps the camera on
   * the rendered frame's centre rather than sending it to infinity.
   */
  private static Vector3f sanitize(Vector3f v) {
    if (v == null || !Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z)) {
      return new Vector3f();
    }
    return v.clone();
  }
}
