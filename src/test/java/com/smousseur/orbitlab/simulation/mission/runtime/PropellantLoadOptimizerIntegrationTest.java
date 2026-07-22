package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * I7 outer-loop integration tests (spec 09 §6 task 4, extended to GEO per bilan 10 §7.3). Runs the
 * full propellant-sizing bisection on real Falcon Heavy missions and asserts the exit criterion:
 * the loads found at {@code λ*} weigh strictly less than the heuristic loads, and the mission at
 * {@code λ*} is feasible. Only the sized top stage (S2) is scaled — the un-staged S1 is dragged
 * until depletion and already flies "just enough", so it stays off λ (see {@link
 * PropellantLoadOptimizer#lambdaScaledMask}); on GEO the payload's AKM is sized separately by
 * {@link PropellantBudget#loadsForGeo} and never appears in the launcher loads.
 *
 * <p>The two scenarios differ structurally in where the sized stage's residual ends up: on LEO, S2
 * is the final active stage and its residual is read at mission end; on GEO, S2 is jettisoned after
 * the GTO injection with its residual aboard, which exercises the per-stage split and the jettison
 * capture (bilan 10 §6) — the stack-wide total would only see the AKM.
 *
 * <p><b>Slow / nightly.</b> Each bisection evaluation is a complete mission optimization (~1.5 min),
 * so each loop is ~15 min. They are opt-in: enable with {@code -Dorbitlab.slowTests=true}. The
 * per-λ progress, the resolved {@code λ*} and the per-stage load comparison are logged at INFO by
 * {@link PropellantLoadOptimizer} and {@link MissionLoadEvaluator}.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
public class PropellantLoadOptimizerIntegrationTest {
  private static final Logger logger =
      LogManager.getLogger(PropellantLoadOptimizerIntegrationTest.class);

  /** Same launch latitude the optimized-transfer LEO mission flies from (its default site). */
  private static final double LAUNCH_LATITUDE_DEG = 45.96;

  private static final double TARGET_ALTITUDE_M = 400_000.0;

  // ── GEO scenario ──
  /** GEO mission default site (Kourou), mirrors GEOMission's private defaults. */
  private static final double GEO_LAUNCH_LATITUDE_DEG = 5.23;

  private static final double GEO_LAUNCH_LONGITUDE_DEG = -52.77;
  private static final double GEO_PARKING_ALTITUDE_M = 400_000.0;
  private static final double GEO_ALTITUDE_M = 35_786_000.0;

  /**
   * Feasibility band on the final orbit: the ±50 km bar {@code GEOMissionOptimizationTest} asserts,
   * expressed as the ratio {@code objectiveMet} consumes. The ±7 % LEO default would accept an
   * orbit thousands of km off GEO.
   */
  private static final double GEO_ALTITUDE_TOLERANCE_M = 50_000.0;

  private static final double GEO_INCLINATION_TOLERANCE_RAD = FastMath.toRadians(0.05);

  /**
   * Realistic heavy payload (10 t) — unlike the 150 kg legacy sat, this makes the sized top stage
   * (S2) do real work and carry a reclaimable margin. {@link PropellantBudget} sizes S2 well under
   * half its capacity for this mass, so there is genuine propellant for the loop to shave.
   */
  private static final double PAYLOAD_MASS_KG = 10_000.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void leo400km_shrinksHeuristicLoads_andStaysFeasible() {
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(PAYLOAD_MASS_KG, 0.0);

    // Heuristic loads (10 % margin) — the λ = 1 baseline the loop must beat.
    double[] heuristicLoads =
        PropellantBudget.loadsForLeo(launcher, payload, TARGET_ALTITUDE_M, LAUNCH_LATITUDE_DEG);
    boolean[] mask = PropellantLoadOptimizer.lambdaScaledMask(launcher);
    logStageLoads("Heuristic loads", launcher, heuristicLoads, mask);

    AbsoluteDate launchEpoch =
        new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    // Each evaluation rebuilds the LEO mission (optimized transfer, spec 06 I6 — the inner loop I7
    // runs per spec 09 §2) with the scaled loads and optimizes it end to end.
    Function<double[], Mission> missionBuilder =
        loads ->
            LEOMission.withOptimizedTransfer(
                "I7 LEO 400 km",
                new LaunchConfiguration(launcher, loads, payload),
                TARGET_ALTITUDE_M);

    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(missionBuilder, heuristicLoads, mask, launchEpoch);

    PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);

    double[] scaledLoads =
        PropellantLoadOptimizer.scaledLoads(result.lambda(), heuristicLoads, mask);
    logStageLoads("Loads at λ*=" + result.lambda(), launcher, scaledLoads, mask);

    double sumHeuristic = Arrays.stream(heuristicLoads).sum();
    double sumScaled = Arrays.stream(scaledLoads).sum();
    double residualRatio =
        result.best().result() != null
            ? result.best().result().performanceReport().residualRatio()
            : Double.NaN;
    logger.info(
        "I7 result: feasible={}, λ*={}, evals={}, Σload heuristic={} kg → λ*={} kg (−{} kg,"
            + " −{}%), residual ratio at λ*={}",
        result.feasible(),
        result.lambda(),
        result.evaluations(),
        Math.round(sumHeuristic),
        Math.round(sumScaled),
        Math.round(sumHeuristic - sumScaled),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * (1.0 - sumScaled / sumHeuristic)),
        residualRatio);

    assertTrue(
        result.feasible(),
        "Heuristic loads must themselves be feasible for the loop to have anything to shrink");
    assertTrue(
        result.lambda() <= PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX,
        () -> "λ* must not exceed the heuristic (upper) bound, got " + result.lambda());
    // Exit criterion (spec 09 §6): the found loads weigh strictly less than the heuristic loads.
    assertTrue(
        sumScaled < sumHeuristic,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "λ* loads (%.0f kg) must be below the heuristic loads (%.0f kg); λ*=%.4f",
                sumScaled,
                sumHeuristic,
                result.lambda()));
  }

  /**
   * GEO counterpart (bilan 10 §7.3): the sized S2 does the GTO injection and is jettisoned with its
   * residual aboard, the AKM (off λ, sized by the budget) circularizes at apogee. Feasibility is
   * measured against the flown final orbit — circular GEO — not the mission's recorded {@code
   * (parking, GEO)} objective, and at the ±50 km bar of the GEO optimization test.
   */
  @Test
  void geo_shrinksHeuristicLoads_andStaysFeasible() {
    LauncherModel launcher = Launchers.FALCON_HEAVY;
    double payloadDryMass = Payloads.GEO_SAT.defaultDryMass();

    // Heuristic loads (10 % margin) — the λ = 1 baseline the loop must beat. The AKM load is
    // sized once here and stays fixed across evaluations: λ only scales the launcher's S2.
    PropellantBudget.GeoLoads geoLoads =
        PropellantBudget.loadsForGeo(
            launcher,
            Payloads.GEO_SAT,
            payloadDryMass,
            GEO_PARKING_ALTITUDE_M,
            GEO_LAUNCH_LATITUDE_DEG);
    double[] heuristicLoads = geoLoads.launcherLoads();
    double akmLoad = geoLoads.akmLoad();
    boolean[] mask = PropellantLoadOptimizer.lambdaScaledMask(launcher);
    logStageLoads("GEO heuristic loads", launcher, heuristicLoads, mask);
    logger.info("GEO heuristic AKM load: {} kg (off λ)", Math.round(akmLoad));

    AbsoluteDate launchEpoch =
        new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());

    Function<double[], Mission> missionBuilder =
        loads ->
            new GEOMission(
                "I7 GEO",
                new LaunchConfiguration(
                    launcher, loads, Payloads.GEO_SAT.toSpacecraft(payloadDryMass, akmLoad)),
                GEO_PARKING_ALTITUDE_M,
                GEO_ALTITUDE_M,
                GEO_LAUNCH_LATITUDE_DEG,
                GEO_LAUNCH_LONGITUDE_DEG,
                0.0,
                0.0);

    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(
            missionBuilder,
            heuristicLoads,
            mask,
            launchEpoch,
            MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS,
            42L,
            GEO_ALTITUDE_TOLERANCE_M / GEO_ALTITUDE_M,
            MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
            OrbitInsertionObjective.circular(SolarSystemBody.EARTH, GEO_ALTITUDE_M, 0.0));

    PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);

    double[] scaledLoads =
        PropellantLoadOptimizer.scaledLoads(result.lambda(), heuristicLoads, mask);
    logStageLoads("GEO loads at λ*=" + result.lambda(), launcher, scaledLoads, mask);

    double sumHeuristic = Arrays.stream(heuristicLoads).sum();
    double sumScaled = Arrays.stream(scaledLoads).sum();
    logger.info(
        "I7 GEO result: feasible={}, λ*={}, evals={}, Σload heuristic={} kg → λ*={} kg (−{} kg,"
            + " −{}%)",
        result.feasible(),
        result.lambda(),
        result.evaluations(),
        Math.round(sumHeuristic),
        Math.round(sumScaled),
        Math.round(sumHeuristic - sumScaled),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * (1.0 - sumScaled / sumHeuristic)));

    assertTrue(
        result.feasible(),
        "Heuristic GEO loads must themselves be feasible for the loop to have anything to shrink");
    assertTrue(
        result.lambda() <= PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX,
        () -> "λ* must not exceed the heuristic (upper) bound, got " + result.lambda());
    assertTrue(
        sumScaled < sumHeuristic,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "λ* loads (%.0f kg) must be below the heuristic loads (%.0f kg); λ*=%.4f",
                sumScaled,
                sumHeuristic,
                result.lambda()));

    MissionComputeResult best = result.best().result();
    assertNotNull(best, "The feasible best evaluation must carry its mission compute result");

    // The GEO-specific mechanism under test (bilan 10 §6): S2 is jettisoned after the GTO
    // injection, so its residual only exists in the per-stage split captured at separation — and
    // it must clear the floor of its own load, not hide behind the AKM's.
    StagePropellant s2 =
        best.performanceReport()
            .residualForStage(1)
            .orElseThrow(() -> new AssertionError("no per-stage propellant split for S2"));
    logger.info(
        "GEO S2 at λ*: loaded={} kg, residual at jettison={} kg ({}% of its load)",
        Math.round(s2.loaded()),
        Math.round(s2.residual()),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * s2.residualRatio()));
    assertTrue(
        s2.residualRatio() >= MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "S2 residual at jettison (%.0f kg) is below %.0f%% of its %.0f kg load",
                s2.residual(),
                100.0 * MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
                s2.loaded()));

    // The retained solution must still meet the full GEO quality bar on the plane, which the
    // altitude-only feasibility predicate does not check (the AKM and the node trim own it).
    MissionEphemerisPoint last = best.ephemeris().lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    assertTrue(
        finalOrbit.getI() < GEO_INCLINATION_TOLERANCE_RAD,
        () ->
            String.format(
                java.util.Locale.ROOT,
                "Final inclination %.4f° exceeds tolerance %.3f°",
                FastMath.toDegrees(finalOrbit.getI()),
                FastMath.toDegrees(GEO_INCLINATION_TOLERANCE_RAD)));
  }

  private static void logStageLoads(
      String label, LauncherModel launcher, double[] loads, boolean[] mask) {
    for (int i = 0; i < loads.length; i++) {
      logger.info(
          "{} — stage {} '{}': {} kg (λ-scaled={}, capacity={} kg)",
          label,
          i,
          launcher.stages().get(i).name(),
          Math.round(loads[i]),
          mask[i],
          Math.round(launcher.stages().get(i).propellantCapacity()));
    }
  }
}
