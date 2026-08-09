package com.smousseur.orbitlab.states.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.app.view.ViewMode;
import org.junit.jupiter.api.Test;

/**
 * Depth resolution of the near viewport (spec {@code
 * specs/graphics-effects/spacecraft-view-artefacts.md} §5). The trajectory line scintillates where
 * it crosses the Earth's disc because the depth buffer cannot separate it from the surface, and the
 * only quantity that governs that separation is the near plane:
 *
 * <pre>Δz = 2⁻²⁴ · z² · (1/near − 1/far)</pre>
 *
 * <p>With {@code near = 10 m} — where the old factor of {@code 5e-4} pinned it once the camera sat
 * 500 m from the spacecraft — one depth step at the Earth's distance is ~274 km, and a 400 km LEO
 * orbit is barely one and a half steps above the surface: the line wins or loses the depth test per
 * pixel and per frame. These tests pin the near plane by what it buys, not by its value.
 */
class NearFrustumDepthTest {

  /** Distance from a spacecraft in LEO to the Earth's surface below it, in km units. */
  private static final double EARTH_SURFACE_DISTANCE_KM = 6_378.0;

  /** Distance from the camera to the far side of the Earth in spacecraft view, in km units. */
  private static final double EARTH_DISTANCE_KM = 6_778.0;

  /** Camera-to-spacecraft distance in spacecraft view: {@code 5e-7} solar units = 500 m. */
  private static final float SPACECRAFT_FOCUS_KM = 0.5f;

  /** One depth-buffer step at distance {@code z}, for a 24-bit buffer. All lengths in km units. */
  private static double depthStepKm(double z, double near, double far) {
    return Math.pow(2, -24) * z * z * (1.0 / near - 1.0 / far);
  }

  @Test
  void spacecraftView_theLeoTrajectoryClearsTheEarthByManyDepthSteps() {
    float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, SPACECRAFT_FOCUS_KM);

    double step = depthStepKm(EARTH_DISTANCE_KM, near, NearCameraSyncAppState.FAR_MIN);
    double leoAltitudeKm = 400.0;

    assertTrue(
        leoAltitudeKm / step > 10.0,
        () ->
            "a 400 km orbit must sit more than ten depth steps above the surface, got "
                + (leoAltitudeKm / step)
                + " (step = "
                + step
                + " km, near = "
                + near
                + " km)");
  }

  @Test
  void spacecraftView_theNearPlaneStaysWellInFrontOfTheSpacecraftModel() {
    float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, SPACECRAFT_FOCUS_KM);

    // The model is ~100 m across and centred on the near origin, so the closest geometry the camera
    // must still see is at (focus distance − 50 m). Clipping it would trade one artefact for a worse
    // one.
    assertTrue(
        near < SPACECRAFT_FOCUS_KM - 0.05f,
        () -> "the near plane clips the spacecraft model itself: " + near + " km");
  }

  @Test
  void spacecraftView_zoomingOutTowardsThePlanetDoesNotClipTheGround() {
    // The factor is licensed by "the closest content is the spacecraft, at the origin", and that
    // holds at the 500 m focus distance the spec reasons about. It stops holding once the user
    // zooms out: pulling the camera d km towards the nadir from a spacecraft 400 km up leaves only
    // (400 − d) km of ground beneath it, which shrinks faster than 0.2·d grows. Unbounded, the
    // planet would open a hole under the camera from ~333 km out — one artefact traded for a worse
    // one. The guarantee stops at 5 km of clearance, where the camera is landing rather than
    // orbiting.
    for (float d = 1f; d <= 395f; d += 1f) {
      float groundClearance = 400f - d;
      float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, d);
      float distance = d;
      assertTrue(
          near < groundClearance,
          () ->
              "at "
                  + distance
                  + " km from the spacecraft the near plane ("
                  + near
                  + " km) cuts into the ground "
                  + groundClearance
                  + " km below the camera");
    }
  }

  @Test
  void planetView_theNearPlaneStaysInFrontOfTheEarthSurface() {
    // The spacecraft-view factor assumes the closest content sits at the origin. In planet view the
    // origin is the Earth's centre and the closest content is its surface, 6378 km nearer — so the
    // factor must stay conditioned on the view mode until a content-driven near plane exists
    // (spec §9.3, "limite connue").
    float distanceToCentre = 7_000f;
    float near = NearCameraSyncAppState.nearPlane(ViewMode.PLANET, distanceToCentre);

    assertTrue(
        near < distanceToCentre - EARTH_SURFACE_DISTANCE_KM,
        () -> "the near plane clips the planet's surface: " + near + " km");
  }

  @Test
  void loweringTheFarPlaneChangesNothing() {
    // Recorded because the previous diagnostic prescribed exactly this, and it was the one fix in it
    // that could not work: Δz ∝ z²/near as soon as far ≫ near (spec §5.3, §7.2).
    float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, SPACECRAFT_FOCUS_KM);

    double atHundredThousand = depthStepKm(EARTH_DISTANCE_KM, near, 100_000f);
    double atFiftyThousand = depthStepKm(EARTH_DISTANCE_KM, near, 50_000f);

    assertEquals(
        atHundredThousand,
        atFiftyThousand,
        atHundredThousand * 1e-3,
        "halving the far plane must not move the depth step by even a tenth of a percent");
  }
}
