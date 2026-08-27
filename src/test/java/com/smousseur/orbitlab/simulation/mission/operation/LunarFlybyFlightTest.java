package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.ObjectiveEvaluator;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowProblem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * MIS-4 / L4 §8.3 — the test of the lot, and the one thing it exists to make true: <b>the mission
 * flies from the ground to the flyby</b>. Ascent, parking insertion, coast to the injection point,
 * translunar injection, and a lunar arc, all through {@code MissionOptimizer} and the ephemeris
 * generator exactly as the application's compute path runs them.
 *
 * <p><b>The ascent is optimized for real</b>, at the 40 000-evaluation budget and seed 42 of {@code
 * AbstractTrajectoryOptimizerTest}. {@code LunarTransferFlightTest} flies at {@code MAX_EVALUATIONS
 * = 1} because none of its stages is optimizable; this chain has one, and the lot closes on "a
 * flight from the ground to the flyby". Replaying a pinned ascent vector would be fast and
 * deterministic and would freeze a number in the very lot that flies this chain for the first time,
 * before anyone knows what it should be — and the ascent references have already had to be
 * re-recorded once, after MIS-7.
 *
 * <p><b>Three measurements ride on this flight and nothing else can take them</b> (§8.3): the
 * perilune actually reached, before the ±10 km band of §4.1 is fixed; the two biases L2 chiffered
 * without flying — 68 s of out-of-model ascent and 115 s of nodal regression — read as the gap
 * between the β planned at the window date and the β real at injection; and the half-degree of J2
 * regression L1 §5 pt 1 computed without flying. They are <b>logged, not asserted</b>: pinning them
 * would pin numbers this lot exists to find out.
 *
 * <p><b>Contrainte de méthode</b> (découpage §3): this flight costs a full CMA-ES ascent plus seven
 * days of propagation, and it is the user who runs it.
 */
class LunarFlybyFlightTest {
  private static final Logger logger = LogManager.getLogger(LunarFlybyFlightTest.class);

  private static final double CANAVERAL_LATITUDE = 28.562;
  private static final double CANAVERAL_LONGITUDE = -80.577;
  private static final double CANAVERAL_ALTITUDE = 3.0;

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double PERILUNE_ALTITUDE = 100_000.0;

  /** The budget and the seed of every mission optimization test of the repository. */
  private static final int MAX_EVALUATIONS = 40_000;

  private static final long TEST_SEED = 42L;

  /**
   * Mass at injection handed to the <b>window</b> (kg), and to the window alone. The date the
   * criterion picks is set by the encounter geometry, not by the vehicle: the mass enters only the
   * depletion-floor verdict of {@code confirm}. This is the figure L0 and L1 built their tables on,
   * so the window is solved exactly as {@code LunarLaunchWindowFlightTest} solves it — and the
   * flight below then flies a Falcon Heavy from the date it returned, which is the whole point of
   * measuring the bias between the two.
   */
  private static final double WINDOW_INJECTION_MASS = 1_700.0;

  /** The budget the window accepts an epoch under (m/s), a little above the 3 124 m/s baseline. */
  private static final double MAX_DELTA_V = 3_400.0;

  /** The margin that carves the slot out of the criterion (m/s) — the Earth problem's own. */
  private static final double MARGIN = 50.0;

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
  @DisplayName("A lunar flyby flies from the pad to its perilune and back out")
  void theMissionFliesFromTheGroundToTheFlyby() {
    // ── the launch date comes from L2's window (§1.4) ────────────────────────
    // Nothing on the spec carries a date; the wizard's planning step will supply one in L5, and
    // here the test plays that role.
    LunarLaunchWindowProblem window =
        new LunarLaunchWindowProblem(
            CANAVERAL_LATITUDE,
            CANAVERAL_LONGITUDE,
            CANAVERAL_ALTITUDE,
            PARKING_ALTITUDE,
            PERILUNE_ALTITUDE,
            new Spacecraft(500, 1200, 1200, PropulsionSystem.getSpacecraftPropulsion()),
            WINDOW_INJECTION_MASS);
    List<LaunchWindow> windows =
        new LaunchWindowSolver(window)
            .solve(
                new LaunchWindowSearch(
                    searchStart,
                    Duration.ofHours(26),
                    window.coarseStep(),
                    window.refinementPrecision(),
                    MAX_DELTA_V,
                    MARGIN,
                    5));
    assertFalse(windows.isEmpty(), "twenty-six hours must hold at least one lunar window");
    LaunchWindow slot =
        windows.stream().min(Comparator.comparingDouble(w -> w.best().deltaV())).orElseThrow();
    AbsoluteDate launchDate = slot.date();
    double plannedMisalignment = window.injectionAt(launchDate).planeMisalignment();
    logger.info(
        "Launch window: {} at {} m/s, β planned = {}°",
        launchDate,
        FastMath.round(slot.best().deltaV()),
        String.format(Locale.ROOT, "%.4f", FastMath.toDegrees(plannedMisalignment)));

    // ── the mission, composed from a spec exactly as the application does ────
    MissionSpec.Lunar spec =
        new MissionSpec.Lunar(
            "Lunar flyby",
            configuration(),
            PARKING_ALTITUDE,
            PERILUNE_ALTITUDE,
            "Cape Canaveral",
            CANAVERAL_LATITUDE,
            CANAVERAL_LONGITUDE,
            CANAVERAL_ALTITUDE,
            null,
            null);
    Mission mission = MissionComposer.compose(spec, OptimizationType.FAST);
    mission.setCurrentState(mission.getInitialState(launchDate));

    long startedAt = System.nanoTime();
    MissionComputeResult result =
        new MissionOptimizer(mission, MAX_EVALUATIONS, TEST_SEED).optimize();
    double wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;

    MissionEphemeris ephemeris = result.ephemeris();
    assertNotNull(ephemeris, "the flight produced no ephemeris");
    logger.info(
        "Lunar flyby: {} points in {} s of wall time",
        ephemeris.allPoints().size(),
        String.format(Locale.ROOT, "%.1f", wallSeconds));

    // ── the arcs: out, past the Moon, and back ───────────────────────────────
    List<SolarSystemBody> arcBodies = new ArrayList<>();
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (arcBodies.isEmpty() || arcBodies.getLast() != point.arc().body()) {
        arcBodies.add(point.arc().body());
      }
    }
    logger.info("Arcs flown, in order: {}", arcBodies);
    assertEquals(
        List.of(SolarSystemBody.EARTH, SolarSystemBody.MOON, SolarSystemBody.EARTH),
        arcBodies,
        "seven days must cross into the lunar sphere and come back out of it");

    // ── the perilune, read on the lunar arc ──────────────────────────────────
    double perilune = Double.POSITIVE_INFINITY;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (point.arc().body() == SolarSystemBody.MOON) {
        perilune = FastMath.min(perilune, point.altitudeMeters());
      }
    }
    logger.info(
        "MEASURE 1 — flown perilune: {} km (aimed {} km, band ±{} km)",
        String.format(Locale.ROOT, "%.1f", perilune / 1000.0),
        PERILUNE_ALTITUDE / 1000.0,
        LunarFlybyMission.PERILUNE_TOLERANCE / 1000.0);
    assertTrue(perilune > 0.0, "the trajectory impacted the Moon, got " + perilune + " m");
    assertEquals(
        PERILUNE_ALTITUDE,
        perilune,
        LunarFlybyMission.PERILUNE_TOLERANCE,
        "the flown perilune must land in the announced band");

    // The same reading through the mission's own objective, which is what the feasibility predicate
    // now runs (§5) and what the truncation guard of §3.4 lives in.
    assertTrue(
        ObjectiveEvaluator.met(ephemeris, mission.getObjective(), Double.NaN),
        "the flown flyby must satisfy the mission's own objective");

    // ── the flight is whole ──────────────────────────────────────────────────
    double flownSeconds = ephemeris.lastPoint().time().durationFrom(launchDate);
    logger.info(
        "Flown duration: {} d of the 7 d horizon",
        String.format(Locale.ROOT, "%.3f", flownSeconds / 86_400.0));
    assertEquals(7.0 * 86_400.0, flownSeconds, 120.0, "the flight must reach the mission horizon");
    assertTrue(ephemeris.isComplete(), "a stage threw during the flight");

    logMeasuredBiases(ephemeris, launchDate, plannedMisalignment);
  }

  /**
   * MEASURES 2 and 3, read off the parking coast the flight actually flew.
   *
   * <p>The parking coast starts at the state the ascent delivered and ends at the injection point,
   * so its two ends carry everything L2 and L1 announced without flying: the β the real geometry
   * produces against the β the window planned, and the nodal regression J2 accumulates over the
   * coast.
   */
  private static void logMeasuredBiases(
      MissionEphemeris ephemeris, AbsoluteDate launchDate, double plannedMisalignment) {
    MissionEphemerisPoint atInsertion = null;
    MissionEphemerisPoint atInjection = null;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (LunarFlybyMission.PARKING_COAST_NAME.equals(point.stageName())) {
        if (atInsertion == null) {
          atInsertion = point;
        }
        atInjection = point;
      }
    }
    if (atInsertion == null || atInsertion == atInjection) {
      logger.warn("The parking coast produced too few samples to read the biases from");
      return;
    }

    Departure departure = TranslunarInjectionPlan.departureFrom(stateOf(atInsertion));
    logger.info(
        "MEASURE 2 — β planned at the window date {}° vs β real at parking insertion {}°"
            + " (Δ = {}°); the parking coast lasted {} s and insertion came {} s after lift-off",
        String.format(Locale.ROOT, "%.4f", FastMath.toDegrees(plannedMisalignment)),
        String.format(Locale.ROOT, "%.4f", FastMath.toDegrees(departure.planeMisalignment())),
        String.format(
            Locale.ROOT,
            "%.4f",
            FastMath.toDegrees(departure.planeMisalignment() - plannedMisalignment)),
        FastMath.round(departure.coastDuration()),
        FastMath.round(atInsertion.time().durationFrom(launchDate)));

    double raanAtInsertion = keplerianOf(atInsertion).getRightAscensionOfAscendingNode();
    double raanAtInjection = keplerianOf(atInjection).getRightAscensionOfAscendingNode();
    logger.info(
        "MEASURE 3 — nodal regression over the parking coast: {}° in {} s",
        String.format(Locale.ROOT, "%.4f", FastMath.toDegrees(raanAtInjection - raanAtInsertion)),
        FastMath.round(atInjection.time().durationFrom(atInsertion.time())));
  }

  /**
   * A fully loaded Falcon Heavy with an inert 5 t payload. Sized by no budget: {@code
   * PropellantBudget} has no translunar case and {@code MissionFactory} refuses this type until L5,
   * so the flight carries the launcher's full capacity and the sizing question stays where the
   * découpage put it.
   */
  private static LaunchConfiguration configuration() {
    return LaunchConfiguration.fullyLoaded(
        Launchers.FALCON_HEAVY, Payloads.CARGO_MODULE.toSpacecraft(5_000.0, 0.0));
  }

  private static SpacecraftState stateOf(MissionEphemerisPoint point) {
    return new SpacecraftState(
            new org.orekit.orbits.CartesianOrbit(
                new TimeStampedPVCoordinates(point.time(), point.position(), point.velocity()),
                point.arc().frame(),
                Constants.WGS84_EARTH_MU))
        .withMass(point.mass());
  }

  private static KeplerianOrbit keplerianOf(MissionEphemerisPoint point) {
    return new KeplerianOrbit(
        new PVCoordinates(point.position(), point.velocity()),
        point.arc().frame(),
        point.time(),
        Constants.WGS84_EARTH_MU);
  }
}
