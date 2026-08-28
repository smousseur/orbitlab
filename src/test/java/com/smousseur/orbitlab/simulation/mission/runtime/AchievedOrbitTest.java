package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
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

  /** The Moon's equatorial radius, as {@code GravitationalContext.moon()} carries it. */
  private static final double MOON_RADIUS = 1_737_400.0;

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
   * The trap documented on {@link AchievedOrbit}, pinned to a measurement: an <em>instantaneously
   * circular</em> orbit is not circular in mean elements. Its mean eccentricity is about {@code f =
   * (3/2)*J2*(RE/a)^2}, so its mean perigee sits roughly {@code a*f} below the osculating one.
   *
   * <p>Measured 2026-08-05 on the real Falcon Heavy 400 km insertion: osculating 400 000 x 400 114
   * m, mean 390 612 x 409 712 m, i.e. −9 388 m for an {@code a*f} of 9 746 m. This test replays the
   * same physics on a synthetic orbit.
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

    AchievedOrbit achieved = AchievedOrbit.of(state, RE);

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
   * PHY-4 / L6 §5.1 — the µ comes off the state, so an orbit achieved around another body is
   * reported against that body's µ instead of the Earth's.
   *
   * <p>The two halves matter equally. The terrestrial half is the non-regression, and it is an
   * <b>identity</b>: {@code GravitationalContext.earth().mu()} <em>is</em> {@code
   * Constants.WGS84_EARTH_MU}, so no terrestrial mission can read a different number than it did
   * before this change. The lunar half is the defect actually fixed: with the Earth constant the
   * same selenocentric state reports a semi-major axis wrong by the µ ratio (~81), which is not a
   * slightly-off reading but a meaningless one.
   */
  @Test
  void of_readsTheMuOffTheState_soANonTerrestrialOrbitIsReportedAgainstItsOwnBody() {
    double a = MOON_RADIUS + 100_000.0;
    double lunarMu = OrekitService.get().body(SolarSystemBody.MOON).getGM();

    // A state as GravitationalContext.moon() would leave it: setMu(context.mu()) is the only thing
    // that puts the number on the orbit, so declaring it here reproduces exactly that.
    SpacecraftState lunar = circular(a, lunarMu);

    AchievedOrbit fromLunar = AchievedOrbit.of(lunar, MOON_RADIUS);
    assertTrue(fromLunar.hasOsculating(), "osculating elements unavailable on a lunar state");
    assertEquals(a, fromLunar.osculating().semiMajorAxis(), 1.0);

    // MIS-5 / L2 §7.4 — and the apside now lands on the Moon's surface, which is the half L6 could
    // not assert: it repaired the µ and left the radius, so this same reading was 4 640 737 m low.
    // This is the only test of the lot on the production path.
    assertEquals(100_000.0, fromLunar.osculating().perigeeAltitude(), 1.0);
    assertFalse(
        fromLunar.hasMean(),
        "Eckstein-Hechler is an Earth theory and must refuse a selenocentric arc (L2 §6)");

    // What the pre-L6 line did to that same PV: the Earth µ on a selenocentric orbit. Computed here
    // rather than through AchievedOrbit.of, since the point is precisely that of() no longer does
    // it — and the reading has to be visibly wrong, otherwise the assertion above proves nothing.
    //
    // The bar is derived, not recorded. Vis-viva gives a = 1/(2/r − v²/µ); with µ_E ≈ 81·µ_L the
    // velocity term is two orders of magnitude below 2/r, so a collapses towards r/2 whatever the
    // orbit — the wrong µ does not shift the semi-major axis, it halves it.
    double asReadWithTheEarthMu =
        new KeplerianOrbit(
                lunar.getPVCoordinates(),
                lunar.getFrame(),
                lunar.getDate(),
                Constants.WGS84_EARTH_MU)
            .getA();
    assertEquals(
        0.5 * a,
        asReadWithTheEarthMu,
        0.01 * a,
        "the Earth µ on a selenocentric PV must collapse the semi-major axis towards r/2");

    // And the terrestrial non-regression, which is an identity and not a tolerance: earth()'s µ IS
    // the constant the line used to hold, so a terrestrial state reads exactly as before.
    assertEquals(
        Constants.WGS84_EARTH_MU,
        GravitationalContext.earth().mu(),
        0.0,
        "if this ever differs, the L6 §5.1 non-regression argument no longer holds");
  }

  /** A circular equatorial orbit of semi-major axis {@code a}, declared with the given µ. */
  private static SpacecraftState circular(double a, double mu) {
    return new SpacecraftState(
        new KeplerianOrbit(
            a,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            PositionAngleType.TRUE,
            FramesFactory.getGCRF(),
            AbsoluteDate.J2000_EPOCH,
            mu));
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
