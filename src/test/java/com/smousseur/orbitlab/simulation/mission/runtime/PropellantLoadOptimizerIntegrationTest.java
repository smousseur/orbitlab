package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * I7 outer-loop integration test (spec 09 §6 task 4). Runs the full propellant-sizing bisection on a
 * real LEO 400 km mission (Falcon Heavy, optimized transfer) and asserts the exit criterion: the
 * loads found at {@code λ*} weigh strictly less than the heuristic loads, and the mission at {@code
 * λ*} is feasible (objective within ±7 %). Only the sized top stage (S2) is scaled — the un-staged
 * S1 is dragged to orbit and already flies "just enough", so it stays off λ (see {@link
 * PropellantLoadOptimizer#lambdaScaledMask}).
 *
 * <p><b>Slow / nightly.</b> Each bisection evaluation is a complete mission optimization (~1.5 min),
 * so the whole loop is ~15 min. It is opt-in: enable with {@code -Dorbitlab.slowTests=true}. The
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
