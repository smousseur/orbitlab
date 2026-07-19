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
 * <p>Three scenarios:
 *
 * <ol>
 *   <li>A trap problem whose cost is artificially flat-bad on the first attempt and well-behaved
 *       afterwards: the optimizer must trigger a retry and succeed below the acceptable cost.
 *   <li>A simple convex problem solved on the first attempt: the optimizer must NOT retry.
 *   <li>A plateau problem whose cost is constant on every attempt: the first retry must still run
 *       (it is the escape mechanism), but plateau detection must skip the remaining retries.
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

  /** A problem stuck at a constant cost above the acceptable threshold on every attempt. */
  static final class PlateauProblem implements TrajectoryProblem {
    private int passCount = 0;

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
      return null;
    }

    @Override
    public double computeCost(SpacecraftState state) {
      return 0.5;
    }
  }

  /**
   * Convex problem with an irreducible cost floor above the acceptable threshold — the synthetic
   * version of a structural optimum (e.g. the gravity turn's FPA-soft floor). Every exploration
   * descends into the same basin: consensus must conclude on the first attempt.
   */
  static final class FlooredConvexProblem implements TrajectoryProblem {
    private int passCount = 0;

    // Exploration runs evaluate this problem from a thread pool: the variables smuggled from
    // propagate() to computeCost() must be per-thread or parallel runs corrupt each other's
    // costs — the strict consensus window makes this test sensitive to that race.
    private final ThreadLocal<double[]> lastVars = ThreadLocal.withInitial(() -> new double[2]);

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
      lastVars.set(variables.clone());
      return null;
    }

    @Override
    public double computeCost(SpacecraftState state) {
      double[] vars = lastVars.get();
      return 0.1 + vars[0] * vars[0] + vars[1] * vars[1];
    }
  }

  @Test
  void consensusOnFlooredOptimum_skipsRefinementAndRetries() {
    FlooredConvexProblem problem = new FlooredConvexProblem();
    // Budget sized so each exploration converges tightly (like production runs): the consensus
    // window is strict and under-converged runs legitimately fail to agree.
    CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, 20_000, 42L);

    OptimizationResult result = optimizer.optimize();

    assertEquals(
        1,
        problem.passCount(),
        "Consensus must conclude on attempt 0: no retry, no seeded start points");
    assertEquals(0.1, result.bestCost(), 1e-3, "Best cost must sit on the structural floor");
  }

  @Test
  void plateauSkipsSecondRetryButNotFirst() {
    PlateauProblem problem = new PlateauProblem();
    CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, 5_000, 42L);

    OptimizationResult result = optimizer.optimize();

    // buildInitialGuess call count: 1 (attempt 0) + 2 (retry 1: seeded start points + pass).
    // Without plateau detection retry 2 would add 2 more calls (total 5).
    assertEquals(
        3,
        problem.passCount(),
        "Plateau: retry 1 must run (escape mechanism) but retry 2 must be skipped");
    assertEquals(0.5, result.bestCost(), 1e-12, "Best cost must be the plateau value");
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
