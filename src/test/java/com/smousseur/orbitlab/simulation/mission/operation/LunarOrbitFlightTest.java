package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.gravity.SoiCrossingDetector;
import com.smousseur.orbitlab.simulation.gravity.SphereOfInfluence;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionLoadEvaluator;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.ObjectiveEvaluator;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowPlanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * MIS-5 / L5 §9.1 — the flight of the lot, and the one thing it exists to make true: <b>the mission
 * flies from the ground to a circular lunar orbit</b>. Ascent, parking, injection, upper-stage
 * jettison, a translunar coast that ends at the sphere of influence, a selenocentric approach, the
 * insertion burn and twelve revolutions of the orbit it captured into — all through {@code
 * MissionOptimizer} and the ephemeris generator, exactly as the application's compute path runs
 * them.
 *
 * <p><b>One flight, on the budget-sized configuration, and that is a decision</b> (§9.1). MIS-4
 * flies two because L4 needed a fully loaded impulsive reference for L6; no lot needs one here, and
 * since L3 the fully loaded orbiter is not a configuration the product offers — it would carry 800
 * kg where the budget sizes ~658, changing the mass at injection, the injection and therefore the
 * arrival, without exercising a single mechanism this flight does not. MIS-4's own non-regression
 * stays with {@code LunarFlybyFlightTest}, untouched.
 *
 * <p><b>Three assertions and five measurements.</b> The assertions are the lot's property: the arcs
 * flown, the flight being whole, and the product's own objective predicate. The measurements are
 * <b>logged and not pinned</b>, on MIS-4 / L4 §8.3's rule — pinning them would freeze in this lot
 * numbers it exists to find out. Among them is what L4 §7 pt 6 bequeaths here: nothing until now
 * was measured on a <em>real</em> arrival, every figure of that lot coming from a fabricated
 * hyperbola.
 *
 * <p><b>Contrainte de méthode</b> (découpage §3): a full CMA-ES ascent plus five days of
 * propagation on both passes, and it is the user who runs it.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class LunarOrbitFlightTest {
  private static final Logger logger = LogManager.getLogger(LunarOrbitFlightTest.class);

  private static final double CANAVERAL_LATITUDE = 28.562;
  private static final double CANAVERAL_LONGITUDE = -80.577;
  private static final double CANAVERAL_ALTITUDE = 3.0;

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double ORBIT_ALTITUDE = 100_000.0;

  /** The terminal coast's name, which is what the objective predicate selects its samples by. */
  private static final String FINAL_COAST_NAME = "Coasting";

  /** Dry mass of the orbiter, as the wizard will pre-fill it. */
  private static final double ORBITER_DRY_MASS = 2_000.0;

  /** The budget and the seed of every mission optimization test of the repository. */
  private static final int MAX_EVALUATIONS = 40_000;

  private static final long TEST_SEED = 42L;

  /**
   * How many window openings are tried before giving up.
   *
   * <p>The window's {@code confirm()} flies the aim from the injection state a pad <em>would</em>
   * reach; the chain arrives with the one its ascent really delivered, and the two differ (MIS-4 /
   * L6 §9). A confirmed date can therefore still be unplannable by the chain, so a refusal advances
   * the floor past it and asks the planner again rather than failing the lot on a date.
   */
  private static final int WINDOW_ATTEMPTS = 4;

  /** Due east: this chain flies {@code i = φ}, where the two azimuth branches merge. */
  private static final double DUE_EAST = FastMath.PI / 2;

  private static AbsoluteDate searchStart;

  @BeforeAll
  static void init() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    searchStart = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("A lunar orbit mission flies from the pad into a circular orbit around the Moon")
  void theMissionFliesFromTheGroundIntoLunarOrbit() {
    LaunchConfiguration configuration = sizedConfiguration();

    MissionComputeResult result = null;
    Mission mission = null;
    AbsoluteDate launchDate = null;
    double wallSeconds = 0.0;
    AbsoluteDate floor = searchStart;
    OrbitlabException lastRefusal = null;

    for (int attempt = 0; attempt < WINDOW_ATTEMPTS && result == null; attempt++) {
      MissionSpec.LunarOrbit spec = spec(configuration);
      Optional<LaunchWindow> window = LunarLaunchWindowPlanner.nextOpportunity(spec, floor);
      assertTrue(window.isPresent(), "the lunar window offered no opening at or after " + floor);

      launchDate = window.get().date();
      logger.info(
          "Launch window: {} at {} m/s (attempt {}/{})",
          launchDate,
          FastMath.round(window.get().best().deltaV()),
          attempt + 1,
          WINDOW_ATTEMPTS);

      mission = MissionComposer.compose(spec, OptimizationType.FAST);
      mission.setCurrentState(mission.getInitialState(launchDate));

      long startedAt = System.nanoTime();
      try {
        result = new MissionOptimizer(mission, MAX_EVALUATIONS, TEST_SEED).optimize();
      } catch (OrbitlabException refused) {
        lastRefusal = refused;
        logger.info(
            "Epoch {} cannot be planned by the chain: {}", launchDate, refused.getMessage());
        floor = launchDate.shiftedBy(3_600.0);
      } finally {
        wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;
      }
    }
    assertNotNull(
        result,
        "no epoch this window offered could be planned by the chain; last refusal: "
            + (lastRefusal == null ? "none" : lastRefusal.getMessage()));

    MissionEphemeris ephemeris = result.ephemeris();
    assertNotNull(ephemeris, "the flight produced no ephemeris");

    // ── MEASURE 1: the point count, against the ~10 500 §7 predicts ──────────
    logger.info(
        "MEASURE 1 — lunar orbit flight: {} points in {} s of wall time, loads {}",
        ephemeris.allPoints().size(),
        String.format(Locale.ROOT, "%.1f", wallSeconds),
        Arrays.toString(configuration.propellantLoads()));

    // ── the arcs: out, and stay ──────────────────────────────────────────────
    List<SolarSystemBody> arcBodies = new ArrayList<>();
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (arcBodies.isEmpty() || arcBodies.getLast() != point.arc().body()) {
        arcBodies.add(point.arc().body());
      }
    }
    logger.info("Arcs flown, in order: {}", arcBodies);
    assertEquals(
        List.of(SolarSystemBody.EARTH, SolarSystemBody.MOON),
        arcBodies,
        "a captured mission flies two arcs and does not come back out");

    // ── the flight is whole ──────────────────────────────────────────────────
    assertTrue(ephemeris.isComplete(), "a stage threw during the flight");

    // ── the product's own verdict ────────────────────────────────────────────
    assertTrue(
        ObjectiveEvaluator.met(
            ephemeris,
            mission.getObjective(),
            MissionLoadEvaluator.DEFAULT_OBJECTIVE_TOLERANCE_RATIO),
        "the flown orbit must satisfy the mission's own insertion objective");

    logAchievedOrbit(ephemeris);
    logDistanceToTheSphere(ephemeris);
  }

  /**
   * MEASURES 2 and 3 — the orbit the mission ended in, read on the terminal coast's lunar samples,
   * which is exactly the set {@code MissionLoadEvaluator.objectiveMet} scores.
   *
   * <p><b>The inclination is reported with its frame named</b>, and that is not decoration: L0
   * measure 1 found a near-constant 21° between the selenocentric ICRF-oriented reading — the one
   * {@code OrbitElements} and this log produce — and the reading above the lunar equator that a
   * mission document would use. Unlabelled, the screen would say 150° for an orbit called 171°.
   */
  private static void logAchievedOrbit(MissionEphemeris ephemeris) {
    double minAltitude = Double.POSITIVE_INFINITY;
    double maxAltitude = Double.NEGATIVE_INFINITY;
    MissionEphemerisPoint last = null;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (FINAL_COAST_NAME.equals(point.stageName())
          && point.arc().body() == SolarSystemBody.MOON) {
        minAltitude = FastMath.min(minAltitude, point.altitudeMeters());
        maxAltitude = FastMath.max(maxAltitude, point.altitudeMeters());
        last = point;
      }
    }
    if (last == null) {
      logger.warn("The terminal coast produced no lunar sample to read the orbit from");
      return;
    }

    KeplerianOrbit orbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            last.arc().frame(),
            last.time(),
            GravitationalContext.moon().mu());
    logger.info(
        "MEASURE 2 — achieved lunar orbit: {} x {} km over the terminal coast (aimed {} km),"
            + " e = {}, period {} s",
        String.format(Locale.ROOT, "%.3f", minAltitude / 1000.0),
        String.format(Locale.ROOT, "%.3f", maxAltitude / 1000.0),
        ORBIT_ALTITUDE / 1000.0,
        String.format(Locale.ROOT, "%.2e", orbit.getE()),
        FastMath.round(orbit.getKeplerianPeriod()));
    logger.info(
        "MEASURE 3 — inclination undergone: {}° in the SELENOCENTRIC ICRF-oriented frame — add"
            + " ~21° to read it above the lunar equator (L0 measure 1)",
        String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(orbit.getI())));
  }

  /**
   * MEASURE 4 — how close the captured orbit ever comes to the sphere of influence, which is the
   * verifiable half of §8's verdict on ε.
   *
   * <p>The dead band scales the radius only for a detector armed <em>from</em> the lunar context,
   * and this chain arms none there. Even if it did, this ratio says whether there would have been
   * anything to measure.
   */
  private static void logDistanceToTheSphere(MissionEphemeris ephemeris) {
    SphereOfInfluence sphere = SphereOfInfluence.of(SolarSystemBody.MOON);
    double closestRatio = 0.0;
    double radiusAtClosest = Double.NaN;
    double sphereAtClosest = Double.NaN;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (FINAL_COAST_NAME.equals(point.stageName())
          && point.arc().body() == SolarSystemBody.MOON) {
        double sphereRadius = sphere.radiusAt(point.time());
        double ratio = point.position().getNorm() / sphereRadius;
        if (ratio > closestRatio) {
          closestRatio = ratio;
          radiusAtClosest = point.position().getNorm();
          sphereAtClosest = sphereRadius;
        }
      }
    }
    logger.info(
        "MEASURE 4 - closest the captured orbit comes to the lunar sphere: r/R = {} ({} km of a"
            + " {} km sphere, whose dead band is {} km wide); a capture never approaches the"
            + " boundary, which is why L5 closes the epsilon debt with a verdict rather than a"
            + " calibration",
        String.format(Locale.ROOT, "%.5f", closestRatio),
        FastMath.round(radiusAtClosest / 1000.0),
        FastMath.round(sphereAtClosest / 1000.0),
        String.format(
            Locale.ROOT, "%.1f", sphereAtClosest * SoiCrossingDetector.EXIT_DEAD_BAND / 1000.0));
  }

  private static MissionSpec.LunarOrbit spec(LaunchConfiguration configuration) {
    return new MissionSpec.LunarOrbit(
        "Lunar orbit",
        configuration,
        PARKING_ALTITUDE,
        ORBIT_ALTITUDE,
        "Cape Canaveral",
        CANAVERAL_LATITUDE,
        CANAVERAL_LONGITUDE,
        CANAVERAL_ALTITUDE,
        null,
        null);
  }

  /**
   * The chain as the wizard will build it: the catalogue orbiter loaded for its own insertion, and
   * launcher loads sized top-down from it by {@code PropellantBudget.loadsForLunarOrbit} (MIS-5 /
   * L3 §4). This is the sizing whose 10 % margin L4 saw consumed at 3.7 % on a fabricated
   * hyperbola; here it meets a real arrival.
   */
  private static LaunchConfiguration sizedConfiguration() {
    PropellantBudget.LunarOrbitLoads loads =
        PropellantBudget.loadsForLunarOrbit(
            Launchers.FALCON_HEAVY,
            Payloads.LUNAR_ORBITER,
            ORBITER_DRY_MASS,
            PARKING_ALTITUDE,
            ORBIT_ALTITUDE,
            CANAVERAL_LATITUDE,
            DUE_EAST);
    Spacecraft orbiter =
        Payloads.LUNAR_ORBITER.toSpacecraft(ORBITER_DRY_MASS, loads.insertionLoad());
    logger.info(
        "Budget sizing: launcher loads {}, insertion load {} kg, mass at injection {} kg",
        Arrays.toString(loads.launcherLoads()),
        FastMath.round(loads.insertionLoad()),
        FastMath.round(loads.massAtInjection()));
    return new LaunchConfiguration(
        Launchers.FALCON_HEAVY, loads.launcherLoads(), orbiter, Payloads.LUNAR_ORBITER.id());
  }
}
