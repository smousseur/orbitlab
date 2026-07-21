package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.runtime.PropellantLoadOptimizer.Evaluation;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.IgnitionMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.PropellantType;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.ShutdownMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageCapabilities;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the I7 outer sizing loop (spec 09 §6 task 1). Exercises the bisection against a
 * synthetic monotone feasibility function — feasible iff {@code λ ≥ threshold} — so it validates the
 * search logic (minimal feasible λ, bracket invariant, evaluation budget, warm-start threading)
 * without any propagation.
 */
class PropellantLoadOptimizerTest {

  /**
   * Records every call and returns feasibility from a monotone threshold: feasible iff {@code λ ≥
   * threshold}. This is exactly the monotonicity the real feasibility predicate has (more propellant
   * ⇒ more likely to reach the objective), so the bisection's bracket invariant must hold.
   */
  private static final class ThresholdEvaluator implements PropellantLoadOptimizer.Evaluator {
    final double threshold;
    final List<Double> lambdas = new ArrayList<>();
    final List<Evaluation> previousArgs = new ArrayList<>();
    Evaluation lastReturned;

    ThresholdEvaluator(double threshold) {
      this.threshold = threshold;
    }

    @Override
    public Evaluation evaluate(double lambda, Evaluation previous) {
      lambdas.add(lambda);
      previousArgs.add(previous);
      Evaluation e = new Evaluation(lambda, lambda >= threshold, null);
      lastReturned = e;
      return e;
    }
  }

  // ── Bisection: minimal feasible λ ───────────────────────────────────────

  @Test
  void minimize_findsMinimalFeasibleLambda_withinTolerance() {
    double threshold = 0.62;
    PropellantLoadOptimizer optimizer = new PropellantLoadOptimizer();
    ThresholdEvaluator evaluator = new ThresholdEvaluator(threshold);

    PropellantLoadOptimizer.Result result = optimizer.minimize(evaluator);

    assertTrue(result.feasible());
    // The returned λ is feasible …
    assertTrue(result.lambda() >= threshold, () -> "λ* must be feasible, got " + result.lambda());
    // … and no more than one tolerance above the true minimal feasible λ.
    assertTrue(
        result.lambda() - threshold <= PropellantLoadOptimizer.DEFAULT_TOLERANCE,
        () -> "λ* must be within tolerance of the threshold, got " + result.lambda());
    // best mirrors the returned λ.
    assertEquals(result.lambda(), result.best().lambda(), 1e-12);
    assertTrue(result.best().feasible());
  }

  @Test
  void minimize_probesUpperThenLowerBoundFirst() {
    ThresholdEvaluator evaluator = new ThresholdEvaluator(0.62);
    new PropellantLoadOptimizer().minimize(evaluator);

    assertEquals(PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX, evaluator.lambdas.get(0), 1e-12);
    assertEquals(PropellantLoadOptimizer.DEFAULT_LAMBDA_MIN, evaluator.lambdas.get(1), 1e-12);
  }

  @Test
  void minimize_maintainsBracketInvariant_allInfeasibleBelowAllFeasible() {
    ThresholdEvaluator evaluator = new ThresholdEvaluator(0.62);
    new PropellantLoadOptimizer().minimize(evaluator);

    double maxInfeasible = Double.NEGATIVE_INFINITY;
    double minFeasible = Double.POSITIVE_INFINITY;
    for (double lambda : evaluator.lambdas) {
      if (lambda >= evaluator.threshold) {
        minFeasible = Math.min(minFeasible, lambda);
      } else {
        maxInfeasible = Math.max(maxInfeasible, lambda);
      }
    }
    double worstInfeasible = maxInfeasible;
    double bestFeasible = minFeasible;
    assertTrue(
        worstInfeasible < bestFeasible,
        () ->
            "bracket invariant broken: infeasible "
                + worstInfeasible
                + " ≥ feasible "
                + bestFeasible);
  }

  // ── Bisection: short-circuits ───────────────────────────────────────────

  @Test
  void minimize_lowerBoundAlreadyFeasible_returnsLambdaMinInTwoEvals() {
    // Threshold at/below λmin: even the smallest allowed load succeeds.
    ThresholdEvaluator evaluator = new ThresholdEvaluator(0.1);
    PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);

    assertTrue(result.feasible());
    assertEquals(PropellantLoadOptimizer.DEFAULT_LAMBDA_MIN, result.lambda(), 1e-12);
    assertEquals(2, result.evaluations(), "upper + lower bound probes only");
  }

  @Test
  void minimize_upperBoundInfeasible_reportsInfeasibleInOneEval() {
    // Threshold above λmax: even the heuristic loads fail → under-dotée mission.
    ThresholdEvaluator evaluator = new ThresholdEvaluator(1.5);
    PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);

    assertFalse(result.feasible());
    assertEquals(PropellantLoadOptimizer.DEFAULT_LAMBDA_MAX, result.lambda(), 1e-12);
    assertEquals(1, result.evaluations(), "stops at the failing upper-bound probe");
    assertFalse(result.best().feasible());
  }

  // ── Bisection: budget ───────────────────────────────────────────────────

  @Test
  void minimize_neverExceedsEvaluationBudget() {
    // A mid-range threshold forces the deepest search; assert the budget is respected.
    for (double threshold = 0.31; threshold < 1.0; threshold += 0.03) {
      ThresholdEvaluator evaluator = new ThresholdEvaluator(threshold);
      PropellantLoadOptimizer.Result result = new PropellantLoadOptimizer().minimize(evaluator);
      assertTrue(
          result.evaluations() <= PropellantLoadOptimizer.DEFAULT_MAX_EVALUATIONS,
          () -> "budget exceeded at threshold " + evaluator.threshold);
      assertEquals(evaluator.lambdas.size(), result.evaluations(), "evaluation count matches calls");
    }
  }

  @Test
  void minimize_tightToleranceStopsAtBudget() {
    // Tolerance well below what 10 evals can resolve: the budget, not convergence, must stop it.
    PropellantLoadOptimizer optimizer = new PropellantLoadOptimizer(0.3, 1.0, 1e-9, 10);
    ThresholdEvaluator evaluator = new ThresholdEvaluator(0.62);

    PropellantLoadOptimizer.Result result = optimizer.minimize(evaluator);

    assertEquals(10, result.evaluations(), "must spend exactly the budget");
    assertTrue(result.feasible());
  }

  // ── Warm-start threading ────────────────────────────────────────────────

  @Test
  void minimize_threadsPreviousEvaluationForWarmStart() {
    ThresholdEvaluator evaluator = new ThresholdEvaluator(0.62);
    new PropellantLoadOptimizer().minimize(evaluator);

    assertNull(evaluator.previousArgs.get(0), "first call has no previous evaluation");
    for (int i = 1; i < evaluator.previousArgs.size(); i++) {
      // Each call's `previous` is exactly the Evaluation returned by the preceding call.
      double expectedLambda = evaluator.lambdas.get(i - 1);
      assertNotNull(evaluator.previousArgs.get(i), "warm-start source missing");
      assertEquals(
          expectedLambda,
          evaluator.previousArgs.get(i).lambda(),
          1e-12,
          "previous must be the immediately preceding evaluation");
    }
  }

  // ── Constructor validation ──────────────────────────────────────────────

  @Test
  void constructor_rejectsInvalidSettings() {
    assertThrows(IllegalArgumentException.class, () -> new PropellantLoadOptimizer(1.0, 0.3, 0.02, 10));
    assertThrows(IllegalArgumentException.class, () -> new PropellantLoadOptimizer(0.3, 1.0, 0.0, 10));
    assertThrows(IllegalArgumentException.class, () -> new PropellantLoadOptimizer(0.3, 1.0, 0.02, 1));
  }

  // ── λ → loads mapping ───────────────────────────────────────────────────

  @Test
  void scaledLoads_scalesOnlyFlaggedStages() {
    double[] heuristic = {1_000_000, 100_000, 5_000};
    boolean[] mask = {false, true, false}; // only the middle (liquid) stage is scaled
    double[] scaled = PropellantLoadOptimizer.scaledLoads(0.5, heuristic, mask);

    assertEquals(1_000_000, scaled[0], 1e-9, "solid stage untouched");
    assertEquals(50_000, scaled[1], 1e-9, "liquid stage scaled by λ");
    assertEquals(5_000, scaled[2], 1e-9, "non-scaled stage untouched");
    // Input array not mutated.
    assertEquals(100_000, heuristic[1], 1e-9);
  }

  @Test
  void scaledLoads_lengthMismatch_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PropellantLoadOptimizer.scaledLoads(0.5, new double[] {1, 2}, new boolean[] {true}));
  }

  @Test
  void lambdaScaledMask_flagsLiquidStagesOnly() {
    StageModel solidBooster =
        new StageModel(
            "SRB",
            30_000,
            240_000,
            new PropulsionSystem(275, 7_000_000),
            new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.BURN_TO_DEPLETION,
                PropellantType.SOLID,
                0.0,
                StageRole.BOOSTER));
    StageModel cryoCore =
        new StageModel(
            "Core",
            15_000,
            170_000,
            new PropulsionSystem(431, 1_400_000),
            new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.COMMANDED,
                PropellantType.CRYOGENIC,
                0.0,
                StageRole.CORE));
    StageModel storableUpper =
        new StageModel(
            "Upper",
            4_000,
            15_000,
            new PropulsionSystem(446, 67_000),
            new StageCapabilities(
                IgnitionMode.AIRSTART,
                1,
                ShutdownMode.COMMANDED,
                PropellantType.STORABLE,
                Double.POSITIVE_INFINITY,
                StageRole.UPPER));
    LauncherModel launcher =
        new LauncherModel(
            "MIXED",
            "Mixed solid+liquid",
            List.of(solidBooster, cryoCore, storableUpper),
            new AscentProfile(7, 3, 2));

    boolean[] mask = PropellantLoadOptimizer.lambdaScaledMask(launcher);

    assertArrayEquals(new boolean[] {false, true, true}, mask);
  }
}
