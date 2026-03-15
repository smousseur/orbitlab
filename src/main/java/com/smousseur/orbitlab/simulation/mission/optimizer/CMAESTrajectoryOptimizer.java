package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.core.OrbitlabException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.random.MersenneTwister;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;

/**
 * Adaptive CMA-ES optimizer with three phases:
 *
 * <ol>
 *   <li><b>Exploration</b> — short runs to locate the best basin quickly. Runs that land in bad
 *       basins are killed early via the convergence checker.
 *   <li><b>Refinement cascade</b> — progressively tighter sigma passes around the best solution
 *       found, each with generous budget.
 *   <li><b>No rescue phase</b> — once a good basin is found (cost < 0.01), further exploration
 *       wastes budget. All remaining evals go to refinement.
 * </ol>
 */
public class CMAESTrajectoryOptimizer implements TrajectoryOptimizer {
  private static final Logger logger = LogManager.getLogger(CMAESTrajectoryOptimizer.class);

  private final TrajectoryProblem problem;
  private final int maxEvaluations;
  private final int numExplorationRuns;

  private final MersenneTwister rng = new MersenneTwister();

  // ── Adaptive thresholds ──
  /** Cost below which we consider we've found a good basin and switch to refinement-only. */
  private static final double GOOD_BASIN_THRESHOLD = 0.01;

  /** Number of refinement passes with decreasing sigma. */
  private static final int REFINEMENT_PASSES = 3;

  /** Sigma reduction factor per refinement pass. */
  private static final double[] REFINEMENT_SIGMA_FACTORS = {0.1, 0.03, 0.01};

  private final CMAESRunExecutor executor;

  /**
   * Creates an optimizer with default exploration runs, tolerances, and stop fitness.
   *
   * @param problem the trajectory problem to optimize
   * @param maxEvaluations total budget of objective function evaluations across all phases
   */
  public CMAESTrajectoryOptimizer(TrajectoryProblem problem, int maxEvaluations) {
    this(problem, maxEvaluations, 4, 1e-6, 1e-4, 1e-6);
  }

  /**
   * Creates an optimizer with full control over exploration and convergence parameters.
   *
   * @param problem the trajectory problem to optimize
   * @param maxEvaluations total budget of objective function evaluations across all phases
   * @param numExplorationRuns number of independent exploration runs in phase 1
   * @param stopFitness fitness value at which the CMA-ES optimizer stops immediately
   * @param relativeTolerance relative convergence tolerance for cost improvement
   * @param absoluteTolerance absolute convergence tolerance for cost improvement
   */
  public CMAESTrajectoryOptimizer(
      TrajectoryProblem problem,
      int maxEvaluations,
      int numExplorationRuns,
      double stopFitness,
      double relativeTolerance,
      double absoluteTolerance) {
    this.problem = problem;
    this.maxEvaluations = maxEvaluations;
    this.numExplorationRuns = numExplorationRuns;
    this.executor = new CMAESRunExecutor(problem, stopFitness, absoluteTolerance, relativeTolerance);
  }

  @Override
  public OptimizationResult optimize() {
    double[] globalBestVars = null;
    double globalBestCost = Double.MAX_VALUE;
    int totalEvaluations = 0;
    int remainingEvals = maxEvaluations;

    double[] initialGuess = problem.buildInitialGuess();
    double[] lower = problem.getLowerBounds();
    double[] upper = problem.getUpperBounds();
    double[] sigma = problem.getInitialSigma();
    int n = problem.getNumVariables();
    int basePopSize = 4 + (int) (3 * FastMath.log(n));

    // Budget: ~40% exploration, ~60% refinement
    int explorationBudget = (int) (maxEvaluations * 0.4);
    int evalsPerExploration = explorationBudget / numExplorationRuns;

    // ════════════════════════════════════════════════════════════════════
    // Phase 1: Exploration — find the best basin quickly
    // ════════════════════════════════════════════════════════════════════
    for (int run = 0; run < numExplorationRuns; run++) {
      if (remainingEvals < evalsPerExploration / 2) break;

      double[] startPoint;
      double[] runSigma;
      int populationSize;

      if (run == 0) {
        // First run: start from the physics-based initial guess
        startPoint = initialGuess.clone();
        runSigma = sigma.clone();
        populationSize = basePopSize;
      } else if (globalBestCost < GOOD_BASIN_THRESHOLD) {
        // Good basin found — explore locally around it
        startPoint = perturbLocal(globalBestVars, sigma, 0.3);
        runSigma = scaleSigma(sigma, 0.5);
        populationSize = basePopSize;
      } else {
        // No good basin yet — explore more broadly
        double[] base = (globalBestVars != null) ? globalBestVars : initialGuess;
        startPoint = perturbGlobal(base, lower, upper, sigma, run);
        runSigma = new double[n];
        for (int i = 0; i < n; i++) {
          runSigma[i] = FastMath.min(sigma[i] * 1.5, (upper[i] - lower[i]) / 3.0);
        }
        populationSize = FastMath.min(basePopSize * 2, 100);
      }

      clampToBounds(startPoint, lower, upper);
      int runBudget = FastMath.min(evalsPerExploration, remainingEvals);

      try {
        CMAESRunExecutor.RunResult result = executor.execute(startPoint, runSigma, populationSize, runBudget, true);
        totalEvaluations += result.evaluations();
        remainingEvals -= result.evaluations();

        logger.info(
            "Exploration {}/{}: cost={}, evals={}",
            run + 1,
            numExplorationRuns,
            result.bestCost(),
            result.evaluations());

        if (result.bestCost() < globalBestCost) {
          globalBestCost = result.bestCost();
          globalBestVars = result.bestVariables().clone();
        }

        if (globalBestCost <= problem.getAcceptableCost()) {
          logger.info("Target reached during exploration: cost={}", globalBestCost);
          break;
        }
      } catch (Exception e) {
        logger.warn("Exploration {} failed: {}", run + 1, e.getMessage());
        // Don't charge full budget for failed runs
        totalEvaluations += runBudget / 4;
        remainingEvals -= runBudget / 4;
      }
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 2: Refinement cascade — polish the best solution
    // ════════════════════════════════════════════════════════════════════
    if (globalBestVars != null && globalBestCost > problem.getAcceptableCost()) {
      logger.info(
          "Refinement cascade starting from cost={}, remaining budget={}",
          globalBestCost,
          remainingEvals);

      int refinePassBudget = remainingEvals / REFINEMENT_PASSES;

      for (int pass = 0; pass < REFINEMENT_PASSES; pass++) {
        if (remainingEvals < 500) break;
        if (globalBestCost <= problem.getAcceptableCost()) break;

        double sigmaFactor = REFINEMENT_SIGMA_FACTORS[pass];
        double[] refineSigma = new double[n];
        for (int i = 0; i < n; i++) {
          // Sigma relative to current search space, not initial sigma
          // This ensures we don't under-explore if the best is far from the initial guess
          refineSigma[i] = FastMath.max(sigma[i] * sigmaFactor, (upper[i] - lower[i]) * 1e-4);
        }

        int budget = FastMath.min(refinePassBudget, remainingEvals);

        try {
          CMAESRunExecutor.RunResult result =
              executor.execute(globalBestVars.clone(), refineSigma, basePopSize, budget, false);
          totalEvaluations += result.evaluations();
          remainingEvals -= result.evaluations();

          logger.info(
              "Refinement {}/{} (sigma×{}): cost={}, evals={}",
              pass + 1,
              REFINEMENT_PASSES,
              sigmaFactor,
              result.bestCost(),
              result.evaluations());

          if (result.bestCost() < globalBestCost) {
            globalBestCost = result.bestCost();
            globalBestVars = result.bestVariables().clone();
          }
        } catch (Exception e) {
          logger.warn("Refinement pass {} failed: {}", pass + 1, e.getMessage());
        }
      }
    }

    // ════════════════════════════════════════════════════════════════════
    // Final result
    // ════════════════════════════════════════════════════════════════════
    if (globalBestVars == null) {
      throw new OrbitlabException("Optimization failed to find a valid solution");
    }

    SpacecraftState bestState = problem.propagate(globalBestVars);
    logger.info("Final best cost={}, total evals={}", globalBestCost, totalEvaluations);
    return new OptimizationResult(globalBestVars, globalBestCost, bestState, totalEvaluations);
  }

  // ══════════════════════════════════════════════════════════════════════
  // Start point strategies
  // ══════════════════════════════════════════════════════════════════════

  /** Small perturbation around a known good point. */
  private double[] perturbLocal(double[] base, double[] sigma, double scale) {
    double[] result = new double[base.length];
    for (int i = 0; i < base.length; i++) {
      result[i] = base[i] + rng.nextGaussian() * sigma[i] * scale;
    }
    return result;
  }

  /** Broader perturbation mixing local and global exploration. */
  private double[] perturbGlobal(
      double[] base, double[] lower, double[] upper, double[] sigma, int seed) {
    double[] result = new double[base.length];
    for (int i = 0; i < base.length; i++) {
      double range = upper[i] - lower[i];
      // Progressive: later runs explore more globally
      double mixRatio = FastMath.min(0.2 * seed, 0.7);
      double local = base[i] + rng.nextGaussian() * sigma[i] * 2.0;
      double global = lower[i] + rng.nextDouble() * range;
      result[i] = (1.0 - mixRatio) * local + mixRatio * global;
    }
    return result;
  }

  private double[] scaleSigma(double[] sigma, double factor) {
    double[] scaled = new double[sigma.length];
    for (int i = 0; i < sigma.length; i++) {
      scaled[i] = sigma[i] * factor;
    }
    return scaled;
  }

  private static void clampToBounds(double[] point, double[] lower, double[] upper) {
    for (int i = 0; i < point.length; i++) {
      point[i] = FastMath.max(lower[i], FastMath.min(upper[i], point[i]));
    }
  }
}
