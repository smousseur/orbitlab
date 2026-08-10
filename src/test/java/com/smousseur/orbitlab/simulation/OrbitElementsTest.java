package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.PropagationType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.EcksteinHechlerPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

class OrbitElementsTest {

  private static final double RE = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private static final Logger logger = LogManager.getLogger(OrbitElementsTest.class);

  /** J2 = −C20, WGS84 field — the constant of the closed form in spec section 1.1. */
  private static final double J2 = -Constants.WGS84_EARTH_C20;

  /** Kourou, {@code EarthMission.DEFAULT_LATITUDE}: the inclination of the measured profiles. */
  private static final double INCLINATION = FastMath.toRadians(5.23);

  /**
   * Near-circular, at the order of magnitude of the real insertions (measured e between 4.7e-6 and
   * 6.3e-6). This is the regime that disqualified Brouwer-Lyddane by measurement (spec section
   * 3.2.1): its fixed point diverges there or, worse, converges to a mean perigee that depends on
   * the sampling phase.
   */
  private static final double ECCENTRICITY = 1.0e-5;

  private static final int SAMPLES = 200;

  /**
   * Agreement margin between the osculating oscillation actually propagated and the closed form
   * (J2, first order). This tolerance compares <b>no</b> mean theory against the closed form: it
   * validates the series the generator produces, before the conversion is judged on it. Measured
   * ratio 0.980 at all three altitudes; 20% leaves room for the odd zonals without letting a sign,
   * factor or convention error through.
   */
  private static final double CLOSED_FORM_TOLERANCE = 0.20;

  /**
   * Short-period residual the conversion leaves behind. Measured 2026-08-05: 625 m at 400 km, 571 m
   * at 600 km, 482 m at 1000 km — i.e. ~97% of the osculating oscillation removed, not 100%. This
   * is not a convergence defect: at threshold 1e-15 and 5000 iterations the result is
   * bit-identical. It is Eckstein-Hechler's own modelling residual.
   *
   * <p>The bar stays 19 times below the ~19 km osculating oscillation, so it would fail loudly if
   * the conversion started returning osculating elements in disguise, which is the only defect it
   * has to catch.
   *
   * <p><b>The margin is only 1.6x</b> (625 m measured against 1000 m). It was deliberately not
   * widened: a ceiling set far above the measurement would say nothing any more. If an Orekit
   * version bump turns this test red with no other change, the modelling residual has moved —
   * noticing that is the point, and the answer is to re-measure the three altitudes and re-set the
   * bar, not to raise it blindly.
   */
  private static final double MEAN_RESIDUAL_CEILING_M = 1_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * Reported altitudes are spherical-equatorial — {@code a(1±e) − RE} — exactly the convention of
   * today's call sites (spec section 3.3). This test pins it down: switching to geodetic altitude
   * would move every reported figure by ~200 m.
   */
  @Test
  void osculating_reportsSphericalEquatorialApsides() {
    double a = RE + 500_000.0;
    double e = 0.01;
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            a,
            e,
            0.1,
            0.2,
            0.3,
            0.4,
            PositionAngleType.TRUE,
            FramesFactory.getGCRF(),
            AbsoluteDate.J2000_EPOCH,
            Constants.WGS84_EARTH_MU);

    OrbitElements elements = OrbitElements.osculating(orbit);

    assertEquals(a, elements.semiMajorAxis(), 1e-6);
    assertEquals(e, elements.eccentricity(), 1e-12);
    assertEquals(0.1, elements.inclination(), 1e-12);
    assertEquals(a * (1.0 - e) - RE, elements.perigeeAltitude(), 1e-6);
    assertEquals(a * (1.0 + e) - RE, elements.apogeeAltitude(), 1e-6);
  }

  /**
   * Two reporting sites depend on this string (the insertion log of the optimization tests and
   * {@code MissionOptimizer}). Pinning it here keeps a format change from going unnoticed.
   *
   * <p>Scientific notation for the eccentricity is not a whim: the measured insertions sit at e ≈
   * 5e-6, which {@code %f} would render as "0.000005".
   */
  @Test
  void format_rendersApsidesEccentricityAndInclination() {
    double a = RE + 500_000.0;
    double e = 0.01;
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            a,
            e,
            0.1,
            0.2,
            0.3,
            0.4,
            PositionAngleType.TRUE,
            FramesFactory.getGCRF(),
            AbsoluteDate.J2000_EPOCH,
            Constants.WGS84_EARTH_MU);

    assertEquals(
        "431219 x 568781 m (e=1.000e-02, i=5.7296 deg)", OrbitElements.osculating(orbit).format());
  }

  /**
   * The oracle of spec section 5.2, at the three altitudes already measured. Generates the
   * osculating series of a known orbit, then checks that the converter removes exactly the
   * oscillation the closed form predicts.
   */
  @Test
  void mean_removesTheShortPeriodJ2OscillationTheClosedFormPredicts() {
    for (double altitude : new double[] {400_000.0, 600_000.0, 1_000_000.0}) {
      double a = RE + altitude;
      double f = 1.5 * J2 * (RE / a) * (RE / a);
      double predictedSpan = 2.0 * a * f;

      UnnormalizedSphericalHarmonicsProvider provider =
          GravityFieldFactory.getUnnormalizedProvider(6, 0);
      KeplerianOrbit seedOrbit =
          new KeplerianOrbit(
              a,
              ECCENTRICITY,
              INCLINATION,
              0.0,
              0.0,
              0.0,
              PositionAngleType.MEAN,
              FramesFactory.getGCRF(),
              AbsoluteDate.J2000_EPOCH,
              provider.getMu());
      // OSCULATING, not MEAN: measured, MEAN yields a flat series (150 m of amplitude against
      // 19 493 m predicted), hence without the short-period motion the oracle has to see.
      EcksteinHechlerPropagator propagator =
          new EcksteinHechlerPropagator(seedOrbit, provider, PropagationType.OSCULATING);

      double period = 2.0 * FastMath.PI * FastMath.sqrt(a * a * a / provider.getMu());
      double oscMin = Double.MAX_VALUE;
      double oscMax = -Double.MAX_VALUE;
      double meanMin = Double.MAX_VALUE;
      double meanMax = -Double.MAX_VALUE;

      for (int i = 0; i < SAMPLES; i++) {
        SpacecraftState state =
            propagator.propagate(AbsoluteDate.J2000_EPOCH.shiftedBy(i * period / SAMPLES));
        KeplerianOrbit sampled = new KeplerianOrbit(state.getOrbit());

        double oscPerigee = OrbitElements.osculating(sampled).perigeeAltitude();
        oscMin = FastMath.min(oscMin, oscPerigee);
        oscMax = FastMath.max(oscMax, oscPerigee);

        Optional<OrbitElements> mean = OrbitElements.mean(sampled);
        assertTrue(mean.isPresent(), "mean orbit unavailable at sample " + i);
        double meanPerigee = mean.get().perigeeAltitude();
        meanMin = FastMath.min(meanMin, meanPerigee);
        meanMax = FastMath.max(meanMax, meanPerigee);
      }

      double measuredSpan = oscMax - oscMin;
      double meanSpan = meanMax - meanMin;
      logger.info(
          "[{} km] osculating perigee span={} m, closed form 2af={} m, ratio={} | mean span={} m",
          (int) (altitude / 1000),
          String.format(Locale.ROOT, "%.0f", measuredSpan),
          String.format(Locale.ROOT, "%.0f", predictedSpan),
          String.format(Locale.ROOT, "%.3f", measuredSpan / predictedSpan),
          String.format(Locale.ROOT, "%.1f", meanSpan));

      double ratio = measuredSpan / predictedSpan;
      assertTrue(
          FastMath.abs(ratio - 1.0) <= CLOSED_FORM_TOLERANCE,
          () ->
              String.format(
                  Locale.ROOT,
                  "osculating perigee span %.0f m disagrees with the closed form %.0f m"
                      + " (ratio %.3f, tolerance ±%.0f%%)",
                  measuredSpan,
                  predictedSpan,
                  ratio,
                  100.0 * CLOSED_FORM_TOLERANCE));

      assertTrue(
          meanSpan <= MEAN_RESIDUAL_CEILING_M,
          () ->
              String.format(
                  Locale.ROOT,
                  "mean perigee oscillates by %.1f m over one period, above the %.0f m residual"
                      + " ceiling — the converter may be returning osculating elements",
                  meanSpan,
                  MEAN_RESIDUAL_CEILING_M));
    }
  }

  /**
   * The degraded mode of spec section 3.4: an input the theory cannot handle yields an empty {@code
   * Optional}, never an exception. No mission must fail because a log could not be computed. A
   * hyperbolic orbit is the clearest such case: the theory means nothing on it.
   */
  @Test
  void mean_returnsEmptyRatherThanThrowing() {
    KeplerianOrbit hyperbolic =
        new KeplerianOrbit(
            -(RE + 500_000.0),
            1.5,
            INCLINATION,
            0.0,
            0.0,
            0.1,
            PositionAngleType.TRUE,
            FramesFactory.getGCRF(),
            AbsoluteDate.J2000_EPOCH,
            Constants.WGS84_EARTH_MU);

    assertTrue(OrbitElements.mean(hyperbolic).isEmpty());
  }
}
