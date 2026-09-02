package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
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
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowProblem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
 * AbstractTrajectoryOptimizerTest}. The PHY-4 demonstration L6 removed flew at {@code
 * MAX_EVALUATIONS = 1} because none of its stages was optimizable; this chain has one, and the lot
 * closes on "a flight from the ground to the flyby". Replaying a pinned ascent vector would be fast
 * and deterministic and would freeze a number in the very lot that flies this chain for the first
 * time, before anyone knows what it should be — and the ascent references have already had to be
 * re-recorded once, after MIS-7.
 *
 * <p><b>Three measurements ride on this flight and nothing else can take them</b> (§8.3): the
 * perilune actually reached, before the ±10 km band of §4.1 is fixed; the two biases L2 chiffered
 * without flying — 68 s of out-of-model ascent and 115 s of nodal regression — read as the gap
 * between the β planned at the window date and the β real at injection; and the half-degree of J2
 * regression L1 §5 pt 1 computed without flying. They are <b>logged, not asserted</b>: pinning them
 * would pin numbers this lot exists to find out.
 *
 * <p><b>Two flights since MIS-4 / L5.</b> The one above is L4's and is left untouched — L4 §11
 * bequeaths it to L6 as the impulsive reference the finite burn will be measured against. The
 * second flies the same chain on the loads {@code PropellantBudget.loadsForLunar} sizes, which is
 * what a mission created in the wizard takes off with; see {@link
 * #theSizedConfigurationAlsoReachesThePerilune()}.
 *
 * <p><b>Contrainte de méthode</b> (découpage §3): each flight costs a full CMA-ES ascent plus seven
 * days of propagation, and it is the user who runs them.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
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
   * Mass at injection handed to the <b>window</b> (kg), and to the window alone.
   *
   * <p><b>It is the mass the fully loaded chain really arrives with, and the window is given the
   * launcher that really flies</b> (MIS-4 / L6 §9.6). The budget-sized profile arrives far lighter
   * and hands the window {@code LunarLoads.massAtInjection()} instead: a mass that does not match
   * the vehicle resolves the wrong stage, and since L6 the window's verdict is on reachability and
   * not only cost, so a wrong stage empties the window rather than mispricing it.
   *
   * <p>It used to be 1 700 kg on a 3 kN spacecraft motor — the PHY-4 demonstration's fixture — on
   * the reasoning that the date is set by the encounter geometry and the vehicle only enters the
   * depletion-floor verdict. That reasoning held while the injection was impulsive and stopped
   * holding when it became a burn: a finite departure reaches <em>fewer</em> perilunes than an
   * impulse, so {@code confirm} now decides reachability and not only cost, and it can only decide
   * it for the vehicle that will fly. Screened on the kick motor, the window returned an epoch
   * whose aim bottoms out at 132 km against a 100 km target — a date the mission cannot honour.
   */
  private static final double FULLY_LOADED_INJECTION_MASS = 61_400.0;

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
    fly("fully loaded", configuration(), FULLY_LOADED_INJECTION_MASS);
  }

  /**
   * MIS-4 / L5 §7.1 — the same chain, <b>sized by {@code PropellantBudget.loadsForLunar}</b>
   * instead of flying the launcher's full capacity.
   *
   * <p><b>Why a second flight and not an assertion.</b> The budget is a heuristic seed: on {@code
   * PRECISE} the λ sweep starts from it and an under-sizing is invisible, absorbed by the sweep. On
   * {@code FAST} — the mode every mission created in the wizard flies ({@code MissionEntry:42}) —
   * those loads are what takes off. The risk this lot introduces is therefore only visible in a
   * flight, and only in this mode.
   *
   * <p><b>The flight above is left exactly as L4 wrote it</b>, and deliberately: L4 §11 bequeaths
   * it to L6 as the impulsive reference the finite burn will be measured against, and moving it
   * would move the next lot's baseline.
   */
  @Test
  @DisplayName("The budget's own sizing flies the same flyby")
  void theSizedConfigurationAlsoReachesThePerilune() {
    Sized sized = sizedConfiguration();
    fly("budget-sized", sized.configuration(), sized.massAtInjection());
  }

  /**
   * The flight itself: window, composition, optimization, and the three readings the lot takes off
   * the ephemeris.
   *
   * @param label what the loads came from, for the logs
   * @param configuration the launcher, its loads and the payload
   * @param windowInjectionMass the mass this chain reaches the injection point with (kg), which is
   *     what resolves the stage the window takes its verdict on
   */
  private void fly(String label, LaunchConfiguration configuration, double windowInjectionMass) {
    // ── the launch date comes from L2's window (§1.4) ────────────────────────
    // Nothing on the spec carries a date. Since L5 the wizard's planning step supplies one, on
    // the very problem LunarLaunchWindowPlanner builds; here the test plays that role.
    LunarLaunchWindowProblem window =
        new LunarLaunchWindowProblem(
            CANAVERAL_LATITUDE,
            CANAVERAL_LONGITUDE,
            CANAVERAL_ALTITUDE,
            PARKING_ALTITUDE,
            PERILUNE_ALTITUDE,
            configuration.toVehicleStack(),
            windowInjectionMass);
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

    // ── the epoch: the cheapest the chain can actually plan (§9.8) ───────────
    // The window confirms on the injection state a pad *would* reach; the chain arrives with the
    // one its ascent really delivered, and MEASURE 2 below is the bias between the two. Since L6
    // that bias can decide feasibility and not only cost: a finite departure reaches fewer
    // perilunes than an impulse, and at the cheapest epoch of this search the aim has no root at
    // all — its perilune bottoms out at 132 km against the 100 km asked for, rising on both sides
    // of the offset that reaches it. So the epochs are tried in cost order and the first one the
    // chain can plan is flown. This does not bypass the window: every date tried came from it.
    List<LaunchWindow> byCost =
        windows.stream()
            .sorted(Comparator.comparingDouble(w -> w.best().deltaV()))
            .collect(Collectors.toList());

    MissionComputeResult result = null;
    Mission mission = null;
    AbsoluteDate launchDate = null;
    double plannedMisalignment = 0.0;
    double wallSeconds = 0.0;
    OrbitlabException lastRefusal = null;

    for (int attempt = 0; attempt < byCost.size() && result == null; attempt++) {
      LaunchWindow slot = byCost.get(attempt);
      launchDate = slot.date();
      plannedMisalignment = window.injectionAt(launchDate).planeMisalignment();
      logger.info(
          "Launch window: {} at {} m/s, β planned = {}°",
          launchDate,
          FastMath.round(slot.best().deltaV()),
          String.format(Locale.ROOT, "%.4f", FastMath.toDegrees(plannedMisalignment)));

      // ── the mission, composed from a spec exactly as the application does ──
      MissionSpec.Lunar spec =
          new MissionSpec.Lunar(
              "Lunar flyby (" + label + ")",
              configuration,
              PARKING_ALTITUDE,
              PERILUNE_ALTITUDE,
              "Cape Canaveral",
              CANAVERAL_LATITUDE,
              CANAVERAL_LONGITUDE,
              CANAVERAL_ALTITUDE,
              null,
              null);
      mission = MissionComposer.compose(spec, OptimizationType.FAST);
      mission.setCurrentState(mission.getInitialState(launchDate));

      long startedAt = System.nanoTime();
      try {
        result = new MissionOptimizer(mission, MAX_EVALUATIONS, TEST_SEED).optimize();
      } catch (OrbitlabException refused) {
        lastRefusal = refused;
        logger.info(
            "Epoch {} cannot be planned by the chain: {}", launchDate, refused.getMessage());
        continue;
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
    logger.info(
        "Lunar flyby [{}]: {} points in {} s of wall time, loads {}",
        label,
        ephemeris.allPoints().size(),
        String.format(Locale.ROOT, "%.1f", wallSeconds),
        Arrays.toString(configuration.propellantLoads()));

    // ── the arcs: out, past the Moon, and back ───────────────────────────────
    List<SolarSystemBody> arcBodies = new ArrayList<>();
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (arcBodies.isEmpty() || arcBodies.getLast() != point.arc().body()) {
        arcBodies.add(point.arc().body());
      }
    }
    logger.info("Arcs flown, in order: {}", arcBodies);

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

  /**
   * A budget-sized chain and the mass it reaches the injection point with — the second being what
   * the window needs to resolve the right stage (§9.6).
   *
   * @param configuration the launcher, its sized loads and the payload
   * @param massAtInjection the mass at the injection point (kg)
   */
  private record Sized(LaunchConfiguration configuration, double massAtInjection) {}

  /**
   * The chain as the wizard builds it: an inert 2 t lunar probe, and loads sized top-down from it
   * by {@code PropellantBudget.loadsForLunar} (MIS-4 / L5 §5.3).
   */
  private static Sized sizedConfiguration() {
    Spacecraft probe = Payloads.LUNAR_PROBE.toSpacecraft(2_000.0, 0.0);
    PropellantBudget.LunarLoads loads =
        PropellantBudget.loadsForLunar(
            Launchers.FALCON_HEAVY,
            probe,
            PARKING_ALTITUDE,
            CANAVERAL_LATITUDE,
            // Due east: this chain flies i = phi, where the two azimuth branches merge.
            FastMath.PI / 2);
    logger.info(
        "Budget sizing: loads {}, mass at injection {} kg",
        Arrays.toString(loads.launcherLoads()),
        FastMath.round(loads.massAtInjection()));
    return new Sized(
        new LaunchConfiguration(
            Launchers.FALCON_HEAVY, loads.launcherLoads(), probe, Payloads.LUNAR_PROBE.id()),
        loads.massAtInjection());
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
