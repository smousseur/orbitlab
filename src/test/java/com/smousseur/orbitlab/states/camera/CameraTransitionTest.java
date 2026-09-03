package com.smousseur.orbitlab.states.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.TransitionTarget;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.CameraTransitionConfig;
import com.smousseur.orbitlab.engine.Easing;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Unit tests for the interpolation carried by a single in-flight camera transition. */
class CameraTransitionTest {

  private static final float TOLERANCE = 1e-5f;
  private static final float DURATION = 2.5f;
  private static final float LEAD = 0.35f;

  private static final CameraOrientation FLAT = new CameraOrientation(0f, 0f);

  private static CameraTransitionConfig config(Easing easing) {
    return new CameraTransitionConfig(DURATION, easing, LEAD);
  }

  private static CameraTransition transition(
      Vector3f to, float srcDistance, float dstDistance, Easing easing) {
    return orienting(to, srcDistance, dstDistance, easing, FLAT, FLAT);
  }

  private static CameraTransition orienting(
      Vector3f to,
      float srcDistance,
      float dstDistance,
      Easing easing,
      CameraOrientation srcOrientation,
      CameraOrientation dstOrientation) {
    return new CameraTransition(
        new TransitionTarget.Planet(SolarSystemBody.EARTH),
        () -> to,
        new CameraStation(srcDistance, srcOrientation),
        new CameraStation(dstDistance, dstOrientation),
        new CameraStation(srcDistance, srcOrientation),
        config(easing));
  }

  private static CameraTransition solarTransition(java.util.function.Supplier<Vector3f> to) {
    return new CameraTransition(
        new TransitionTarget.Solar(),
        to,
        new CameraStation(1f, FLAT),
        new CameraStation(1f, FLAT),
        new CameraStation(1f, FLAT),
        config(Easing.LINEAR));
  }

  @Test
  void advanceReachesTheEndAndClampsThere() {
    CameraTransition t = transition(new Vector3f(), 1f, 1f, Easing.LINEAR);

    assertFalse(t.isFinished());
    t.advance(1f);
    assertFalse(t.isFinished());
    assertEquals(0.4f, t.easedProgress(), TOLERANCE);

    t.advance(10f);
    assertTrue(t.isFinished());
    assertEquals(1f, t.easedProgress(), TOLERANCE);
  }

  @Test
  void nonPositiveFrameTimesDoNotMoveTheAnimation() {
    CameraTransition t = transition(new Vector3f(), 1f, 1f, Easing.LINEAR);

    t.advance(0f);
    t.advance(-1f);
    t.advance(Float.NaN);

    assertEquals(0f, t.easedProgress(), TOLERANCE);
  }

  @Test
  void smoothstepIsSymmetricAroundItsMidpoint() {
    CameraTransition t = transition(new Vector3f(), 1f, 1f, Easing.SMOOTHSTEP);

    assertEquals(0f, t.easedProgress(), TOLERANCE);
    t.advance(DURATION / 4f);
    assertEquals(0.15625f, t.easedProgress(), TOLERANCE);
    t.advance(DURATION / 4f);
    assertEquals(0.5f, t.easedProgress(), TOLERANCE);
    t.advance(DURATION / 4f);
    assertEquals(0.84375f, t.easedProgress(), TOLERANCE);
    t.advance(DURATION / 4f);
    assertEquals(1f, t.easedProgress(), TOLERANCE);
  }

  @Test
  void pivotIsTheDestinationFromTheFirstFrame() {
    Vector3f destination = new Vector3f(6f, 8f, 4f);
    CameraTransition t = transition(destination, 1f, 1f, Easing.LINEAR);

    // It does not walk there: what closes in is the distance to it (see BUG-5, the last frame
    // used to absorb 700 000 km because the two were on different schedules).
    assertEquals(destination, t.currentPivot());
    t.advance(DURATION / 2f);
    assertEquals(destination, t.currentPivot());
    t.advance(DURATION / 2f);
    assertEquals(destination, t.currentPivot());
  }

  @Test
  void pivotFollowsADestinationThatMovesDuringTheTransition() {
    AtomicReference<Vector3f> movingTarget = new AtomicReference<>(new Vector3f(10f, 0f, 0f));
    CameraTransition t = solarTransition(movingTarget::get);

    t.advance(DURATION / 2f);
    assertEquals(new Vector3f(10f, 0f, 0f), t.currentPivot());

    movingTarget.set(new Vector3f(20f, 0f, 0f));
    assertEquals(new Vector3f(20f, 0f, 0f), t.currentPivot());
  }

  @Test
  void anUnusablePivotFallsBackToTheOrigin() {
    CameraTransition t = solarTransition(() -> new Vector3f(Float.NaN, 0f, 0f));

    t.advance(DURATION / 2f);
    assertEquals(new Vector3f(), t.currentPivot());
  }

  @Test
  void theFirstFrameRendersTheStationTheCameraAlreadyHolds() {
    CameraOrientation approach = new CameraOrientation(1.2f, -0.3f);
    CameraTransition t =
        orienting(new Vector3f(100f, 0f, 0f), 4669f, 0.006f, Easing.SMOOTHSTEP, approach, FLAT);

    // Nothing may move on the frame the transition starts: the approach station is the camera's
    // own position restated about the destination, so distance and bearing are already correct.
    assertEquals(4669f, t.currentDistance(), TOLERANCE);
    assertEquals(approach.yawRad(), t.currentOrientation().yawRad(), TOLERANCE);
    assertEquals(approach.pitchRad(), t.currentOrientation().pitchRad(), TOLERANCE);
  }

  @Test
  void distanceIsInterpolatedGeometrically() {
    // A span of six decades, the order of magnitude between the solar view and a spacecraft: the
    // midpoint must be the geometric mean, not the arithmetic one that would still sit at 500.
    CameraTransition t = transition(new Vector3f(), 1000f, 1e-3f, Easing.LINEAR);

    assertEquals(1000f, t.currentDistance(), TOLERANCE);
    t.advance(DURATION / 2f);
    assertEquals(1f, t.currentDistance(), TOLERANCE);
    t.advance(DURATION / 2f);
    assertEquals(1e-3f, t.currentDistance(), 1e-9f);
  }

  @Test
  void distanceFallsBackToALinearRampWhenAnEndpointIsNotPositive() {
    CameraTransition t = transition(new Vector3f(), 0f, 100f, Easing.LINEAR);

    t.advance(DURATION / 2f);
    assertEquals(50f, t.currentDistance(), TOLERANCE);
  }

  @Test
  void orientationIsDoneTurningBeforeTheTravelIsOver() {
    CameraTransition t =
        orienting(new Vector3f(), 1f, 1f, Easing.LINEAR, FLAT, new CameraOrientation(1f, 0f));

    // Full lead window in, the orientation has arrived while the pivot is barely a third of the
    // way.
    t.advance(DURATION * LEAD);
    assertEquals(1f, t.orientationProgress(), TOLERANCE);
    assertEquals(LEAD, t.easedProgress(), TOLERANCE);
    assertEquals(1f, t.currentOrientation().yawRad(), TOLERANCE);

    // And it holds there rather than overshooting for the rest of the transition.
    t.advance(DURATION / 2f);
    assertEquals(1f, t.orientationProgress(), TOLERANCE);
    assertEquals(1f, t.currentOrientation().yawRad(), TOLERANCE);
  }

  @Test
  void orientationLeadsTheTravelAtEveryPointOfTheWindow() {
    CameraTransition t =
        orienting(new Vector3f(), 1f, 1f, Easing.LINEAR, FLAT, new CameraOrientation(1f, 0f));

    t.advance(DURATION * LEAD / 2f);
    assertEquals(0.5f, t.orientationProgress(), TOLERANCE);
    assertEquals(LEAD / 2f, t.easedProgress(), TOLERANCE);
  }

  @Test
  void aSpunCameraTurnsTheShortWayRound() {
    // The orbit camera never wraps its yaw, so the user can leave it at 3 rad while the computed
    // bearing comes back as -3: the delta is 0.28 rad the short way, 6.0 the wrong way.
    CameraTransition t =
        orienting(
            new Vector3f(),
            1f,
            1f,
            Easing.LINEAR,
            new CameraOrientation(3f, 0f),
            new CameraOrientation(-3f, 0f));

    t.advance(DURATION * LEAD / 2f);
    float yaw = t.currentOrientation().yawRad();

    assertEquals(3f + (FastMath.TWO_PI - 6f) / 2f, yaw, TOLERANCE);
    assertTrue(yaw > 3f, "the short way round goes up through pi, not down through zero");
  }

  @Test
  void pitchIsInterpolatedDirectly() {
    CameraTransition t =
        orienting(
            new Vector3f(),
            1f,
            1f,
            Easing.LINEAR,
            new CameraOrientation(0f, -1f),
            new CameraOrientation(0f, 1f));

    t.advance(DURATION * LEAD / 2f);
    assertEquals(0f, t.currentOrientation().pitchRad(), TOLERANCE);
  }

  @Test
  void theExactEndpointIsKeptForTheFinalSnap() {
    CameraTransition t = transition(new Vector3f(), 800f, 5e-7f, Easing.SMOOTHSTEP);

    assertEquals(5e-7f, t.targetDistance(), 1e-12f);
  }
}
