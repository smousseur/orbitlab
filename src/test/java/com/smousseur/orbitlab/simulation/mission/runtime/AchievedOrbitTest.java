package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Covers the production path the fast suites could not reach: {@code AchievedOrbit.of} is only
 * called from {@code MissionOptimizer.optimize()}, hence only by the optimization tests, which run
 * for minutes. These two tests exercise the same reading on a hand-built state, in milliseconds.
 */
class AchievedOrbitTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /** Kourou, {@code EarthMission.DEFAULT_LATITUDE}. */
  private static final double INCLINATION = FastMath.toRadians(5.23);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * The trap documented on {@link AchievedOrbit}, pinned to a measurement: an
   * <em>instantaneously circular</em> orbit is not circular in mean elements. Its mean eccentricity
   * is about {@code f = (3/2)*J2*(RE/a)^2}, so its mean perigee sits roughly {@code a*f} below the
   * osculating one.
   *
   * <p>Measured 2026-08-05 on the real Falcon Heavy 400 km insertion: osculating
   * 400 000 x 400 114 m, mean 390 612 x 409 712 m, i.e. −9 388 m for an {@code a*f} of 9 746 m.
   * This test replays the same physics on a synthetic orbit.
   *
   * <p>If this test starts failing, it is not a mission's targeting that moved — it is the
   * osculating-to-mean conversion. That confusion is exactly what the assertion exists to prevent.
   */
  @Test
  void of_readsBothConventions_andTheMeanOfACircularOrbitIsNotCircular() {
    double a = RE + 400_000.0;
    double j2 = -Constants.WGS84_EARTH_C20;
    double af = a * 1.5 * j2 * (RE / a) * (RE / a);

    SpacecraftState state =
        new SpacecraftState(
            new KeplerianOrbit(
                a,
                1.0e-5,
                INCLINATION,
                0.0,
                0.0,
                0.0,
                PositionAngleType.TRUE,
                FramesFactory.getGCRF(),
                AbsoluteDate.J2000_EPOCH,
                Constants.WGS84_EARTH_MU));

    AchievedOrbit achieved = AchievedOrbit.of(state);

    // The osculating orbit is read as it is, with no conversion.
    assertTrue(achieved.hasOsculating(), "osculating elements unavailable");
    assertEquals(a, achieved.osculating().semiMajorAxis(), 1.0);
    assertEquals(1.0e-5, achieved.osculating().eccentricity(), 1.0e-7);

    assertTrue(achieved.hasMean(), "mean orbit unavailable on a bound circular orbit");
    OrbitElements mean = achieved.mean();

    // The mean eccentricity is of order f, not of order 0. That is the fact which forbids
    // displaying the mean orbit alone for a mission requested circular.
    double f = 1.5 * j2 * (RE / a) * (RE / a);
    assertTrue(
        mean.eccentricity() > 0.5 * f && mean.eccentricity() < 1.5 * f,
        () ->
            String.format(
                "mean eccentricity %.3e is not of order f=%.3e — the circular-orbit trap"
                    + " documented on AchievedOrbit no longer holds",
                mean.eccentricity(), f));

    // And the mean perigee therefore sits about a*f below the osculating one, in that direction.
    double drop = achieved.osculating().perigeeAltitude() - mean.perigeeAltitude();
    assertTrue(
        drop > 0.5 * af && drop < 1.5 * af,
        () ->
            String.format(
                "mean perigee sits %.0f m below the osculating one, expected about a*f=%.0f m",
                drop, af));
  }

  /**
   * The degraded mode as the caller sees it: when a reading is unavailable the report says
   * "unavailable" and does not throw.
   *
   * <p>This is not decorative caution. {@code MissionLoadEvaluator} translates any {@code
   * RuntimeException} escaping {@code optimize()} into "lambda infeasible", so an exception leaking
   * out of a reporting line would silently move the lambda retained by the propellant-sizing sweep
   * — a mission number shifted by a log.
   */
  @Test
  void formatting_saysUnavailableRatherThanThrowing() {
    OrbitElements osculating = new OrbitElements(7_000_000.0, 0.001, 0.1, 614_863.0, 628_863.0);

    AchievedOrbit meanMissing = new AchievedOrbit(osculating, null);
    assertTrue(meanMissing.hasOsculating());
    assertFalse(meanMissing.hasMean());
    assertEquals(osculating.format(), meanMissing.formatOsculating());
    assertEquals("unavailable", meanMissing.formatMean());

    assertFalse(AchievedOrbit.UNAVAILABLE.hasOsculating());
    assertFalse(AchievedOrbit.UNAVAILABLE.hasMean());
    assertEquals("unavailable", AchievedOrbit.UNAVAILABLE.formatOsculating());
    assertEquals("unavailable", AchievedOrbit.UNAVAILABLE.formatMean());
  }
}
