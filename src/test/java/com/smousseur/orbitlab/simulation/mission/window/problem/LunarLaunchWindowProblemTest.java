package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * MIS-4 / L2 §5.1 — the closed half of the lunar launch window: geometry, no propagation,
 * milliseconds.
 *
 * <p><b>Every expectation is derived from the ephemeris, none is recorded.</b> The two
 * opportunities are not counted but checked against the closed form they come from — {@code ĥ · û_M
 * = sin i·cos δ·sin(Ω − α) + cos i·sin δ}, which vanishes at {@code sin(Ω − α) = −cot i · tan δ} —
 * the relief is measured against the worst instant of the same day, and the optimum is compared
 * with what {@code TranslunarInjectionPlan} costs on the plane it builds <em>for</em> the Moon at
 * the very same epoch. A test written on recorded outputs could not tell any of those from a
 * plausible bug.
 *
 * <p><b>The inclination these tests predict with is read off the plane, not off the site.</b> The
 * pad reaches a plane inclined at its <em>geocentric</em> latitude, a fraction of a degree under
 * the geodetic one the site is named by, and near the declination maximum that fraction is the
 * whole question — it is what decides whether the two roots still exist at all.
 *
 * <p><b>Nothing here confirms</b> (see {@link ScreeningOnly}): a flown perilune costs some four
 * seconds, and it belongs to {@code LunarLaunchWindowFlightTest}.
 */
class LunarLaunchWindowProblemTest {
  private static final Logger logger = LogManager.getLogger(LunarLaunchWindowProblemTest.class);

  /** Cape Canaveral, the site whose latitude Apollo chose to make the lunar plane reachable. */
  private static final double CANAVERAL_LATITUDE = 28.562;

  private static final double CANAVERAL_LONGITUDE = -80.577;
  private static final double CANAVERAL_ALTITUDE = 3.0;

  /** Kourou, whose latitude reaches the Moon 12.5% of a lunation (L0 §5). */
  private static final double KOUROU_LATITUDE = 5.236;

  private static final double KOUROU_LONGITUDE = -52.769;
  private static final double KOUROU_ALTITUDE = 14.0;

  /** The parking altitude the baseline was measured at (L0 §2). */
  private static final double PARKING_ALTITUDE = TranslunarInjectionPlan.PARKING_ALTITUDE;

  private static final double TARGET_PERILUNE = 100_000.0;

  /** Mass at injection (kg) — the figure L0 and L1 measured their tables at. */
  private static final double INJECTION_MASS = 1_700.0;

  private static final double SIDEREAL_DAY =
      2.0 * FastMath.PI / Constants.WGS84_EARTH_ANGULAR_VELOCITY;

  /**
   * The problem with its second tier switched off — the screening criterion alone.
   *
   * <p>Every test here is about the shape of that criterion, and confirming a candidate flies some
   * thirty four-day propagations. Left on, a single solver call would put this class in the tens of
   * seconds, and worse, it would compare a brute-force sweep of {@code evaluate} against an optimum
   * ranked on {@code confirm} — the two are six m/s apart at the optimum, which is enough to swap
   * two opportunities that sit 0.2 m/s from each other.
   */
  private static final class ScreeningOnly extends LunarLaunchWindowProblem {
    ScreeningOnly(double latitude, double longitude, double altitude) {
      super(
          latitude,
          longitude,
          altitude,
          PARKING_ALTITUDE,
          TARGET_PERILUNE,
          vehicle(),
          INJECTION_MASS);
    }

    @Override
    public LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
      return candidate;
    }
  }

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  private static Vehicle vehicle() {
    return new Spacecraft(500, 1200, 1200, PropulsionSystem.getSpacecraftPropulsion());
  }

  private static LunarLaunchWindowProblem canaveral() {
    return new ScreeningOnly(CANAVERAL_LATITUDE, CANAVERAL_LONGITUDE, CANAVERAL_ALTITUDE);
  }

  private static LunarLaunchWindowProblem kourou() {
    return new ScreeningOnly(KOUROU_LATITUDE, KOUROU_LONGITUDE, KOUROU_ALTITUDE);
  }

  /** The plane Canaveral reaches, built the way the problem builds its own. */
  private static LaunchSitePlane canaveralPlane() {
    return new LaunchSitePlane(
        CANAVERAL_LATITUDE, CANAVERAL_LONGITUDE, CANAVERAL_ALTITUDE, FastMath.PI / 2);
  }

  private static Vector3D moonDirection(AbsoluteDate date) {
    return OrekitService.get()
        .body(SolarSystemBody.MOON)
        .getPosition(date, OrekitService.get().gcrf())
        .normalize();
  }

  /** The declination the Moon has when a launch at {@code epoch} arrives, four days later. */
  private static double arrivalDeclination(AbsoluteDate epoch) {
    return FastMath.asin(
        moonDirection(epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS)).getZ());
  }

  /**
   * How far the plane reached at {@code epoch} is from containing the Moon at arrival, expressed as
   * the residual of the closed form: {@code sin(Ω − α) + cot i · tan δ}, which is zero exactly at
   * an opportunity.
   *
   * <p>Independent of {@link LunarLaunchWindowProblem.Injection#planeMisalignment()} in everything
   * but the arrival date it reads the Moon at: this one is written in nodes and declinations, that
   * one is a dot product of two vectors.
   */
  private static double closedFormResidual(LunarLaunchWindowProblem problem, AbsoluteDate epoch) {
    Vector3D normal = canaveralPlane().normalAt(epoch);
    double raan = FastMath.atan2(normal.getX(), -normal.getY());
    double inclination = FastMath.acos(normal.getZ());
    Vector3D moon = moonDirection(problem.injectionAt(epoch).arrivalDate());
    double rightAscension = FastMath.atan2(moon.getY(), moon.getX());
    double declination = FastMath.asin(moon.getZ());
    return FastMath.sin(raan - rightAscension)
        + FastMath.tan(declination) / FastMath.tan(inclination);
  }

  /** One brute-force sample of the criterion. */
  private record Sample(AbsoluteDate epoch, double deltaV, double misalignment) {}

  private static List<Sample> sweep(
      LunarLaunchWindowProblem problem, AbsoluteDate from, double spanSeconds, double stepSeconds) {
    List<Sample> samples = new ArrayList<>();
    for (double t = 0.0; t <= spanSeconds; t += stepSeconds) {
      AbsoluteDate epoch = from.shiftedBy(t);
      samples.add(
          new Sample(
              epoch,
              problem.evaluate(epoch).deltaV(),
              problem.injectionAt(epoch).planeMisalignment()));
    }
    return samples;
  }

  /** The strict local minima of a brute-force sweep, in chronological order. */
  private static List<Sample> localMinima(List<Sample> samples) {
    List<Sample> minima = new ArrayList<>();
    for (int i = 1; i < samples.size() - 1; i++) {
      if (samples.get(i).deltaV() < samples.get(i - 1).deltaV()
          && samples.get(i).deltaV() <= samples.get(i + 1).deltaV()) {
        minima.add(samples.get(i));
      }
    }
    return minima;
  }

  /**
   * The instants of a sweep where the misalignment changes sign — the opportunities themselves,
   * located by bisection on the sign rather than by looking for a minimum of the cost.
   *
   * <p><b>The distinction matters near the fusion of the two roots</b>: the cost keeps a local
   * minimum on the far side of the day, half a turn from any opportunity, and counting minima would
   * take it for one.
   */
  private static List<AbsoluteDate> opportunities(List<Sample> samples) {
    List<AbsoluteDate> zeros = new ArrayList<>();
    for (int i = 1; i < samples.size(); i++) {
      double before = samples.get(i - 1).misalignment();
      double after = samples.get(i).misalignment();
      if (before == 0.0 || (before < 0.0) != (after < 0.0)) {
        zeros.add(samples.get(i - 1).epoch());
      }
    }
    return zeros;
  }

  /**
   * Refines a sign change by bisection on the misalignment, to the second.
   *
   * @param problem the problem whose geometry is being read
   * @param from the last sample before the sign change
   * @param stepSeconds the sweep step the sign change was found at
   */
  private static AbsoluteDate refineOpportunity(
      LunarLaunchWindowProblem problem, AbsoluteDate from, double stepSeconds) {
    double low = 0.0;
    double high = stepSeconds;
    double atLow = problem.injectionAt(from).planeMisalignment();
    for (int i = 0; i < 40 && high - low > 1.0; i++) {
      double middle = 0.5 * (low + high);
      double atMiddle = problem.injectionAt(from.shiftedBy(middle)).planeMisalignment();
      if ((atLow < 0.0) == (atMiddle < 0.0)) {
        low = middle;
        atLow = atMiddle;
      } else {
        high = middle;
      }
    }
    return from.shiftedBy(0.5 * (low + high));
  }

  private static Sample cheapest(List<Sample> samples) {
    return samples.stream().min((a, b) -> Double.compare(a.deltaV(), b.deltaV())).orElseThrow();
  }

  private static String degrees(double radians) {
    return String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(radians));
  }

  private static String minutes(double seconds) {
    return String.format(Locale.ROOT, "%.1f", seconds / 60.0);
  }

  /**
   * An epoch whose arrival declination is under {@code maxDeclination}, searched forward from
   * {@code from} by whole days — so the "Moon near the equator" case is found in the ephemeris
   * rather than copied out of an almanac.
   */
  private static AbsoluteDate arrivalNearEquator(AbsoluteDate from, double maxDeclination) {
    for (int day = 0; day < 40; day++) {
      AbsoluteDate epoch = from.shiftedBy(day * 86_400.0);
      if (FastMath.abs(arrivalDeclination(epoch)) < maxDeclination) {
        return epoch;
      }
    }
    throw new IllegalStateException("no equatorial crossing within 40 days");
  }

  @Test
  @DisplayName("The day holds two opportunities, each where the closed form puts it")
  void theDayHoldsTwoOpportunities() {
    LunarLaunchWindowProblem problem = canaveral();
    AbsoluteDate start =
        arrivalNearEquator(
            new AbsoluteDate(2026, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC()),
            FastMath.toRadians(2.0));

    List<Sample> samples = sweep(problem, start, SIDEREAL_DAY, 300.0);
    List<AbsoluteDate> zeros =
        opportunities(samples).stream().map(z -> refineOpportunity(problem, z, 300.0)).toList();

    logger.info(
        "Moon near the equator ({}° at arrival from {}): {} opportunities over a sidereal day",
        degrees(arrivalDeclination(start)),
        start,
        zeros.size());
    zeros.forEach(
        z ->
            logger.info(
                "  opportunity at {} — {} m/s, β = {}°, closed-form residual {}",
                z,
                Math.round(problem.evaluate(z).deltaV()),
                degrees(problem.injectionAt(z).planeMisalignment()),
                String.format(Locale.ROOT, "%.2e", closedFormResidual(problem, z))));
    if (zeros.size() == 2) {
      logger.info(
          "separated by {} min, the plane's inclination being {}° against a {}° site",
          minutes(zeros.get(1).durationFrom(zeros.get(0))),
          degrees(FastMath.acos(canaveralPlane().normalAt(zeros.get(0)).getZ())),
          CANAVERAL_LATITUDE);
    }

    assertEquals(2, zeros.size(), "a plane containing a direction meets it twice per turn");
    for (AbsoluteDate zero : zeros) {
      assertEquals(
          0.0,
          closedFormResidual(problem, zero),
          1.0e-4,
          // The bisection stops at one second, and the node it brackets moves at ω⊕ = 7.3e-5 rad/s,
          // so a residual of that order is the resolution rather than a disagreement.
          "an opportunity is where sin(Ω − α) = −cot i · tan δ, and nowhere else");
    }
    // Both opportunities are real ones: half a turn apart, not the same crossing sampled twice.
    assertTrue(
        zeros.get(1).durationFrom(zeros.get(0)) > 0.25 * SIDEREAL_DAY,
        "with the Moon near the equator the two roots are half a turn apart");
  }

  @Test
  @DisplayName("The two opportunities merge when the declination reaches the inclination")
  void theTwoOpportunitiesMergeAtTheDeclinationMaximum() {
    LunarLaunchWindowProblem problem = canaveral();

    // The 2026 declination maximum, 28.415° (L0 §5), read four days earlier so the arrival falls
    // on it.
    AbsoluteDate start =
        new AbsoluteDate(2026, 2, 26, 0, 0, 0.0, TimeScalesFactory.getUTC())
            .shiftedBy(-TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);

    List<Sample> samples = sweep(problem, start, SIDEREAL_DAY, 300.0);
    List<AbsoluteDate> zeros =
        opportunities(samples).stream().map(z -> refineOpportunity(problem, z, 300.0)).toList();
    Sample closest =
        samples.stream()
            .min(
                (a, b) ->
                    Double.compare(FastMath.abs(a.misalignment()), FastMath.abs(b.misalignment())))
            .orElseThrow();
    double inclination = FastMath.acos(canaveralPlane().normalAt(closest.epoch()).getZ());

    logger.info(
        "Declination maximum: δ = {}° at arrival against a reached inclination of {}° — {}"
            + " opportunities, closest approach β = {}° at {} for {} m/s",
        degrees(arrivalDeclination(closest.epoch())),
        degrees(inclination),
        zeros.size(),
        degrees(closest.misalignment()),
        closest.epoch(),
        Math.round(closest.deltaV()));
    if (zeros.size() == 2) {
      double predicted =
          (FastMath.PI
                  - 2.0
                      * FastMath.abs(
                          FastMath.asin(
                              FastMath.tan(arrivalDeclination(closest.epoch()))
                                  / FastMath.tan(inclination))))
              / (2.0 * FastMath.PI)
              * SIDEREAL_DAY;
      logger.info(
          "the two roots are {} min apart, the closed form predicting {} min",
          minutes(zeros.get(1).durationFrom(zeros.get(0))),
          minutes(predicted));
    }

    assertTrue(
        FastMath.abs(closest.misalignment()) < FastMath.toRadians(0.5),
        "Canaveral still reaches the Moon at the 2026 maximum, to within half a degree, got "
            + degrees(closest.misalignment())
            + "°");
    assertTrue(
        zeros.size() <= 2 && zeros.size() != 1,
        "the roots are either both there or merged, never an odd number over a full turn");
    if (zeros.size() == 2) {
      assertTrue(
          zeros.get(1).durationFrom(zeros.get(0)) < 2.0 * 3_600.0,
          "at the declination maximum the two roots have all but merged");
    }

    List<LaunchWindow> hourly =
        new LaunchWindowSolver(problem)
            .solve(
                new LaunchWindowSearch(
                    start,
                    Duration.ofSeconds(Math.round(SIDEREAL_DAY)),
                    problem.coarseStep(),
                    problem.refinementPrecision(),
                    1.0e9,
                    50.0,
                    5));
    logger.info("the hourly sweep sees {} window(s)", hourly.size());
    assertEquals(
        1,
        hourly.size(),
        "an hourly sweep cannot resolve two opportunities that have merged, and must not invent a"
            + " second one out of the far-side minimum");
  }

  @Test
  @DisplayName("The far side of the day costs several times the optimum")
  void theFarSideOfTheDayCostsSeveralTimesTheOptimum() {
    LunarLaunchWindowProblem problem = canaveral();
    AbsoluteDate start = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
    List<Sample> samples = sweep(problem, start, SIDEREAL_DAY, 300.0);

    Sample best = cheapest(samples);
    Sample worst =
        samples.stream().max((a, b) -> Double.compare(a.deltaV(), b.deltaV())).orElseThrow();
    Sample mostMisaligned =
        samples.stream()
            .max(
                (a, b) ->
                    Double.compare(FastMath.abs(a.misalignment()), FastMath.abs(b.misalignment())))
            .orElseThrow();

    logger.info(
        "Canaveral over a sidereal day: best {} m/s at {} (β = {}°), worst {} m/s at {} (β = {}°)",
        Math.round(best.deltaV()),
        best.epoch(),
        degrees(best.misalignment()),
        Math.round(worst.deltaV()),
        worst.epoch(),
        degrees(worst.misalignment()));
    logger.info(
        "the largest misalignment of the day is {}° at {}, and costs {} m/s — the dearest instant"
            + " and the most misaligned one are not the same",
        degrees(mostMisaligned.misalignment()),
        mostMisaligned.epoch(),
        Math.round(mostMisaligned.deltaV()));

    assertTrue(
        samples.stream().allMatch(s -> Double.isFinite(s.deltaV())),
        "the criterion is finite at every instant of the day");
    assertTrue(
        worst.deltaV() > 1.5 * best.deltaV(),
        "the worst instant of the day must cost far more than the optimum, got "
            + Math.round(worst.deltaV())
            + " against "
            + Math.round(best.deltaV()));
  }

  @Test
  @DisplayName("A suffered plane that contains the Moon costs what the built plane costs")
  void theOptimumMeetsTheBaseline() {
    LunarLaunchWindowProblem problem = canaveral();
    AbsoluteDate start = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
    Sample best = cheapest(sweep(problem, start, SIDEREAL_DAY, 60.0));

    double onTheBuiltPlane =
        TranslunarInjectionPlan.keplerianInjectionDeltaV(best.epoch(), INJECTION_MASS);

    logger.info(
        "At the optimum {}: criterion {} m/s on the plane the pad reaches (β = {}°), against {} m/s"
            + " on the plane built for the Moon at the same epoch",
        best.epoch(),
        String.format(Locale.ROOT, "%.1f", best.deltaV()),
        degrees(best.misalignment()),
        String.format(Locale.ROOT, "%.1f", onTheBuiltPlane));

    assertEquals(
        onTheBuiltPlane,
        best.deltaV(),
        10.0,
        "a suffered plane that contains the Moon must cost what the built plane costs");
  }

  @Test
  @DisplayName("Kourou is priced, not refused")
  void kourouIsPricedRatherThanRefused() {
    LunarLaunchWindowProblem problem = kourou();
    double inclination = FastMath.toRadians(KOUROU_LATITUDE);

    AbsoluteDate inReach =
        arrivalNearEquator(
            new AbsoluteDate(2026, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC()), 0.5 * inclination);
    Sample bestInReach = cheapest(sweep(problem, inReach, SIDEREAL_DAY, 300.0));

    AbsoluteDate outOfReach = inReach.shiftedBy(7.0 * 86_400.0);
    List<Sample> outside = sweep(problem, outOfReach, SIDEREAL_DAY, 300.0);
    Sample bestOutOfReach = cheapest(outside);

    logger.info(
        "Kourou in reach (δ = {}° at arrival): {} m/s at β = {}°; out of reach (δ = {}°): {} m/s at"
            + " best, β = {}° — finite, and priced out of any budget",
        degrees(arrivalDeclination(inReach)),
        Math.round(bestInReach.deltaV()),
        degrees(bestInReach.misalignment()),
        degrees(arrivalDeclination(outOfReach)),
        Math.round(bestOutOfReach.deltaV()),
        degrees(bestOutOfReach.misalignment()));

    assertTrue(
        outside.stream().allMatch(s -> Double.isFinite(s.deltaV())),
        "out of reach is a price, not a refusal");
    assertTrue(
        bestOutOfReach.deltaV() > 1.5 * bestInReach.deltaV(),
        "the price out of reach must be worth refusing on a budget, got "
            + Math.round(bestOutOfReach.deltaV())
            + " against "
            + Math.round(bestInReach.deltaV()));
    assertTrue(
        FastMath.abs(bestInReach.misalignment()) < FastMath.toRadians(1.0),
        "in reach, the optimum is where the plane contains the Moon");
  }

  @Test
  @DisplayName("The solver lands on the minima a brute-force sweep finds")
  void theSolverFindsWhatBruteForceFinds() {
    LunarLaunchWindowProblem problem = canaveral();
    AbsoluteDate start = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
    double span = 26.0 * 3_600.0;

    List<Sample> samples = sweep(problem, start, span, 60.0);
    Sample best = cheapest(samples);
    List<Sample> minima = localMinima(samples);

    double largestJump = 0.0;
    AbsoluteDate atJump = start;
    for (int i = 1; i < samples.size(); i++) {
      double jump = FastMath.abs(samples.get(i).deltaV() - samples.get(i - 1).deltaV());
      if (jump > largestJump) {
        largestJump = jump;
        atJump = samples.get(i).epoch();
      }
    }

    List<LaunchWindow> windows =
        new LaunchWindowSolver(problem)
            .solve(
                new LaunchWindowSearch(
                    start,
                    Duration.ofSeconds(Math.round(span)),
                    problem.coarseStep(),
                    problem.refinementPrecision(),
                    1.0e9,
                    50.0,
                    5));
    LaunchWindow cheapestWindow =
        windows.stream()
            .min((a, b) -> Double.compare(a.best().deltaV(), b.best().deltaV()))
            .orElseThrow();

    logger.info(
        "Brute force at 60 s over 26 h ({} samples): {} local minima, cheapest {} m/s at {}; the"
            + " solver returns {} window(s), cheapest {} m/s at {}",
        samples.size(),
        minima.size(),
        String.format(Locale.ROOT, "%.1f", best.deltaV()),
        best.epoch(),
        windows.size(),
        String.format(Locale.ROOT, "%.1f", cheapestWindow.best().deltaV()),
        cheapestWindow.date());
    logger.info(
        "largest jump between two consecutive samples: {} m/s at {}, {} min from the cheapest"
            + " minimum — the first-passage discontinuity of §2.5, measured",
        String.format(Locale.ROOT, "%.1f", largestJump),
        atJump,
        minutes(FastMath.abs(atJump.durationFrom(best.epoch()))));

    assertTrue(windows.size() >= 2, "twenty-six hours hold both of the day's opportunities");
    for (LaunchWindow window : windows) {
      double distance =
          minima.stream()
              .mapToDouble(m -> FastMath.abs(window.date().durationFrom(m.epoch())))
              .min()
              .orElse(Double.POSITIVE_INFINITY);
      assertTrue(
          distance <= 60.0,
          "the golden-section refinement must land on a brute-force minimum, "
              + window.date()
              + " is "
              + minutes(distance)
              + " min from the nearest");
    }
    assertTrue(
        cheapestWindow.best().deltaV() <= best.deltaV() + 1.0,
        "refining to the second cannot be dearer than sampling at the minute, got "
            + cheapestWindow.best().deltaV()
            + " against "
            + best.deltaV());
  }

  @Test
  @DisplayName("The three scales are the ones the criterion needs")
  void theThreeScalesAreDeclared() {
    LunarLaunchWindowProblem problem = canaveral();

    assertEquals(Duration.ofHours(1), problem.coarseStep(), "an hour brackets a smooth minimum");
    assertEquals(
        Duration.ofSeconds(1),
        problem.refinementPrecision(),
        "a launch instant is decided to the second, not to a tenth of the sweep step");
    assertEquals(
        0.5 * SIDEREAL_DAY,
        problem.recurrence().toNanos() / 1.0e9,
        1.0,
        "two opportunities per sidereal day, so the recurrence is half of one");
  }
}
