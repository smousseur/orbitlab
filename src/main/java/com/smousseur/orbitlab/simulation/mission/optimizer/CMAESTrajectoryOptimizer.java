package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>If the final cost is still above {@link TrajectoryProblem#getAcceptableCost()} after a full
 * exploration + refinement pass, the optimizer retries up to {@link #DEFAULT_MAX_RETRIES} times
 * with progressively more exploration runs, larger initial sigma, and seeded starting points
 * biased away from any saturated bound observed on the previous attempt.
 */
public class CMAESTrajectoryOptimizer implements TrajectoryOptimizer {
  private static final Logger logger = LogManager.getLogger(CMAESTrajectoryOptimizer.class);

  private final TrajectoryProblem problem;
  private final int maxEvaluations;
  private final int numExplorationRuns;
  private final int maxRetries;

  private final MersenneTwister rng = new MersenneTwister();

  // ── Adaptive thresholds ──
  /** Cost below which we consider we've found a good basin and switch to refinement-only. */
  private static final double GOOD_BASIN_THRESHOLD = 0.01;

  /** Number of refinement passes with decreasing sigma. */
  private static final int REFINEMENT_PASSES = 3;

  /** Sigma reduction factor per refinement pass. */
  private static final double[] REFINEMENT_SIGMA_FACTORS = {0.1, 0.03, 0.01};

  /** Default maximum number of retries when the first attempt fails to reach acceptable cost. */
  public static final int DEFAULT_MAX_RETRIES = 2;

  /** Per-attempt scaling of the number of exploration runs (index 0 = first attempt). */
  private static final int[] RETRY_EXPLORATION_RUNS_BONUS = {0, 2, 4};

  /** Per-attempt multiplier on the initial sigma vector (index 0 = first attempt). */
  private static final double[] RETRY_SIGMA_SCALE = {1.0, 1.3, 1.6};

  private final CMAESRunExecutor executor;

  /**
   * Creates an optimizer with default exploration runs, retries, tolerances, and stop fitness.
   *
   * @param problem the trajectory problem to optimize
   * @param maxEvaluations total budget of objective function evaluations across all phases (per
   *     retry attempt)
   */
  public CMAESTrajectoryOptimizer(TrajectoryProblem problem, int maxEvaluations) {
    this(problem, maxEvaluations, 4, DEFAULT_MAX_RETRIES, 1e-6, 1e-4, 1e-6);
  }

  /**
   * Creates an optimizer with full control over exploration, retry, and convergence parameters.
   *
   * @param problem the trajectory problem to optimize
   * @param maxEvaluations total budget of objective function evaluations across all phases (per
   *     retry attempt)
   * @param numExplorationRuns number of independent exploration runs in phase 1 (first attempt)
   * @param maxRetries maximum retries on top of the first attempt; each retry repeats the full
   *     pipeline with broader exploration and seeded starting points
   * @param stopFitness fitness value at which the CMA-ES optimizer stops immediately
   * @param relativeTolerance relative convergence tolerance for cost improvement
   * @param absoluteTolerance absolute convergence tolerance for cost improvement
   */
  public CMAESTrajectoryOptimizer(
      TrajectoryProblem problem,
      int maxEvaluations,
      int numExplorationRuns,
      int maxRetries,
      double stopFitness,
      double relativeTolerance,
      double absoluteTolerance) {
    this.problem = problem;
    this.maxEvaluations = maxEvaluations;
    this.numExplorationRuns = numExplorationRuns;
    this.maxRetries = maxRetries;
    this.executor = new CMAESRunExecutor(problem, stopFitness, absoluteTolerance, relativeTolerance);
  }

  @Override
  public OptimizationResult optimize() {
    double[] lower = problem.getLowerBounds();
    double[] upper = problem.getUpperBounds();
    double[] baseSigma = problem.getInitialSigma();
    double acceptableCost = problem.getAcceptableCost();

    double[] globalBestVars = null;
    double globalBestCost = Double.MAX_VALUE;
    int totalEvaluations = 0;
    boolean previousSaturated = false;

    int totalAttempts = maxRetries + 1;
    for (int attempt = 0; attempt < totalAttempts; attempt++) {
      int idx = FastMath.min(attempt, RETRY_SIGMA_SCALE.length - 1);
      int explorationRuns = numExplorationRuns + RETRY_EXPLORATION_RUNS_BONUS[idx];
      double[] attemptSigma = scaleSigma(baseSigma, RETRY_SIGMA_SCALE[idx]);
      List<double[]> seededStartPoints = buildSeededStartPoints(attempt, previousSaturated, lower, upper);

      if (attempt > 0) {
        logger.info(
            "Retry {}/{} triggered: previous cost={} > acceptable={}, "
                + "explorationRuns={}, sigmaScale={}, anti-saturation={}",
            attempt,
            maxRetries,
            globalBestCost,
            acceptableCost,
            explorationRuns,
            RETRY_SIGMA_SCALE[idx],
            previousSaturated);
      }

      SinglePassResult pass =
          runSinglePass(explorationRuns, attemptSigma, seededStartPoints, lower, upper);
      totalEvaluations += pass.evaluations();

      if (pass.bestVars() != null && pass.bestCost() < globalBestCost) {
        globalBestCost = pass.bestCost();
        globalBestVars = pass.bestVars().clone();
      }

      if (globalBestVars != null && globalBestCost <= acceptableCost) {
        if (attempt > 0) {
          logger.info("Retry {} succeeded: cost={}", attempt, globalBestCost);
        }
        break;
      }

      if (globalBestVars != null) {
        previousSaturated = hasSaturatedParameter(globalBestVars, lower, upper);
      }
    }

    if (globalBestVars == null) {
      throw new OrbitlabException("Optimization failed to find a valid solution");
    }

    SpacecraftState bestState = problem.propagate(globalBestVars);
    logger.info(
        "Final best cost={}, total evals={}, attempts up to {}",
        globalBestCost,
        totalEvaluations,
        totalAttempts);
    if (globalBestCost > problem.getAcceptableCost()) {
      logger.warn(
          "Final cost {} above acceptable {} after {} attempts",
          globalBestCost,
          problem.getAcceptableCost(),
          totalAttempts);
    }
    return new OptimizationResult(globalBestVars, globalBestCost, bestState, totalEvaluations);
  }

  // ══════════════════════════════════════════════════════════════════════
  // Single attempt (exploration + refinement)
  // ══════════════════════════════════════════════════════════════════════

  /** Result of one full exploration + refinement pass. */
  private record SinglePassResult(double[] bestVars, double bestCost, int evaluations) {}

  private SinglePassResult runSinglePass(
      int explorationRuns,
      double[] sigma,
      List<double[]> seededStartPoints,
      double[] lower,
      double[] upper) {

    double[] initialGuess = problem.buildInitialGuess();
    int n = problem.getNumVariables();
    int basePopSize = 4 + (int) (3 * FastMath.log(n));

    double[] bestVars = null;
    double bestCost = Double.MAX_VALUE;
    int totalEvals = 0;
    int remainingEvals = maxEvaluations;

    int explorationBudget = (int) (maxEvaluations * 0.4);
    int evalsPerExploration = explorationBudget / explorationRuns;

    // ── Phase 1: Exploration ─────────────────────────────────────────────
    for (int run = 0; run < explorationRuns; run++) {
      if (remainingEvals < evalsPerExploration / 2) break;

      double[] startPoint;
      double[] runSigma;
      int populationSize;

      if (run < seededStartPoints.size()) {
        startPoint = seededStartPoints.get(run).clone();
        runSigma = sigma.clone();
        populationSize = basePopSize;
      } else if (run == 0) {
        startPoint = initialGuess.clone();
        runSigma = sigma.clone();
        populationSize = basePopSize;
      } else if (bestCost < GOOD_BASIN_THRESHOLD) {
        startPoint = perturbLocal(bestVars, sigma, 0.3);
        runSigma = scaleSigma(sigma, 0.5);
        populationSize = basePopSize;
      } else {
        double[] base = (bestVars != null) ? bestVars : initialGuess;
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
        CMAESRunExecutor.RunResult result =
            executor.execute(startPoint, runSigma, populationSize, runBudget, true);
        totalEvals += result.evaluations();
        remainingEvals -= result.evaluations();

        logger.info(
            "Exploration {}/{}: cost={}, evals={}",
            run + 1,
            explorationRuns,
            result.bestCost(),
            result.evaluations());

        if (result.bestCost() < bestCost) {
          bestCost = result.bestCost();
          bestVars = result.bestVariables().clone();
        }

        if (bestCost <= problem.getAcceptableCost()) {
          logger.info("Target reached during exploration: cost={}", bestCost);
          break;
        }
      } catch (Exception e) {
        logger.warn("Exploration {} failed: {}", run + 1, e.getMessage());
        totalEvals += runBudget / 4;
        remainingEvals -= runBudget / 4;
      }
    }

    // ── Phase 2: Refinement cascade ──────────────────────────────────────
    if (bestVars != null && bestCost > problem.getAcceptableCost()) {
      logger.info(
          "Refinement cascade starting from cost={}, remaining budget={}", bestCost, remainingEvals);

      int refinePassBudget = remainingEvals / REFINEMENT_PASSES;

      for (int pass = 0; pass < REFINEMENT_PASSES; pass++) {
        if (remainingEvals < 500) break;
        if (bestCost <= problem.getAcceptableCost()) break;

        double sigmaFactor = REFINEMENT_SIGMA_FACTORS[pass];
        double[] refineSigma = new double[n];
        for (int i = 0; i < n; i++) {
          refineSigma[i] = FastMath.max(sigma[i] * sigmaFactor, (upper[i] - lower[i]) * 1e-4);
        }

        int budget = FastMath.min(refinePassBudget, remainingEvals);

        try {
          CMAESRunExecutor.RunResult result =
              executor.execute(bestVars.clone(), refineSigma, basePopSize, budget, false);
          totalEvals += result.evaluations();
          remainingEvals -= result.evaluations();

          logger.info(
              "Refinement {}/{} (sigma×{}): cost={}, evals={}",
              pass + 1,
              REFINEMENT_PASSES,
              sigmaFactor,
              result.bestCost(),
              result.evaluations());

          if (result.bestCost() < bestCost) {
            bestCost = result.bestCost();
            bestVars = result.bestVariables().clone();
          }
        } catch (Exception e) {
          logger.warn("Refinement pass {} failed: {}", pass + 1, e.getMessage());
        }
      }
    }

    return new SinglePassResult(bestVars, bestCost, totalEvals);
  }

  // ══════════════════════════════════════════════════════════════════════
  // Seed selection for retries
  // ══════════════════════════════════════════════════════════════════════

  /**
   * Builds the list of imposed starting points for the leading exploration runs of a given attempt.
   *
   * <p>For attempt 0, prepends the pure analytical (e.g., Hohmann) seed when the problem provides
   * one (Niveau 3.2 of the robustness roadmap), followed by the {@code initialGuess}. Retries add
   * physically diverse seeds so CMA-ES is not pulled back into the same basin.
   */
  private List<double[]> buildSeededStartPoints(
      int attempt, boolean previousSaturated, double[] lower, double[] upper) {
    if (attempt == 0) {
      List<double[]> seeds = new ArrayList<>();
      double[] analyticalSeed = problem.buildAnalyticalSeed();
      if (analyticalSeed != null) {
        seeds.add(analyticalSeed.clone());
        seeds.add(problem.buildInitialGuess());
        logger.info(
            "Niveau 3.2: warm-start with pure analytical seed for run 0, initialGuess for run 1");
      }
      return seeds;
    }

    List<double[]> seeds = new ArrayList<>();
    double[] guess = problem.buildInitialGuess();

    seeds.add(guess.clone());
    seeds.add(blend(guess, lower, upper, 0.7));
    seeds.add(blend(guess, lower, upper, 0.3));

    if (previousSaturated || attempt >= 2) {
      double[] center = blend(guess, lower, upper, 0.5);
      seeds.add(center);
    }

    return seeds;
  }

  /**
   * Returns a starting point where each component is shifted toward the {@code position} fraction
   * of its [lower, upper] range, falling back to the existing guess when the bounds are
   * degenerate.
   */
  private static double[] blend(double[] guess, double[] lower, double[] upper, double position) {
    double[] out = new double[guess.length];
    for (int i = 0; i < guess.length; i++) {
      double range = upper[i] - lower[i];
      if (range > 0) {
        out[i] = lower[i] + position * range;
      } else {
        out[i] = guess[i];
      }
    }
    return out;
  }

  private static boolean hasSaturatedParameter(double[] best, double[] lower, double[] upper) {
    return OptimizerDiagnostics.evaluateBounds(best, lower, upper).stream()
        .anyMatch(f -> f.lowSat() || f.highSat());
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
