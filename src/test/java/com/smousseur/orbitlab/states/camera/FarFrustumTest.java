package com.smousseur.orbitlab.states.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.renderer.Camera;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.engine.OrbitCameraConfig;
import org.junit.jupiter.api.Test;

/**
 * Pins the invariant {@code BUG-2} cost a whole field of view: the far camera is never observable
 * with a {@code near} that has lost its {@code top}.
 *
 * <p>Also pins the two near-plane clamps as they stand, because {@code BUG-22} rests on the second
 * one and this refactor deliberately did not touch it.
 */
class FarFrustumTest {

  private static final OrbitCameraConfig CONFIG = EngineConfig.defaultSolarSystem().orbitCamera();

  /** Solar view default: 20 000 units of system radius × 0.04. */
  private static final float SOLAR_DEFAULT = 800f;

  /** Planet view raises the far floor so distant orbits stay drawn. */
  private static final float PLANET_FAR_FLOOR = 50_000f;

  /** One wheel tick, from {@code applyWheelZoom}: {@code distance × exp(-zoomSpeed)}. */
  private static final double ONE_TICK_IN = Math.exp(-0.12);

  @Test
  void theCoupleAlwaysShowsTheFieldOfViewItWasBuiltFor() {
    for (float distance : new float[] {1e-3f, 6e-3f, 0.032f, 1f, SOLAR_DEFAULT, 20_000f}) {
      Camera cam = camera();
      FarFrustum frustum = FarFrustum.of(CONFIG, distance, PLANET_FAR_FLOOR);
      frustum.applyTo(cam);

      assertEquals(
          frustum.fovDegrees(),
          fovShownBy(cam),
          1e-3f,
          "at distance " + distance + " the couple must describe the field of view it was given");
    }
  }

  @Test
  void writingTheNearPlaneAloneMovesTheFieldOfView() {
    Camera cam = camera();
    FarFrustum before = FarFrustum.of(CONFIG, SOLAR_DEFAULT, 0f);
    before.applyTo(cam);

    // What the wheel branch used to do: a new near plane, the old top left behind.
    float nearAfterOneTick =
        FarFrustum.of(CONFIG, (float) (SOLAR_DEFAULT * ONE_TICK_IN), 0f).near();
    cam.setFrustumNear(nearAfterOneTick);

    float drift = fovShownBy(cam) - before.fovDegrees();
    assertTrue(
        drift > 5f && drift < 6f,
        "one wheel tick used to widen the shown field of view by about 5.4 deg, got " + drift);
  }

  @Test
  void theNearPlaneClampsAreUnchanged() {
    // 11 000 km: the near plane still sits on nearMin, and BUG-22's depth guard still separates.
    assertEquals(CONFIG.nearMin(), FarFrustum.of(CONFIG, 0.011f, PLANET_FAR_FLOOR).near(), 0f);

    // 10 000 km: the keep-pivot-visible clamp takes over and drops it to its floor, which is the
    // first of the two conditions BUG-22 needs.
    assertEquals(1e-4f, FarFrustum.of(CONFIG, 0.010f, PLANET_FAR_FLOOR).near(), 0f);
  }

  @Test
  void theFarFloorIsHonoured() {
    assertEquals(PLANET_FAR_FLOOR, FarFrustum.of(CONFIG, 0.032f, PLANET_FAR_FLOOR).far(), 0f);
    assertEquals(
        SOLAR_DEFAULT * CONFIG.farFactor(), FarFrustum.of(CONFIG, SOLAR_DEFAULT, 0f).far(), 1e-3f);
  }

  /** The field of view the camera actually shows, as every consumer of the couple computes it. */
  private static float fovShownBy(Camera cam) {
    return 2f * FastMath.atan2(cam.getFrustumTop(), cam.getFrustumNear()) * FastMath.RAD_TO_DEG;
  }

  private static Camera camera() {
    return new Camera(1280, 720);
  }
}
