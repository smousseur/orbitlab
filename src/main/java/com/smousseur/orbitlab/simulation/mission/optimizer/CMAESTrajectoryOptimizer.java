package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *       basins are killed early via the convergence checker, and the first run to <em>complete</em>
 *       below the acceptable cost aborts every other run (cross-run early stop): the phase costs
 *       the time of the first converged success, not of the slowest run.
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
 *
 * <p><b>Plateau detection</b>: refinement passes and retries that fail to improve the best cost by
 * more than {@link #STAGNATION_RELATIVE_EPS} (relative) are cut short — when every phase lands on
 * the same optimum, the cost floor is structural (e.g. irreducible penalty terms) and the
 * remaining budget would only re-find it. The first retry is never skipped: a flat first attempt
 * says nothing about what broader exploration can reach (see the trap-problem retry test).
 *
 * <p><b>Consensus early-stop</b>: when at least {@link #CONSENSUS_MIN_RUNS} exploration runs
 * started from different points, genuinely <em>descended</em> (best cost below their start-point
 * cost by {@link #CONSENSUS_DESCENT_RATIO}) and agree on the attempt's best cost within {@link
 * #CONSENSUS_RELATIVE_EPS}, the landscape has one dominant basin: refinement and all remaining
 * retries are skipped. The descent requirement is what protects the trap-problem contract — on a
 * flat deceptive landscape the runs do not descend, so no consensus forms and the escape retry
 * still runs.
 */
public class CMAESTrajectoryOptimizer implements TrajectoryOptimizer {
  private static final Logger logger = LogManager.getLogger(CMAESTrajectoryOptimizer.class);

  private final TrajectoryProblem problem;
  private final int maxEvaluations;
  private final int numExplorationRuns;
  private final int maxRetries;

  private final MersenneTwister rng;

  // ── Adaptive thresholds ──
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

  /**
   * Relative cost improvement below which a refinement pass or a retry is considered stagnant.
   * Kept strict (parts-per-million) so only a true plateau is cut: a retry that relaxes bounds
   * (e.g. β1 anti-saturation) and still cannot move the cost has genuinely hit the floor.
   */
  private static final double STAGNATION_RELATIVE_EPS = 1e-6;

  /** Number of descended, agreeing exploration runs that concludes the attempt (consensus). */
  private static final int CONSENSUS_MIN_RUNS = 2;

  /** Relative cost window within which two exploration results are considered the same optimum. */
  private static final double CONSENSUS_RELATIVE_EPS = 1e-4;

  /**
   * Minimum relative descent from a run's start-point cost for it to count toward the consensus.
   * A run that barely moved (flat landscape, or warm start already at the optimum) carries no
   * evidence that the shared value is a genuine attractor.
   */
  private static final double CONSENSUS_DESCENT_RATIO = 0.01;

  private final CMAESRunExecutor executor;

  /**
   * Creates an optimizer with default exploration runs, retries, tolerances, and stop fitness, and
   * a non-deterministic master seed (current default behavior).
   *
   * @param problem the trajectory problem to optimize
   * @param maxEvaluations total budget of objective function evaluations across all phases (per
   *     retry attempt)
   */
  public CMAESTrajectoryOptimizer(TrajectoryProblem problem, int maxEvaluations) {
    this(problem, maxEvaluations, System.nanoTime());
  }

  /**
   * Creates an optimizer with default exploration runs, retries, tolerances, and stop fitness, and
   * an explicit master seed (for reproducible runs, e.g. tests).
   *
   * @param problem the trajectory problem to optimize
   * @param maxEvaluations total budget of objective function evaluations across all phases (per
   *     retry attempt)
   * @param seed master seed driving all CMA-ES randomness in this optimizer instance
   */
  public CMAESTrajectoryOptimizer(TrajectoryProblem problem, int maxEvaluations, long seed) {
    this(problem, maxEvaluations, 4, DEFAULT_MAX_RETRIES, 1e-6, 1e-4, 1e-6, seed);
  }

  /**
   * Creates an optimizer with full control over exploration, retry, and convergence parameters.
   * Uses a non-deterministic master seed.
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
    this(
        problem,
        maxEvaluations,
        numExplorationRuns,
        maxRetries,
        stopFitness,
        relativeTolerance,
        absoluteTolerance,
        System.nanoTime());
  }

  /**
   * Creates an optimizer with full control over exploration, retry, and convergence parameters and
   * an explicit master seed.
   *
   * @param seed master seed driving all CMA-ES randomness in this optimizer instance
   */
  public CMAESTrajectoryOptimizer(
      TrajectoryProblem problem,
      int maxEvaluations,
      int numExplorationRuns,
      int maxRetries,
      double stopFitness,
      double relativeTolerance,
      double absoluteTolerance,
      long seed) {
    this.problem = problem;
    this.maxEvaluations = maxEvaluations;
    this.numExplorationRuns = numExplorationRuns;
    this.maxRetries = maxRetries;
    this.rng = new MersenneTwister(seed);
    this.executor = new CMAESRunExecutor(problem, stopFitness, absoluteTolerance, relativeTolerance);
    logger.info("CMA-ES optimizer initialized with seed={}", seed);
  }

  @Override
  public OptimizationResult optimize() {
    double acceptableCost = problem.getAcceptableCost();

    double[] globalBestVars = null;
    double globalBestCost = Double.MAX_VALUE;
    int totalEvaluations = 0;
    boolean previousSaturated = false;

    int totalAttempts = maxRetries + 1;
    for (int attempt = 0; attempt < totalAttempts; attempt++) {
      // Per-attempt bounds and sigma — problems may relax β1 (or other parameters) on retry
      // when the previous attempt saturated; default impl returns the un-relaxed bounds.
      double[] lower = problem.getLowerBoundsForAttempt(attempt, globalBestVars);
      double[] upper = problem.getUpperBoundsForAttempt(attempt, globalBestVars);
      double[] baseSigma = problem.getInitialSigmaForAttempt(attempt, globalBestVars);

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

      double costBeforeAttempt = globalBestCost;
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

      if (pass.consensusPlateau() && globalBestVars != null) {
        // Multiple independent explorations descended to the same optimum: the floor is real,
        // further retries would only re-find it (logged by runSinglePass).
        break;
      }

      if (attempt > 0 && !improvedMeaningfully(costBeforeAttempt, globalBestCost)) {
        logger.info(
            "Plateau detected: retry {} left best cost at {} (relative improvement <= {}); "
                + "skipping remaining retries",
            attempt,
            globalBestCost,
            STAGNATION_RELATIVE_EPS);
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
  private record SinglePassResult(
      double[] bestVars, double bestCost, int evaluations, boolean consensusPlateau) {}

  /** Pre-computed configuration for one parallel exploration run. */
  private record RunConfig(double[] startPoint, double[] runSigma, int populationSize, int budget) {}

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

    // ── Phase 1: Exploration (parallel) ──────────────────────────────────
    // Pre-compute every run's config sequentially (uses this.rng, which is not
    // thread-safe). Each config is independent of the others' results, so they
    // can be executed concurrently — the Hohmann warm-start lives in run 0,
    // additional seededStartPoints come next, and remaining slots get
    // perturbGlobal seeds around the analytical guess.
    int parallelBudget = FastMath.min(evalsPerExploration, remainingEvals / FastMath.max(1, explorationRuns));
    List<RunConfig> configs = new ArrayList<>(explorationRuns);
    for (int run = 0; run < explorationRuns; run++) {
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
      } else {
        startPoint = perturbGlobal(initialGuess, lower, upper, sigma, run);
        runSigma = new double[n];
        for (int i = 0; i < n; i++) {
          runSigma[i] = FastMath.min(sigma[i] * 1.5, (upper[i] - lower[i]) / 3.0);
        }
        populationSize = FastMath.min(basePopSize * 2, 100);
      }
      clampToBounds(startPoint, lower, upper);
      configs.add(new RunConfig(startPoint, runSigma, populationSize, parallelBudget));
    }

    // Start-point costs feed the descent half of the consensus test (one evaluation per run).
    double[] startCosts = new double[configs.size()];
    for (int i = 0; i < configs.size(); i++) {
      startCosts[i] = evaluateCost(configs.get(i).startPoint);
      totalEvals++;
      remainingEvals--;
    }
    double[] runCosts = new double[configs.size()];
    java.util.Arrays.fill(runCosts, Double.NaN);

    // Reserve one core for the JME render thread so the UI stays responsive while the optimizer
    // is running on the dedicated mission-optimizer thread.
    int availableForOptimizer = FastMath.max(1, Runtime.getRuntime().availableProcessors() - 1);
    int poolSize = FastMath.min(explorationRuns, availableForOptimizer);
    ExecutorService pool = Executors.newFixedThreadPool(poolSize);
    // Pre-draw a sub-seed per run sequentially so that the master seed deterministically reproduces
    // the full exploration phase even when runs execute in parallel.
    long[] runSeeds = new long[configs.size()];
    for (int i = 0; i < configs.size(); i++) {
      runSeeds[i] = rng.nextLong();
    }
    // Cross-run early stop: the first run to complete below the acceptable cost flips the flag
    // and every other run aborts at its next evaluation, returning its best-so-far. The phase
    // then costs the time of the first converged success instead of the slowest run — completion
    // (not first threshold crossing) so a run seeded below the acceptable cost still optimizes.
    AtomicBoolean crossRunStop = new AtomicBoolean(false);
    try {
      List<Future<CMAESRunExecutor.RunResult>> futures = new ArrayList<>(configs.size());
      for (int i = 0; i < configs.size(); i++) {
        RunConfig cfg = configs.get(i);
        long runSeed = runSeeds[i];
        futures.add(
            pool.submit(
                () ->
                    executor.execute(
                        cfg.startPoint,
                        cfg.runSigma,
                        cfg.populationSize,
                        cfg.budget,
                        true,
                        runSeed,
                        crossRunStop)));
      }
      for (int run = 0; run < futures.size(); run++) {
        try {
          CMAESRunExecutor.RunResult result = futures.get(run).get();
          totalEvals += result.evaluations();
          remainingEvals -= result.evaluations();
          logger.info(
              "Exploration {}/{}: cost={}, evals={}",
              run + 1,
              explorationRuns,
              result.bestCost(),
              result.evaluations());
          runCosts[run] = result.bestCost();
          if (result.bestCost() < bestCost) {
            bestCost = result.bestCost();
            bestVars = result.bestVariables().clone();
          }
        } catch (Exception e) {
          logger.warn("Exploration {} failed: {}", run + 1, e.getMessage());
          totalEvals += parallelBudget / 4;
          remainingEvals -= parallelBudget / 4;
        }
      }
    } finally {
      pool.shutdown();
    }
    if (bestVars != null && bestCost <= problem.getAcceptableCost()) {
      logger.info("Target reached during exploration: cost={}", bestCost);
    }

    // ── Consensus early-stop ─────────────────────────────────────────────
    if (bestVars != null && bestCost > problem.getAcceptableCost()) {
      int consensus = 0;
      for (int run = 0; run < runCosts.length; run++) {
        double cost = runCosts[run];
        if (Double.isNaN(cost)) {
          continue;
        }
        boolean descended =
            startCosts[run] - cost > CONSENSUS_DESCENT_RATIO * FastMath.abs(startCosts[run]);
        boolean agrees = FastMath.abs(cost - bestCost) <= CONSENSUS_RELATIVE_EPS * FastMath.abs(bestCost);
        if (descended && agrees) {
          consensus++;
        }
      }
      if (consensus >= CONSENSUS_MIN_RUNS) {
        logger.info(
            "Consensus: {} independent explorations descended to cost={} (within {} relative); "
                + "skipping refinement and remaining retries",
            consensus,
            bestCost,
            CONSENSUS_RELATIVE_EPS);
        return new SinglePassResult(bestVars, bestCost, totalEvals, true);
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
          double costBeforePass = bestCost;
          CMAESRunExecutor.RunResult result =
              executor.execute(
                  bestVars.clone(), refineSigma, basePopSize, budget, false, rng.nextLong(), null);
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
          if (!improvedMeaningfully(costBeforePass, bestCost)) {
            logger.info(
                "Refinement cascade stopped after pass {}: plateau at cost={}", pass + 1, bestCost);
            break;
          }
        } catch (Exception e) {
          logger.warn("Refinement pass {} failed: {}", pass + 1, e.getMessage());
        }
      }
    }

    return new SinglePassResult(bestVars, bestCost, totalEvals, false);
  }

  /** Cost of a single point, with the same exception penalty semantics as an optimization run. */
  private double evaluateCost(double[] point) {
    try {
      return problem.computeCost(problem.propagate(point));
    } catch (Exception e) {
      return CMAESRunExecutor.EXCEPTION_PENALTY_COST;
    }
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

  /** True when {@code after} improves on {@code before} by more than the stagnation epsilon. */
  private static boolean improvedMeaningfully(double before, double after) {
    return before - after > STAGNATION_RELATIVE_EPS * FastMath.abs(before);
  }

  private static boolean hasSaturatedParameter(double[] best, double[] lower, double[] upper) {
    return OptimizerDiagnostics.evaluateBounds(best, lower, upper).stream()
        .anyMatch(f -> f.lowSat() || f.highSat());
  }

  // ══════════════════════════════════════════════════════════════════════
  // Start point strategies
  // ══════════════════════════════════════════════════════════════════════

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
