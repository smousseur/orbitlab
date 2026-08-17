package com.smousseur.orbitlab.simulation.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * <b>PHY-4 / L2 — the third body does the physics it is supposed to do</b> (spec {@code
 * docs/multi-corps/04-conception-L2.md} §5.2).
 *
 * <p>An equatorial geostationary orbit left alone by the Moon and the Sun keeps its plane. Perturbed
 * by both, its inclination grows at a rate quoted across the domain at about <b>0.85 °/year</b> —
 * the reason every operational GEO satellite spends most of its propellant on north-south
 * station-keeping. That is the behaviour this fixture measures, on the very factory the twenty
 * production construction sites call.
 *
 * <p><b>Four propagations, where the découpage asked for two.</b> The obvious fixture — one run with
 * the third bodies, one control without — has a blind spot: a half-wired force list, with only one
 * of the two bodies mounted or the same one mounted twice, produces a rate that still lands inside a
 * ±20 % tolerance on the total. Splitting the contributions makes that failure mode visible for the
 * price of one more propagation. Only the combined case is asserted; the two single-body runs are
 * logged, and their near-2:1 ratio is what a reader should check when this fixture ever goes red.
 *
 * <p><b>Why the tolerance is wide, and must stay wide.</b> The 0.85 °/year is a <em>mean</em>. The
 * lunar contribution depends on the orientation of the Moon's node, which regresses over 18.6 years,
 * so the instantaneous rate depends on the epoch. That is not an argument from the literature here —
 * it was measured, by re-running the combined case from successive epochs at a fixed 180-day span:
 *
 * <pre>
 *   2026 → 0.9570    2028 → 0.9050    2032 → 0.7834
 *   2027 → 0.9279    2030 → 0.8583    2035 → 0.7574   (°/year)
 * </pre>
 *
 * <p>The rate sweeps the whole 0.75–0.95 band within a decade, and 0.85 is its middle. The ±20 % is
 * therefore the physical width of the target, not caution — a later lot that tightened this test
 * would change its nature, not merely its tolerance. This fixture's own epoch, 2026, sits near a
 * maximum of that cycle, which is why the measured value lands in the upper half of the band.
 *
 * <p><b>Why the span is long, measured rather than argued.</b> Over a short span the fortnightly
 * lunar term is the same order as the accumulated secular drift, so the measured rate reports the
 * start date rather than the physics (spec §1.1-B). Sweeping the span at a fixed epoch shows exactly
 * where that stops:
 *
 * <pre>
 *    30 d → 1.2178     90 d → 0.9209    365 d → 0.9487
 *    60 d → 1.0637    180 d → 0.9570    730 d → 0.9418   (°/year)
 * </pre>
 *
 * <p>The plateau starts around 180 days and holds to within 2 % out to two years. Note what the
 * first line means: the découpage's own suggestion of "a few tens of days" would have measured
 * 1.22 °/year and failed the ±20 % tolerance it proposed in the same sentence.
 */
class GeoInclinationDriftTest {
  private static final Logger logger = LogManager.getLogger(GeoInclinationDriftTest.class);

  /** Geostationary radius (m). */
  private static final double GEO_RADIUS = 42_164_000.0;

  private static final double SECONDS_PER_DAY = 86_400.0;
  private static final double DAYS_PER_YEAR = 365.25;

  /**
   * Propagation span, in days — the first point of the plateau in the sweep above. Long enough that
   * the fortnightly lunar term is a correction rather than a competitor, and short enough that
   * reading the rate as a straight {@code Δi / Δt} stays honest: the inclination vector precesses
   * about the Laplace plane over roughly half a century, so half a year is a small fraction of a
   * cycle. The four propagations together take about 33 s, which is why this fixture stays in the
   * default suite rather than behind {@code orbitlab.slowTests}.
   */
  private static final double SPAN_DAYS = 180.0;

  /** The domain value the combined luni-solar drift must reproduce, in degrees per year. */
  private static final double TARGET_DEG_PER_YEAR = 0.85;

  /** ±20 % of the target — the physical width of the target, see the class javadoc. */
  private static final double TOLERANCE_DEG_PER_YEAR = 0.17;

  /**
   * The control must stay below this, in degrees per year. The unperturbed orbit is not exactly
   * frozen: it starts equatorial in GCRF, whose equator is J2000's, so it sits a fraction of a
   * degree off the equator of date and the non-spherical field moves it a little. Measured at
   * 0.0125 °/year — fifty times smaller than the perturbed drift, which is the point.
   */
  private static final double CONTROL_CEILING_DEG_PER_YEAR = 0.05;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aGeoPerturbedByTheMoonAndTheSun_seesItsInclinationGrow() {
    Drift control = measure("no perturber", GravitationalContext.earth());
    Drift moon = measure("Moon only", GravitationalContext.earth().withPerturbers(MOON));
    Drift sun = measure("Sun only", GravitationalContext.earth().withPerturbers(SUN));
    Drift both = measure("Moon + Sun", GravitationalContext.earth().withPerturbers(MOON, SUN));

    logger.info("L2 GEO inclination drift over {} days:", fmt(SPAN_DAYS, 0));
    log(control);
    log(moon);
    log(sun);
    log(both);
    logger.info("  target: {} +/- {} deg/year", fmt(TARGET_DEG_PER_YEAR, 2), fmt(TOLERANCE_DEG_PER_YEAR, 2));

    assertTrue(
        control.degPerYear() < CONTROL_CEILING_DEG_PER_YEAR,
        () ->
            "without a third body the plane must hold; measured "
                + fmt(control.degPerYear(), 4)
                + " deg/year. If this moves, the perturbed measurement is not measuring the Moon");

    assertTrue(
        moon.degPerYear() > sun.degPerYear(),
        () ->
            "the Moon must dominate the Sun (roughly 2:1); measured "
                + fmt(moon.degPerYear(), 4)
                + " against "
                + fmt(sun.degPerYear(), 4)
                + " deg/year. A single body mounted twice would show them equal");

    assertEquals(
        TARGET_DEG_PER_YEAR,
        both.degPerYear(),
        TOLERANCE_DEG_PER_YEAR,
        "the combined luni-solar attraction must tilt the geostationary plane at the known rate");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private static final SolarSystemBody MOON = SolarSystemBody.MOON;
  private static final SolarSystemBody SUN = SolarSystemBody.SUN;

  private record Drift(String label, double degPerYear, long millis) {}

  /**
   * Propagates an equatorial circular orbit at geostationary radius for {@link #SPAN_DAYS} and
   * returns the inclination it has picked up, expressed as a yearly rate.
   *
   * <p>The inclination is read in GCRF, the frame the states are propagated in, and the orbit starts
   * at exactly zero there — so the reading is the drift itself, with no baseline to subtract.
   */
  private static Drift measure(String label, GravitationalContext context) {
    AbsoluteDate epoch = epoch();
    Orbit initial =
        new KeplerianOrbit(
            GEO_RADIUS,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            PositionAngleType.TRUE,
            OrekitService.get().gcrf(),
            epoch,
            context.mu());

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(new SpacecraftState(initial));

    long start = System.nanoTime();
    SpacecraftState end = propagator.propagate(epoch.shiftedBy(SPAN_DAYS * SECONDS_PER_DAY));
    long millis = (System.nanoTime() - start) / 1_000_000L;

    KeplerianOrbit finish = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(end.getOrbit());
    double drift = FastMath.toDegrees(FastMath.abs(finish.getI()));
    return new Drift(label, drift * DAYS_PER_YEAR / SPAN_DAYS, millis);
  }

  private static void log(Drift drift) {
    logger.info(
        "  {} -> {} deg/year ({} ms)",
        String.format(Locale.ROOT, "%-12s", drift.label()),
        fmt(drift.degPerYear(), 4),
        drift.millis());
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
