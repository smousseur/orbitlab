package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlanOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * PHY-2 / L5 §3.3 — the measurement the bascule was assumed against, made runnable. Flipping the
 * default to drag-on makes every production computation pay the ascent's drag-on cost that {@code
 * L1} measured at ×7.5 on the gravity turn ({@code docs/atmosphere/08-conception-L1-PHY-2.md} §6).
 * This runs the <b>real production computation</b> — a {@link MissionPlanOptimizer} on a Falcon
 * Heavy LEO-400 built the way the wizard builds it, which for an {@link MissionSpec.EarthOrbit}
 * routes to the two-pass {@code MeasuredLoadPlanner} of {@code L4} ("un dimensionnement + vol
 * planner", §3.3) — drag-on against the same mission opted out, and logs the ratio.
 *
 * <p><b>It goes through {@link MissionPlanOptimizer}, not a planner directly</b>, precisely so it
 * exercises the LEO production path: the sizing sees the flown Δv, drag included. Driving {@code
 * FixedLoadPlanner} at the analytic loads instead measured the wrong thing — those loads are
 * drag-agnostic, and a drag-on ascent flown at them under-delivers into a sub-orbital hand-off the
 * analytic transfer cannot recover.
 *
 * <p><b>Termination is the only assertion, and that is deliberate</b> — the same stance as its L1
 * sibling {@code AscentDragTerminationTest}. The number is logged, not asserted: it is the
 * escalation trigger of §3.3 (if the per-computation cost is intolerable, the "cheap atmosphere for
 * the optimizer" lever of {@code L1} §6 is opened as its own lot; if it is a tolerable one-shot,
 * the bascule stands as is), an input to a decision, not a property of L5. A drag-on run that fails
 * to <em>close</em> is itself the finding — it fails here with the propagation's own reason, which
 * is the honest outcome to surface rather than to swallow.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class DefaultAtmosphereCostFlightTest extends AbstractTrajectoryOptimizerTest {

  private static final Logger logger = LogManager.getLogger(DefaultAtmosphereCostFlightTest.class);

  /** Kourou, the site the Falcon Heavy LEO-400 profile of PHY-2 was measured from. */
  private static final double LATITUDE_DEG = 5.23;

  private static final double LONGITUDE_DEG = -52.77;
  private static final double TARGET_ALTITUDE_M = 400_000.0;

  public static final int PARKING_ALTITUDE = 400_000;

  /**
   * Per-run wall-clock bound. Not a performance target — it turns a non-terminating drag-on run
   * back into a failure rather than a hang. The two-pass sizing runs a few optimizations, so a
   * drag-on production computation is minutes; a preemptive timeout means the socle failed to bound
   * a low-diving candidate.
   */
  private static final Duration TERMINATION_BUDGET = Duration.ofMinutes(20);

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void defaultDragOnCost_againstVacuum_falconHeavyLeo400() {
    Map<String, Object> values = leo400WizardValues(Launchers.FALCON_HEAVY);

    // Drag-on is the production default: MissionFactory applies NRLMSISE at the single origin.
    MissionSpec dragOn = MissionFactory.specFromWizardValues(values, MissionType.LEO);
    assertEquals(
        AtmosphereModel.NRLMSISE, dragOn.atmosphere(), "the wizard default is drag-on since L5");
    // The same mission opted out — the reference the ratio is read against.
    MissionSpec dragOff = dragOn.withAtmosphere(AtmosphereModel.NONE);

    double dragOffSeconds = timeProductionComputation(dragOff);
    double dragOnSeconds = timeProductionComputation(dragOn);

    logger.info(
        "[PHY-2 L5 ×cost] Falcon Heavy LEO-400 production computation (FAST, two-pass sizing): "
            + "drag-off {} s, drag-on {} s, ratio ×{}",
        fmt(dragOffSeconds),
        fmt(dragOnSeconds),
        fmt(dragOnSeconds / dragOffSeconds));
  }

  @Test
  void defaultDragOnCost_ariane64Leo400() {
    Map<String, Object> values = leo400WizardValues(Launchers.ARIANE_64);

    MissionSpec mission = MissionFactory.specFromWizardValues(values, MissionType.LEO);
    double computationTime = timeProductionComputation(mission);
    logger.info("Computation Time = {}", fmt(computationTime));
  }

  /**
   * Runs one full production computation the way the runtime does — {@link MissionPlanOptimizer} on
   * a {@link MissionEntry}, which selects the two-pass sizing for an EarthOrbit mission — times it,
   * logs the achieved orbit, and returns the seconds.
   */
  private double timeProductionComputation(MissionSpec spec) {
    MissionEntry entry = new MissionEntry(spec);
    entry.setOptimizationType(OptimizationType.FAST);
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    long start = System.nanoTime();
    MissionPlan plan =
        assertTimeoutPreemptively(
            TERMINATION_BUDGET,
            () -> new MissionPlanOptimizer(entry, epoch).compute(),
            () ->
                "the "
                    + spec.atmosphere()
                    + " production computation did not terminate within the budget");
    double seconds = (System.nanoTime() - start) / 1e9;

    assertNotNull(plan, "the planner must return a plan under " + spec.atmosphere());

    MissionEphemerisPoint last = plan.computation().ephemeris().lastPoint();
    KeplerianOrbit orbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    double apogee = orbit.getA() * (1.0 + orbit.getE()) - earthRadius;
    double perigee = orbit.getA() * (1.0 - orbit.getE()) - earthRadius;
    logger.info(
        "[PHY-2 L5 ×cost] {} run: {} s, achieved {} x {} km, final mass {} kg",
        spec.atmosphere(),
        fmt(seconds),
        fmt(perigee / 1000.0),
        fmt(apogee / 1000.0),
        fmt(last.mass()));
    return seconds;
  }

  private static Map<String, Object> leo400WizardValues(LauncherModel launcher) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Falcon Heavy LEO 400 (default-atmosphere cost)");
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", LATITUDE_DEG);
    values.put("LAUNCH_SITE_LONG", LONGITUDE_DEG);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", launcher.id());
    values.put("PAYLOAD_TYPE", Payloads.EARTH_OBSERVATION_SAT.id());
    values.put("PAYLOAD_MASS", Payloads.EARTH_OBSERVATION_SAT.defaultDryMass());
    values.put("LEO_PERIGEE_ALT", TARGET_ALTITUDE_M / 1000.0);
    values.put("LEO_APOGEE_ALT", TARGET_ALTITUDE_M / 1000.0);
    values.put("GTO_PARKING_ALT", PARKING_ALTITUDE / 1000.0);
    return values;
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
