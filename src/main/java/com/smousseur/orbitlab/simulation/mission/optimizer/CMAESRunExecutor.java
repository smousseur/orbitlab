package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.analysis.MultivariateFunction;
import org.hipparchus.optim.InitialGuess;
import org.hipparchus.optim.MaxEval;
import org.hipparchus.optim.SimpleBounds;
import org.hipparchus.optim.nonlinear.scalar.GoalType;
import org.hipparchus.optim.nonlinear.scalar.ObjectiveFunction;
import org.hipparchus.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.hipparchus.random.MersenneTwister;
import org.orekit.propagation.SpacecraftState;

/**
 * Executes a single CMA-ES optimization run for a {@link TrajectoryProblem}.
 *
 * <p>Wraps objective function evaluation (with exception penalty), CMAESOptimizer construction,
 * and result capture. Delegates convergence decisions to {@link AdaptiveConvergenceChecker}.
 */
final class CMAESRunExecutor {

  private static final Logger logger = LogManager.getLogger(CMAESRunExecutor.class);

  static final double EXCEPTION_PENALTY_COST = 1e10;

  /**
   * Result of a single CMA-ES optimization run.
   *
   * @param bestVariables the parameter vector that produced the lowest cost
   * @param bestCost the lowest cost value achieved during the run
   * @param evaluations the number of objective function evaluations performed
   */
  record RunResult(double[] bestVariables, double bestCost, int evaluations) {}

  private final TrajectoryProblem problem;
  private final double stopFitness;
  private final double absoluteTolerance;
  private final double relativeTolerance;

  /**
   * Creates a new executor for running CMA-ES optimization passes.
   *
   * @param problem the trajectory problem defining the objective function and bounds
   * @param stopFitness fitness value at which the optimizer stops immediately
   * @param absoluteTolerance absolute convergence tolerance
   * @param relativeTolerance relative convergence tolerance
   */
  CMAESRunExecutor(
      TrajectoryProblem problem,
      double stopFitness,
      double absoluteTolerance,
      double relativeTolerance) {
    this.problem = problem;
    this.stopFitness = stopFitness;
    this.absoluteTolerance = absoluteTolerance;
    this.relativeTolerance = relativeTolerance;
  }

  /**
   * Runs a single CMA-ES pass with the given configuration.
   *
   * <p>The objective function wraps the problem's propagation and cost computation, assigning a
   * large penalty cost when exceptions occur or when the cost is NaN/Infinite.
   *
   * @param startPoint initial parameter vector for the optimization
   * @param sigma initial standard deviations for each parameter dimension
   * @param populationSize CMA-ES population size per generation
   * @param maxEvals maximum number of objective function evaluations
   * @param earlyKill if true, the convergence checker will kill runs stuck in bad basins
   * @return the result containing the best parameters, cost, and evaluation count
   */
  RunResult execute(
      double[] startPoint, double[] sigma, int populationSize, int maxEvals, boolean earlyKill) {

    double[] runBestVars = startPoint.clone();
    double[] runBestCostHolder = {Double.MAX_VALUE};

    MultivariateFunction objectiveFunction =
        candidate -> {
          try {
            SpacecraftState state = problem.propagate(candidate);
            double cost = problem.computeCost(state);
            if (Double.isNaN(cost) || Double.isInfinite(cost)) {
              return EXCEPTION_PENALTY_COST;
            }
            synchronized (runBestCostHolder) {
              if (cost < runBestCostHolder[0]) {
                runBestCostHolder[0] = cost;
                System.arraycopy(candidate, 0, runBestVars, 0, candidate.length);
              }
            }
            return cost;
          } catch (Exception e) {
            synchronized (runBestCostHolder) {
              if (EXCEPTION_PENALTY_COST < runBestCostHolder[0]) {
                runBestCostHolder[0] = EXCEPTION_PENALTY_COST;
                System.arraycopy(candidate, 0, runBestVars, 0, candidate.length);
              }
            }
            return EXCEPTION_PENALTY_COST;
          }
        };

    AdaptiveConvergenceChecker checker =
        new AdaptiveConvergenceChecker(
            earlyKill, problem.getAcceptableCost(), absoluteTolerance, relativeTolerance);

    CMAESOptimizer optimizer =
        new CMAESOptimizer(maxEvals, stopFitness, true, 0, 0, new MersenneTwister(), false, checker);

    try {
      optimizer.optimize(
          new MaxEval(maxEvals),
          new ObjectiveFunction(objectiveFunction),
          GoalType.MINIMIZE,
          new InitialGuess(startPoint),
          new CMAESOptimizer.Sigma(sigma),
          new CMAESOptimizer.PopulationSize(populationSize),
          new SimpleBounds(problem.getLowerBounds(), problem.getUpperBounds()));
    } catch (org.hipparchus.exception.MathRuntimeException e) {
      // Budget exhausted (TooManyEvaluationsException) or numerical issue — use best found so far
      logger.debug("CMA-ES run ended early: {}", e.getMessage());
    }

    return new RunResult(runBestVars, runBestCostHolder[0], optimizer.getEvaluations());
  }
}
