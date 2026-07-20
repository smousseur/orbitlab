package com.smousseur.orbitlab.simulation.mission.optimizer;

import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * <p>An optional cross-run stop signal lets parallel exploration runs cut each other short: a run
 * that <em>completes</em> (checker convergence or budget exhaustion) with a best cost at or below
 * the problem's acceptable cost flips the signal, and every other run aborts at its next
 * evaluation. The signal is deliberately not raised mid-run at the first threshold crossing: a
 * good analytical seed can start below the acceptable cost, and claiming victory there would end
 * the phase without any optimization having happened (observed: the transfer stage returning its
 * Hohmann seed verbatim, angles unexplored).
 */
final class CMAESRunExecutor {

  private static final Logger logger = LogManager.getLogger(CMAESRunExecutor.class);

  static final double EXCEPTION_PENALTY_COST = 1e10;

  /**
   * Control-flow exception thrown by the objective wrapper when a concurrent run already reached
   * the acceptable cost. Stackless: it carries no diagnostic value, it only unwinds the run.
   */
  private static final class RunAbortedException extends RuntimeException {
    RunAbortedException() {
      super(null, null, false, false);
    }
  }

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
   * @param seed seed for the MersenneTwister driving CMA-ES sampling (run-local, thread-safe)
   * @param crossRunStop shared stop signal between parallel runs, or {@code null} for a
   *     sequential pass. This run sets it when it completes with a best cost at or below the
   *     acceptable cost; when it is set by a concurrent run, this run aborts at its next
   *     evaluation and returns its best-so-far.
   * @return the result containing the best parameters, cost, and evaluation count. An aborted run
   *     that never completed an evaluation reports {@code Double.MAX_VALUE} as its best cost.
   */
  RunResult execute(
      double[] startPoint,
      double[] sigma,
      int populationSize,
      int maxEvals,
      boolean earlyKill,
      long seed,
      AtomicBoolean crossRunStop) {

    double[] runBestVars = startPoint.clone();
    double[] runBestCostHolder = {Double.MAX_VALUE};
    double acceptableCost = problem.getAcceptableCost();

    MultivariateFunction objectiveFunction =
        candidate -> {
          if (crossRunStop != null && crossRunStop.get()) {
            throw new RunAbortedException();
          }
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
            earlyKill, acceptableCost, absoluteTolerance, relativeTolerance);

    // stopFitness stays the configured value on purpose: flooring it at the acceptable cost
    // would stop a well-seeded run at its start point (an analytical seed can already sit below
    // the acceptable cost) before any optimization happened. Per-run termination belongs to the
    // convergence checker.
    CMAESOptimizer optimizer =
        new CMAESOptimizer(
            maxEvals, stopFitness, true, 0, 0, new MersenneTwister(seed), false, checker);

    try {
      optimizer.optimize(
          new MaxEval(maxEvals),
          new ObjectiveFunction(objectiveFunction),
          GoalType.MINIMIZE,
          new InitialGuess(startPoint),
          new CMAESOptimizer.Sigma(sigma),
          new CMAESOptimizer.PopulationSize(populationSize),
          new SimpleBounds(problem.getLowerBounds(), problem.getUpperBounds()));
    } catch (RunAbortedException e) {
      logger.debug("CMA-ES run aborted: a concurrent run completed below the acceptable cost");
    } catch (org.hipparchus.exception.MathRuntimeException e) {
      // Budget exhausted (TooManyEvaluationsException) or numerical issue — use best found so far
      logger.debug("CMA-ES run ended early: {}", e.getMessage());
    }

    // Completion-based cross-run stop: this run has finished its local work (converged or spent
    // its budget). If it ended at or below the target, the laggards' remaining budget buys
    // nothing better — signal them to abort with their best-so-far.
    if (crossRunStop != null && runBestCostHolder[0] <= acceptableCost) {
      crossRunStop.set(true);
    }

    return new RunResult(runBestVars, runBestCostHolder[0], optimizer.getEvaluations());
  }
}
