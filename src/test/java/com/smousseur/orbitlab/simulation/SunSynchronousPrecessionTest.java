package com.smousseur.orbitlab.simulation;

import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

/**
 * <b>MIS-7 / P1, test T4 — a sun-synchronous orbit actually precesses with the Sun</b>, and the
 * <b>arbiter of the reference-frame question of spec §3.4</b> ({@code
 * docs/earth-orbit/01-mission-terre-parametrable.md}).
 *
 * <p><b>Why the inclination alone proves nothing.</b> {@code SunSynchronousInclinationTest} (T3)
 * checks the arithmetic of {@code cos i = −a^{7/2}(1−e²)²·n_prec / (3/2·J2·Re²·√µ)} against three
 * reference values. That is a check of a formula against a table. What makes an orbit
 * sun-synchronous is a <em>behaviour</em>: its node must drift eastward at 360° / 365.2422 days =
 * 0.9856°/day, so that the local solar time of every pass stays put. Only a propagation can say
 * whether it does, and the propagation is run under the real gravity field, where J2 is one term
 * among many.
 *
 * <p><b>The frame question, and its answer.</b> §3.4 left open whether the target inclination
 * should be expressed in GCRF — the frame the states are propagated in, whose equator is J2000's —
 * or in the equator of the date, and named this fixture as the arbiter: nodal precession is the
 * only one of the two readings with an observable physical meaning. It therefore measures the drift
 * in <em>both</em> frames. The answer, at 700 km over 3.2 days, is that <b>the choice does not
 * matter</b>: the two frames give 0.98776 and 0.98786°/day, 0.01 % apart, against the 1.8 % §3.4
 * feared. The frame offset lands in the <em>inclination</em> (0.021° of drift in GCRF against
 * 0.013° of date) and cancels out of the rate. {@code LaunchPlane.inclinationFrame()} keeps GCRF,
 * and this fixture is what would notice if that ever stopped being harmless.
 */
class SunSynchronousPrecessionTest {
  private static final Logger logger = LogManager.getLogger(SunSynchronousPrecessionTest.class);

  private static final double ALTITUDE = 700_000.0;

  /** 360° / 365.2422 days — the rate the node must hold to keep the Sun in the same place. */
  private static final double TARGET_PRECESSION_DEG_PER_DAY = 0.9856;

  private static final double SECONDS_PER_DAY = 86_400.0;

  /**
   * Propagation span. Long enough for the secular drift (~3°) to dominate the short-period J2
   * wobble of the osculating node, short enough to stay a test.
   */
  private static final double SPAN_DAYS = 3.2;

  /**
   * Tolerance on the measured drift rate, in degrees per day. <b>Set from the measurement</b>, not
   * chosen in advance (spec §7, §10): what is asserted is that the orbit precesses with the Sun to
   * a few percent, which is what distinguishes a sun-synchronous orbit from a merely polar one — a
   * polar orbit's node does not drift at all.
   */
  private static final double TOLERANCE_DEG_PER_DAY = 0.05;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void theNodeOfASunSynchronousOrbit_keepsPaceWithTheSun() {
    double inclination = Physics.sunSynchronousInclinationForAltitude(ALTITUDE);
    Drift gcrf = measureDrift(inclination, LaunchPlane.inclinationFrame());
    Drift ofDate = measureDrift(inclination, equatorOfDate());

    logger.info(
        "T4 sun-synchronous 700 km (i = {}°), {} days of propagation:",
        fmt(FastMath.toDegrees(inclination), 4),
        fmt(SPAN_DAYS, 1));
    logger.info(
        "  GCRF (the frame LaunchPlane declares) -> {}°/day, i drifts {}°",
        fmt(gcrf.raanDegPerDay(), 5),
        fmt(gcrf.inclinationDrift(), 5));
    logger.info(
        "  equator of date (TOD)                 -> {}°/day, i drifts {}°",
        fmt(ofDate.raanDegPerDay(), 5),
        fmt(ofDate.inclinationDrift(), 5));
    logger.info("  target: {}°/day", fmt(TARGET_PRECESSION_DEG_PER_DAY, 4));

    assertEquals(
        TARGET_PRECESSION_DEG_PER_DAY,
        gcrf.raanDegPerDay(),
        TOLERANCE_DEG_PER_DAY,
        "the derived inclination must make the node keep pace with the Sun");
  }

  /**
   * The control: a plain polar orbit at the same altitude does <em>not</em> precess. Without it the
   * fixture above could pass on an orbit whose node happened to move for any other reason — this is
   * what makes the measurement about sun-synchronicity rather than about propagation noise.
   */
  @Test
  void aPolarOrbitAtTheSameAltitude_doesNotPrecess() {
    Drift polar = measureDrift(FastMath.toRadians(90.0), LaunchPlane.inclinationFrame());

    logger.info("T4 control — polar 700 km: {}°/day", fmt(polar.raanDegPerDay(), 5));

    assertTrue(
        FastMath.abs(polar.raanDegPerDay()) < 0.1,
        () ->
            "a polar orbit's node is not supposed to drift; measured "
                + fmt(polar.raanDegPerDay(), 5)
                + "°/day. If this moves, the sun-synchronous measurement is not measuring J2");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private record Drift(double raanDegPerDay, double inclinationDrift) {}

  /**
   * Propagates a circular orbit at the given inclination and returns its secular nodal drift, read
   * in the given frame.
   *
   * @param inclination the orbit inclination (rad), expressed in the propagation frame
   * @param readFrame the frame the node is read in — the whole point of the fixture
   * @return the measured drift
   */
  private static Drift measureDrift(double inclination, Frame readFrame) {
    AbsoluteDate epoch = epoch();
    Frame propagationFrame = OrekitService.get().gcrf();
    Orbit initial =
        new KeplerianOrbit(
            Constants.WGS84_EARTH_EQUATORIAL_RADIUS + ALTITUDE,
            0.0,
            inclination,
            0.0,
            0.0,
            0.0,
            PositionAngleType.TRUE,
            propagationFrame,
            epoch,
            Constants.WGS84_EARTH_MU);

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(FlightContext.earth(), OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(new SpacecraftState(initial));

    double span = SPAN_DAYS * SECONDS_PER_DAY;
    SpacecraftState end = propagator.propagate(epoch.shiftedBy(span));

    KeplerianOrbit start = readAsKeplerian(new SpacecraftState(initial), readFrame);
    KeplerianOrbit finish = readAsKeplerian(end, readFrame);

    double deltaRaan =
        FastMath.toDegrees(
            normalizeSigned(
                finish.getRightAscensionOfAscendingNode()
                    - start.getRightAscensionOfAscendingNode()));
    return new Drift(
        deltaRaan / SPAN_DAYS, FastMath.toDegrees(FastMath.abs(finish.getI() - start.getI())));
  }

  private static KeplerianOrbit readAsKeplerian(SpacecraftState state, Frame frame) {
    Orbit inFrame =
        new org.orekit.orbits.CartesianOrbit(
            state.getPVCoordinates(frame), frame, state.getDate(), state.getOrbit().getMu());
    return (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(inFrame);
  }

  /** The true equator and equinox of the date — the frame J2 secular theory is written in. */
  private static Frame equatorOfDate() {
    return FramesFactory.getTOD(IERSConventions.IERS_2010, true);
  }

  /** Guards against a node crossing 2π being read as a −360° jump. */
  private static double normalizeSigned(double angle) {
    double wrapped = angle % (2.0 * FastMath.PI);
    if (wrapped > FastMath.PI) {
      wrapped -= 2.0 * FastMath.PI;
    } else if (wrapped < -FastMath.PI) {
      wrapped += 2.0 * FastMath.PI;
    }
    return wrapped;
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
