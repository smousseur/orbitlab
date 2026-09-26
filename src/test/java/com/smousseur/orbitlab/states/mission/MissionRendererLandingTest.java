package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The render rules of an object that lands — the primary once its payload has been deorbited, as a
 * debris already does: when it counts as landed, how its render-only seat shrinks onto its ground
 * point just before touchdown, and how its trajectory ribbon fades out with a frozen tip. All three
 * are pure functions of the ephemeris and the clock, so they reverse by themselves under a scrub.
 */
class MissionRendererLandingTest {

  /** Mirror of the private {@code MissionRenderer.TOUCHDOWN_FADE_SECONDS}. */
  private static final double TOUCHDOWN_FADE_SECONDS = 25.0;

  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /** A two-sample ephemeris ending one hour after the epoch at {@code lastAltitudeMeters}. */
  private static MissionEphemeris endingAt(double lastAltitudeMeters) {
    return new MissionEphemeris(
        List.of(point(epoch, 400_000.0), point(epoch.shiftedBy(3_600.0), lastAltitudeMeters)));
  }

  private static MissionEphemerisPoint point(AbsoluteDate time, double altitudeMeters) {
    return new MissionEphemerisPoint(
        time,
        new Vector3D(6_378_137.0 + altitudeMeters, 0.0, 0.0),
        new Vector3D(0.0, 7_500.0, 0.0),
        "Reentry",
        false,
        1_000.0,
        altitudeMeters,
        TrajectoryArc.forBody(SolarSystemBody.EARTH));
  }

  @Test
  void anEphemerisEndingInOrbitNeverLands() {
    MissionEphemeris orbital = endingAt(400_000.0);

    assertFalse(MissionRenderer.hasLanded(orbital, orbital.endDate().shiftedBy(3_600.0)));
  }

  @Test
  void aLandingEphemerisIsNotLandedUpToItsEnd() {
    MissionEphemeris landing = endingAt(0.0);

    assertFalse(MissionRenderer.hasLanded(landing, landing.endDate().shiftedBy(-1.0)));
    assertFalse(MissionRenderer.hasLanded(landing, landing.endDate()));
  }

  @Test
  void aLandingEphemerisIsLandedPastItsEnd() {
    MissionEphemeris landing = endingAt(0.0);

    assertTrue(MissionRenderer.hasLanded(landing, landing.endDate().shiftedBy(1.0)));
  }

  @Test
  void theSeatIsWholeBeforeTheTouchdownWindow() {
    MissionEphemeris landing = endingAt(0.0);

    assertEquals(
        1.0,
        MissionRenderer.touchdownSeatFactor(
            landing, landing.endDate().shiftedBy(-TOUCHDOWN_FADE_SECONDS - 5.0)));
  }

  @Test
  void theSeatShrinksLinearlyOverTheTouchdownWindow() {
    MissionEphemeris landing = endingAt(0.0);

    assertEquals(
        0.5,
        MissionRenderer.touchdownSeatFactor(
            landing, landing.endDate().shiftedBy(-TOUCHDOWN_FADE_SECONDS / 2.0)),
        1e-9);
  }

  @Test
  void theSeatIsGoneAtAndAfterTouchdown() {
    MissionEphemeris landing = endingAt(0.0);

    assertEquals(0.0, MissionRenderer.touchdownSeatFactor(landing, landing.endDate()));
    assertEquals(
        0.0, MissionRenderer.touchdownSeatFactor(landing, landing.endDate().shiftedBy(600.0)));
  }

  @Test
  void theSeatIsWholeForAnEphemerisThatDoesNotLand() {
    MissionEphemeris orbital = endingAt(400_000.0);

    assertEquals(
        1.0, MissionRenderer.touchdownSeatFactor(orbital, orbital.endDate().shiftedBy(-1.0)));
    assertEquals(
        1.0, MissionRenderer.touchdownSeatFactor(orbital, orbital.endDate().shiftedBy(600.0)));
  }

  @Test
  void aTrailInFlightIsOpaqueAndReachesItsObject() {
    AbsoluteDate touchdown = epoch.shiftedBy(3_600.0);

    MissionRenderer.TrailDisplay display =
        MissionRenderer.trailDisplay(false, true, touchdown.shiftedBy(-60.0), touchdown);

    assertEquals(new MissionRenderer.TrailDisplay(true, 1f, false), display);
  }

  @Test
  void aLandedTrailFadesWithAFrozenTip() {
    AbsoluteDate touchdown = epoch.shiftedBy(3_600.0);

    MissionRenderer.TrailDisplay display =
        MissionRenderer.trailDisplay(
            true, true, touchdown.shiftedBy(TOUCHDOWN_FADE_SECONDS / 2.0), touchdown);

    assertTrue(display.visible());
    assertEquals(0.5f, display.opacity(), 1e-6f);
    assertTrue(display.tipFrozen());
  }

  @Test
  void aFadedOutTrailIsHidden() {
    AbsoluteDate touchdown = epoch.shiftedBy(3_600.0);

    MissionRenderer.TrailDisplay display =
        MissionRenderer.trailDisplay(
            true, true, touchdown.shiftedBy(TOUCHDOWN_FADE_SECONDS + 5.0), touchdown);

    assertEquals(new MissionRenderer.TrailDisplay(false, 0f, true), display);
  }

  @Test
  void aTrailItsObjectDoesNotShowStaysHidden() {
    AbsoluteDate touchdown = epoch.shiftedBy(3_600.0);

    MissionRenderer.TrailDisplay display =
        MissionRenderer.trailDisplay(false, false, touchdown.shiftedBy(-60.0), touchdown);

    assertFalse(display.visible());
  }
}
