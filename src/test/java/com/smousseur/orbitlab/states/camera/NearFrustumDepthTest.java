package com.smousseur.orbitlab.states.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.app.view.ViewMode;
import org.junit.jupiter.api.Test;

/**
 * Depth resolution of the near viewport (spec {@code
 * docs/graphics-effects/spacecraft-view-artefacts.md} §5). The trajectory line scintillates where
 * it crosses the Earth's disc because the depth buffer cannot separate it from the surface, and the
 * only quantity that governs that separation is the near plane:
 *
 * <pre>Δz = 2⁻²⁴ · z² · (1/near − 1/far)</pre>
 *
 * <p>With {@code near = 10 m} — where the old factor of {@code 5e-4} pinned it once the camera sat
 * 500 m from the spacecraft — one depth step at the Earth's distance is ~274 km, and a 400 km LEO
 * orbit is barely one and a half steps above the surface: the line wins or loses the depth test per
 * pixel and per frame. These tests pin the near plane by what it buys, not by its value.
 *
 * <p><b>The third viewport, and why there is none</b> (spec {@code
 * docs/multi-corps/07-conception-L5.md} §5.3). Roadmap open question n° 4 asked whether Earth +
 * Moon + spacecraft in one frame forces a third "mid" viewport, reverse-Z or a logarithmic depth
 * buffer, and named this class as the instrument to decide it. The measurement below says no. One
 * depth step at the Moon's distance is ~88 000 km, fourteen Earth radii — but nothing out there is
 * competing for depth: the near viewport draws exactly one globe, on the origin, where the step is
 * ~27 km. The far end of the trajectory disputes depth only with itself. What was actually broken
 * was the far <em>clip</em> plane, and that is one constant.
 */
class NearFrustumDepthTest {

  /** Distance from a spacecraft in LEO to the Earth's surface below it, in km units. */
  private static final double EARTH_SURFACE_DISTANCE_KM = 6_378.0;

  /** Distance from the camera to the far side of the Earth in spacecraft view, in km units. */
  private static final double EARTH_DISTANCE_KM = 6_778.0;

  /** Camera-to-spacecraft distance in spacecraft view: {@code 5e-7} solar units = 500 m. */
  private static final float SPACECRAFT_FOCUS_KM = 0.5f;

  /** Mean Earth-Moon distance, in km units: the far end of a lunar transfer. */
  private static final double LUNAR_DISTANCE_KM = 384_400.0;

  /** The far floor before PHY-4 / L5, kept so the two can be compared rather than described. */
  private static final float PREVIOUS_FAR_MIN = 100_000f;

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
    // must still see is at (focus distance − 50 m). Clipping it would trade one artefact for a
    // worse
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
    for (int km = 1; km <= 395; km++) {
      float d = km;
      float groundClearance = 400f - d;
      float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, d);
      assertTrue(
          near < groundClearance,
          () ->
              "at "
                  + d
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
  void theFarPlaneCoversTheWholeLunarTransfer() {
    // The defect this floor actually fixes, and the only one of the three lunar cases that was
    // visible rather than merely imprecise: at 100 000 km, everything past a quarter of the way to
    // the Moon was clipped out of the near viewport before any depth question arose.
    assertTrue(
        NearCameraSyncAppState.FAR_MIN > LUNAR_DISTANCE_KM,
        () ->
            "a lunar transfer reaches "
                + LUNAR_DISTANCE_KM
                + " km and the near viewport clips at "
                + NearCameraSyncAppState.FAR_MIN
                + " km");
  }

  @Test
  void raisingTheFarFloorCostsNothingWhereTheDepthBudgetIsSpent() {
    // The licence for the value above, and the mirror image of loweringTheFarPlaneChangesNothing:
    // Δz ∝ z²/near as soon as far ≫ near, so the floor can be sized by what has to stay in frame
    // rather than traded against depth precision. Asserted where the budget is actually tight — the
    // LEO trajectory over the Earth's disc, which is what §5 of the spec was written about.
    float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, SPACECRAFT_FOCUS_KM);

    double before = depthStepKm(EARTH_DISTANCE_KM, near, PREVIOUS_FAR_MIN);
    double after = depthStepKm(EARTH_DISTANCE_KM, near, NearCameraSyncAppState.FAR_MIN);

    assertEquals(
        before,
        after,
        before * 1e-3,
        "raising the far floor five-fold must not move the depth step by even a tenth of a percent");
  }

  @Test
  void atLunarDistanceOneDepthStepIsEnormous_andThatIsAccepted() {
    // Pinned, not fixed. Recorded the way PHY-4 / L2 kept its tidal acceleration in a logged
    // reference: a reader who computes this number and finds it alarming must be able to see that
    // it was measured and accepted, with the reason attached, rather than missed.
    //
    // Nothing out at the Moon's distance competes for depth. SceneGraph.showBodySpatial culls every
    // near-viewport globe but one, and that one sits on the origin — where the step is ~27 km, the
    // figure this class already pins. The far end of the trace disputes depth with itself alone.
    float near = NearCameraSyncAppState.nearPlane(ViewMode.SPACECRAFT, SPACECRAFT_FOCUS_KM);
    double step = depthStepKm(LUNAR_DISTANCE_KM, near, NearCameraSyncAppState.FAR_MIN);

    assertEquals(
        88_000.0,
        step,
        1_000.0,
        "the measured depth step at the Moon's distance, which no third viewport is bought to fix");
  }

  @Test
  void loweringTheFarPlaneChangesNothing() {
    // Recorded because the previous diagnostic prescribed exactly this, and it was the one fix in
    // it
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
