package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.hipparchus.analysis.MultivariateFunction;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.optim.*;
import org.hipparchus.optim.nonlinear.scalar.GoalType;
import org.hipparchus.optim.nonlinear.scalar.ObjectiveFunction;
import org.hipparchus.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.hipparchus.random.MersenneTwister;
import org.hipparchus.util.FastMath;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.utils.Constants;

public class CMAESTrajectoryOptimizer implements TrajectoryOptimizer {
  private static final double PENALTY_COST = Double.MAX_VALUE;

  private final TrajectoryProblem problem;
  private final int maxEvaluations;
  private final double stopFitness;
  private final double relativeTolerance;
  private final double absoluteTolerance;

  // Best candidate tracking
  private double[] globalBestVariables;
  private double globalBestCost = Double.MAX_VALUE;

  public CMAESTrajectoryOptimizer(TrajectoryProblem problem, int maxEvaluations) {
    this(problem, maxEvaluations, 1e-6, 1e-6, 1e-8);
  }

  public CMAESTrajectoryOptimizer(
      TrajectoryProblem problem,
      int maxEvaluations,
      double stopFitness,
      double relativeTolerance,
      double absoluteTolerance) {
    this.problem = problem;
    this.maxEvaluations = 5000;
    this.stopFitness = stopFitness;
    this.relativeTolerance = relativeTolerance;
    this.absoluteTolerance = absoluteTolerance;
  }

  @Override
  public OptimizationResult optimize() {

    // 1. Wrap the problem as a MultivariateFunction for Commons Math
    MultivariateFunction objectiveFunction = this::evaluateCandidate;

    int n = problem.getNumVariables();
    int populationSize = 20; // 4 + (int) (3 * Math.log(n)); // CMA-ES default heuristic

    // 2. Build the CMA-ES optimizer
    CMAESOptimizer optimizer =
        new CMAESOptimizer(
            maxEvaluations,
            stopFitness,
            true,
            0, // diagonalOnly — 0 = full covariance
            0,
            new MersenneTwister(),
            false,
            checker);

    // 3. Configure and launch
    PointValuePair result =
        optimizer.optimize(
            new MaxEval(maxEvaluations),
            new ObjectiveFunction(objectiveFunction),
            GoalType.MINIMIZE,
            new InitialGuess(problem.buildInitialGuess()),
            new CMAESOptimizer.Sigma(problem.getInitialSigma()),
            new CMAESOptimizer.PopulationSize(populationSize),
            new SimpleBounds(problem.getLowerBounds(), problem.getUpperBounds()));

    // 4. Build result with final state
    double[] bestVariables = globalBestVariables.clone();
    double bestCost = globalBestCost;

    SpacecraftState bestState = problem.propagate(bestVariables);

    return new OptimizationResult(bestVariables, bestCost, bestState, optimizer.getEvaluations());
  }

  private double evaluateCandidate(double[] candidate) {
    try {
      SpacecraftState state = problem.propagate(candidate);
      double cost = problem.computeCost(state);
      if (Double.isNaN(cost) || Double.isInfinite(cost)) {
        // System.out.println("[EVAL] NaN/Inf cost for candidate");
        return PENALTY_COST;
      }
      // Track global best
      if (cost < globalBestCost) {
        globalBestCost = cost;
        globalBestVariables = candidate.clone();
      }
      return cost;
    } catch (Exception e) {
      return 1e10;
    }
  }

  ConvergenceChecker<PointValuePair> checker =
      new ConvergenceChecker<>() {
        private int iterationCount = 0;
        private static final int MIN_ITERATIONS = 100;

        @Override
        public boolean converged(int iteration, PointValuePair previous, PointValuePair current) {
          iterationCount++;
          if (iterationCount < MIN_ITERATIONS) {
            return false; // force at least MIN_ITERATIONS generations
          }
          double prevCost = previous.getValue();
          double currCost = current.getValue();
          double diff = FastMath.abs(prevCost - currCost);
          return diff <= absoluteTolerance || diff <= relativeTolerance * FastMath.abs(currCost);
        }
      };
}
