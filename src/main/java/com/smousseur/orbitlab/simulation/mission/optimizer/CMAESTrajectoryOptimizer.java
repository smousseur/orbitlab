package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.analysis.MultivariateFunction;
import org.hipparchus.optim.*;
import org.hipparchus.optim.nonlinear.scalar.GoalType;
import org.hipparchus.optim.nonlinear.scalar.ObjectiveFunction;
import org.hipparchus.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.hipparchus.random.MersenneTwister;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;

public class CMAESTrajectoryOptimizer implements TrajectoryOptimizer {
  private static final Logger logger = LogManager.getLogger(CMAESTrajectoryOptimizer.class);

  public static final double EXCEPTION_PENALTY_COST = 1e10;

  private final TrajectoryProblem problem;
  private final int maxEvaluations;
  private final int numRestarts;
  private final double stopFitness;
  private final double relativeTolerance;
  private final double absoluteTolerance;

  public CMAESTrajectoryOptimizer(TrajectoryProblem problem, int maxEvaluations) {
    this(problem, maxEvaluations, 5, 1e-6, 1e-4, 1e-6);
  }

  public CMAESTrajectoryOptimizer(
      TrajectoryProblem problem,
      int maxEvaluations,
      int numRestarts,
      double stopFitness,
      double relativeTolerance,
      double absoluteTolerance) {
    this.problem = problem;
    this.maxEvaluations = maxEvaluations;
    this.numRestarts = numRestarts;
    this.stopFitness = stopFitness;
    this.relativeTolerance = relativeTolerance;
    this.absoluteTolerance = absoluteTolerance;
  }

  @Override
  public OptimizationResult optimize() {
    double[] globalBestVars = null;
    double globalBestCost = Double.MAX_VALUE;
    int totalEvaluations = 0;

    int evalsPerRun = maxEvaluations / (numRestarts + 1); // +1 for the refinement run
    double[] initialGuess = problem.buildInitialGuess();
    double[] lower = problem.getLowerBounds();
    double[] upper = problem.getUpperBounds();
    double[] sigma = problem.getInitialSigma();
    int n = problem.getNumVariables();

    // ── Phase 1: Exploration with restarts ──
    for (int run = 0; run < numRestarts; run++) {
      double[] startPoint;
      double[] runSigma;
      int populationSize;

      if (run == 0) {
        startPoint = initialGuess.clone();
        runSigma = sigma.clone();
        populationSize = 4 + (int) (3 * FastMath.log(n));
      } else {
        populationSize = (int) ((4 + (int) (3 * FastMath.log(n))) * FastMath.pow(2, run));
        populationSize = FastMath.min(populationSize, 200);

        double[] base = (globalBestVars != null) ? globalBestVars : initialGuess;
        startPoint = perturbStartPoint(base, lower, upper, sigma, run);
        runSigma = new double[n];
        for (int i = 0; i < n; i++) {
          runSigma[i] = FastMath.min(sigma[i] * (1.0 + 0.5 * run), (upper[i] - lower[i]) / 4.0);
        }
      }

      for (int i = 0; i < n; i++) {
        startPoint[i] = FastMath.max(lower[i], FastMath.min(upper[i], startPoint[i]));
      }

      try {
        RunResult result = runSingleCMAES(startPoint, runSigma, populationSize, evalsPerRun);
        totalEvaluations += result.evaluations;

        logger.info(
            "Exploration {}/{}: cost={}, evals={}",
            run + 1,
            numRestarts,
            result.bestCost,
            result.evaluations);

        if (result.bestCost < globalBestCost) {
          globalBestCost = result.bestCost;
          globalBestVars = result.bestVariables.clone();
        }

        if (globalBestCost < stopFitness) {
          logger.info("Early stop: cost {} < stopFitness {}", globalBestCost, stopFitness);
          break;
        }
      } catch (Exception e) {
        logger.warn("Run {} failed: {}", run + 1, e.getMessage());
        totalEvaluations += evalsPerRun;
      }
    }

    // ── Phase 2: Local refinement around best found ──
    if (globalBestVars != null && globalBestCost > stopFitness) {
      logger.info("Refinement phase starting from cost={}", globalBestCost);
      double[] refineSigma = new double[n];
      for (int i = 0; i < n; i++) {
        refineSigma[i] = sigma[i] * 0.1; // 10% of initial sigma → fine-grained search
      }
      try {
        int refinePopSize = 4 + (int) (3 * FastMath.log(n));
        RunResult refineResult =
            runSingleCMAES(globalBestVars.clone(), refineSigma, refinePopSize, evalsPerRun);
        totalEvaluations += refineResult.evaluations;

        logger.info(
            "Refinement: cost={}, evals={}", refineResult.bestCost, refineResult.evaluations);

        if (refineResult.bestCost < globalBestCost) {
          globalBestCost = refineResult.bestCost;
          globalBestVars = refineResult.bestVariables.clone();
        }
      } catch (Exception e) {
        logger.warn("Refinement failed: {}", e.getMessage());
      }
    }

    SpacecraftState bestState = problem.propagate(globalBestVars);
    logger.info("Final best cost={}, total evals={}", globalBestCost, totalEvaluations);
    return new OptimizationResult(globalBestVars, globalBestCost, bestState, totalEvaluations);
  }

  private double[] perturbStartPoint(
      double[] base, double[] lower, double[] upper, double[] sigma, int run) {
    MersenneTwister rng = new MersenneTwister(run * 42L + System.nanoTime());
    double[] perturbed = new double[base.length];
    for (int i = 0; i < base.length; i++) {
      double range = upper[i] - lower[i];
      // Mix between local perturbation and full-range exploration
      double localPert = base[i] + rng.nextGaussian() * sigma[i] * (1.0 + run);
      double globalPert = lower[i] + rng.nextDouble() * range;
      double mixRatio = FastMath.min(0.3 * run, 0.8); // later runs = more global
      perturbed[i] = (1.0 - mixRatio) * localPert + mixRatio * globalPert;
    }
    return perturbed;
  }

  private record RunResult(double[] bestVariables, double bestCost, int evaluations) {}

  private RunResult runSingleCMAES(
      double[] startPoint, double[] sigma, int populationSize, int maxEvals) {

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
            return EXCEPTION_PENALTY_COST;
          }
        };

    ConvergenceChecker<PointValuePair> runChecker =
        new ConvergenceChecker<>() {
          private int iterationCount = 0;

          @Override
          public boolean converged(int iteration, PointValuePair previous, PointValuePair current) {
            iterationCount++;
            if (iterationCount < 100) return false;
            double diff = FastMath.abs(previous.getValue() - current.getValue());
            return diff <= absoluteTolerance
                || diff <= relativeTolerance * FastMath.abs(current.getValue());
          }
        };

    CMAESOptimizer optimizer =
        new CMAESOptimizer(
            maxEvals, stopFitness, true, 0, 0, new MersenneTwister(), false, runChecker);

    optimizer.optimize(
        new MaxEval(maxEvals),
        new ObjectiveFunction(objectiveFunction),
        GoalType.MINIMIZE,
        new InitialGuess(startPoint),
        new CMAESOptimizer.Sigma(sigma),
        new CMAESOptimizer.PopulationSize(populationSize),
        new SimpleBounds(problem.getLowerBounds(), problem.getUpperBounds()));

    return new RunResult(runBestVars, runBestCostHolder[0], optimizer.getEvaluations());
  }
}
