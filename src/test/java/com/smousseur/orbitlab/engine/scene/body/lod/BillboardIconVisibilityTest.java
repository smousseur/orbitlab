package com.smousseur.orbitlab.engine.scene.body.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.junit.jupiter.api.Test;

/**
 * Pins the behind-camera rejection of {@code BUG-22}: the solar system drawn as icons behind Pluto,
 * while the camera looked at Pluto's lit face and had all of it behind its own back.
 *
 * <p>The configuration below is the one measured at the time: planet view, near plane on the {@code
 * 1e-4} floor its keep-pivot-visible clamp imposes below ~10 300 km, and the Sun 5 324 units behind
 * the camera. It is reproduced exactly rather than approximated, because the whole defect lives in
 * the last two digits of a {@code float}.
 */
class BillboardIconVisibilityTest {

  /** Near plane in planet view once the camera is closer than ~10 300 km to the pivot. */
  private static final float NEAR_ON_ITS_FLOOR = 0.0001f;

  /** Near plane above that distance, where the depth guard still separated. */
  private static final float NEAR_ABOVE_THE_CLAMP = 0.001f;

  /** Pluto's heliocentric distance in solar units, and so the Sun's distance seen from it. */
  private static final float SUN_DISTANCE_UNITS = 5324f;

  /** Field of view the adaptive curve gives at Pluto's arrival framing. */
  private static final float FOV_AT_PLUTO_FOCUS_DEG = 34.1f;

  @Test
  void aBodyBehindTheCameraIsRejectedWhateverTheNearPlane() {
    Vector3f behind = new Vector3f(0f, 0f, SUN_DISTANCE_UNITS);

    assertTrue(
        BillboardIconView.isBehindCamera(planetView(NEAR_ON_ITS_FLOOR), behind),
        "the Sun is behind the camera and must be rejected on the near-plane floor");
    assertTrue(
        BillboardIconView.isBehindCamera(planetView(NEAR_ABOVE_THE_CLAMP), behind),
        "and above the clamp too — the answer must not depend on the frustum");
  }

  @Test
  void theProjectedDepthGoesBlindOnTheNearFloor() {
    Vector3f behind = new Vector3f(0f, 0f, SUN_DISTANCE_UNITS);

    // This is BUG-22 itself: the depth a behind-camera point returns is 1 + 2*near/distance, and
    // on the 1e-4 floor that excess (3.8e-8) is under half an ulp of 1f, so it rounds away.
    assertEquals(
        1.0f,
        planetView(NEAR_ON_ITS_FLOOR).getScreenCoordinates(behind).z,
        0f,
        "the z > 1 test sees nothing wrong here, which is why the sign test exists");
    assertTrue(
        planetView(NEAR_ABOVE_THE_CLAMP).getScreenCoordinates(behind).z > 1f,
        "one clamp higher it did separate — hence the defect appearing only past a zoom");
  }

  @Test
  void aBodyInFrontIsNotRejected() {
    Camera cam = planetView(NEAR_ON_ITS_FLOOR);

    assertFalse(
        BillboardIconView.isBehindCamera(cam, new Vector3f(0f, 0f, -SUN_DISTANCE_UNITS)),
        "a body straight ahead must keep its icon");
    assertFalse(
        BillboardIconView.isBehindCamera(cam, new Vector3f(SUN_DISTANCE_UNITS, 0f, -1f)),
        "and so must one barely inside the half-space, however far off-axis");
  }

  /** A camera at the origin looking down {@code -Z}, framed as a planet focus frames a body. */
  private static Camera planetView(float near) {
    Camera cam = new Camera(1280, 720);
    cam.setLocation(Vector3f.ZERO);
    cam.lookAtDirection(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);
    cam.setFrustumPerspective(FOV_AT_PLUTO_FOCUS_DEG, 1280f / 720f, near, 50_000f);
    return cam;
  }
}
