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
 *
 * <h2>The pivot is the destination, from the first frame ({@code BUG-5})</h2>
 *
 * <p>It used to be a straight line walked between the two endpoints, while the distance collapsed
 * geometrically. The two schedules disagree, and measurably: on a solar-view focus of Pluto the
 * camera reached its final <em>distance to the pivot</em> — 5 951 km — while the pivot was still
 * <b>701 808 km short of Pluto</b>, so the last frame absorbed 700 000 km and took the planet from
 * 1.7 px of projected radius to 234.7 px. A factor 138 in one frame, and for the whole crossing
 * before it the destination sat under a pixel: the approach never showed what it was approaching.
 *
 * <p>So the destination is the pivot throughout, and what ramps geometrically is the camera's
 * distance <em>to it</em> — from where the camera happens to stand when the transition starts, down
 * to the framing it settles at. Remaining travel and framing distance become the same number, which
 * is the property the old pair could not hold. The bearing does the rest: it interpolates from
 * where the camera already is, as seen from the destination, so the first frame renders exactly
 * where the camera already was and nothing jumps at either end.
 */
final class CameraTransition {

  private final TransitionTarget target;
  private final Supplier<Vector3f> pivot;
  private final CameraStation approach;
  private final CameraStation arrival;
  private final CameraStation restore;
  private final CameraTransitionConfig config;

  private float elapsedSec;

  /**
   * @param target the focus to apply on the last frame
   * @param pivot where the destination sits in the rendered frame, re-read every frame
   * @param approach where the camera stands when the transition starts, expressed about the
   *     destination — so that the first frame draws exactly the view already on screen
   * @param arrival where it settles
   * @param restore where it came from, expressed about the pivot it had then, for a cancellation
   * @param config the tuning
   */
  CameraTransition(
      TransitionTarget target,
      Supplier<Vector3f> pivot,
      CameraStation approach,
      CameraStation arrival,
      CameraStation restore,
      CameraTransitionConfig config) {
    this.target = Objects.requireNonNull(target, "target");
    this.pivot = Objects.requireNonNull(pivot, "pivot");
    this.approach = Objects.requireNonNull(approach, "approach");
    this.arrival = Objects.requireNonNull(arrival, "arrival");
    this.restore = Objects.requireNonNull(restore, "restore");
    this.config = Objects.requireNonNull(config, "config");
  }

  /** The focus state to apply once the animation completes. */
  TransitionTarget target() {
    return target;
  }

  /** The exact distance to settle on at the end, free of any interpolation drift. */
  float targetDistance() {
    return arrival.distance();
  }

  /** The distance the camera started from, restored if the transition is cancelled mid-flight. */
  float sourceDistance() {
    return restore.distance();
  }

  /** The exact orientation to settle on at the end. */
  CameraOrientation targetOrientation() {
    return arrival.orientation();
  }

  /** The orientation the camera started from, restored if the transition is cancelled. */
  CameraOrientation sourceOrientation() {
    return restore.orientation();
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
   * The pivot for the current frame: the destination itself, re-read because it keeps moving while
   * the simulation clock runs. It does not interpolate — see the class docstring for the frame the
   * measurement rejected.
   *
   * @return a freshly allocated pivot position, in the current rendered frame
   */
  Vector3f currentPivot() {
    return sanitize(pivot.get());
  }

  /**
   * The camera orientation for the current frame.
   *
   * @return the interpolated orientation
   */
  CameraOrientation currentOrientation() {
    return approach.orientation().towards(arrival.orientation(), orientationProgress());
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
   * <p>Since the pivot is the destination, this is the distance to the destination — and that is
   * what makes the ramp describe the approach instead of merely accompanying it.
   *
   * @return the interpolated distance
   */
  float currentDistance() {
    float u = easedProgress();
    float from = approach.distance();
    float to = arrival.distance();
    if (from > 0f && to > 0f) {
      return (float) Math.exp(Math.log(from) + u * (Math.log(to) - Math.log(from)));
    }
    return from + (to - from) * u;
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
