package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;

/**
 * Verifies the retry wrapper of {@link CMAESTrajectoryOptimizer} using mock {@link
 * TrajectoryProblem} implementations. No Orekit data, propagator, or {@link SpacecraftState} is
 * exercised — the mocks return {@code null} states and arbitrary scalar costs.
 *
 * <p>Two scenarios:
 *
 * <ol>
 *   <li>A trap problem whose cost is artificially flat-bad on the first attempt and well-behaved
 *       afterwards: the optimizer must trigger a retry and succeed below the acceptable cost.
 *   <li>A simple convex problem solved on the first attempt: the optimizer must NOT retry.
 * </ol>
 */
class CMAESTrajectoryOptimizerRetryTest {

  /** Acceptable cost shared by both problems — chosen well below typical convex residuals. */
  private static final double ACCEPTABLE_COST = 0.01;

  /**
   * A problem that traps the very first attempt at a constant cost above the acceptable threshold,
   * then exposes a real distance-to-origin cost on subsequent attempts.
   *
   * <p>"Pass" detection leverages the optimizer contract: {@link #buildInitialGuess()} is called
   * exactly once per single pass (and an additional time per retry by {@code
   * buildSeededStartPoints}). Counting calls therefore distinguishes the first attempt (passCount
   * == 1) from any retry (passCount &ge; 2).
   */
  static final class TrapThenConvexProblem implements TrajectoryProblem {
    private int passCount = 0;
    private double[] lastVars = new double[2];

    int passCount() {
      return passCount;
    }

    @Override
    public double getAcceptableCost() {
      return ACCEPTABLE_COST;
    }

    @Override
    public int getNumVariables() {
      return 2;
    }

    @Override
    public double[] buildInitialGuess() {
      passCount++;
      return new double[] {0.5, 0.5};
    }

    @Override
    public double[] getLowerBounds() {
      return new double[] {-1.0, -1.0};
    }

    @Override
    public double[] getUpperBounds() {
      return new double[] {1.0, 1.0};
    }

    @Override
    public double[] getInitialSigma() {
      return new double[] {0.6, 0.6};
    }

    @Override
    public SpacecraftState propagate(double[] variables) {
      lastVars = variables.clone();
      return null;
    }

    @Override
    public double computeCost(SpacecraftState state) {
      if (passCount <= 1) {
        return 0.5;
      }
      return lastVars[0] * lastVars[0] + lastVars[1] * lastVars[1];
    }
  }

  /** Convex distance-to-origin problem, solvable on the first attempt without any retry. */
  static final class ConvexProblem implements TrajectoryProblem {
    private int passCount = 0;
    private double[] lastVars = new double[2];

    int passCount() {
      return passCount;
    }

    @Override
    public double getAcceptableCost() {
      return ACCEPTABLE_COST;
    }

    @Override
    public int getNumVariables() {
      return 2;
    }

    @Override
    public double[] buildInitialGuess() {
      passCount++;
      return new double[] {0.4, 0.4};
    }

    @Override
    public double[] getLowerBounds() {
      return new double[] {-1.0, -1.0};
    }

    @Override
    public double[] getUpperBounds() {
      return new double[] {1.0, 1.0};
    }

    @Override
    public double[] getInitialSigma() {
      return new double[] {0.6, 0.6};
    }

    @Override
    public SpacecraftState propagate(double[] variables) {
      lastVars = variables.clone();
      return null;
    }

    @Override
    public double computeCost(SpacecraftState state) {
      return lastVars[0] * lastVars[0] + lastVars[1] * lastVars[1];
    }
  }

  @Test
  void retryRecoversFromBadFirstAttempt() {
    TrapThenConvexProblem problem = new TrapThenConvexProblem();
    CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, 5_000);

    OptimizationResult result = optimizer.optimize();

    assertTrue(
        problem.passCount() >= 2,
        () ->
            "Expected at least one retry (passCount >= 2 due to buildSeededStartPoints), got "
                + problem.passCount());
    assertTrue(
        result.bestCost() <= ACCEPTABLE_COST,
        () ->
            "Expected final cost <= "
                + ACCEPTABLE_COST
                + " after retry, got "
                + result.bestCost());
    double[] bestVars = result.bestVariables();
    double dist = Math.sqrt(bestVars[0] * bestVars[0] + bestVars[1] * bestVars[1]);
    assertTrue(
        dist < 0.2,
        () -> "Expected solution near origin after retry, got distance " + dist);
  }

  @Test
  void noRetryWhenFirstAttemptSucceeds() {
    ConvexProblem problem = new ConvexProblem();
    CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, 5_000);

    OptimizationResult result = optimizer.optimize();

    assertTrue(
        result.bestCost() <= ACCEPTABLE_COST,
        () -> "Convex problem should converge below acceptable cost, got " + result.bestCost());
    assertEquals(
        1,
        problem.passCount(),
        "First attempt should succeed without retry; passCount must remain 1 "
            + "(no buildSeededStartPoints, no second runSinglePass).");
  }
}
