package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveStatus;
import org.hipparchus.linear.Array2DRowRealMatrix;
import org.hipparchus.linear.ArrayRealVector;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.RealVector;
import org.hipparchus.optim.SimpleVectorValueChecker;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresBuilder;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresProblem;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LevenbergMarquardtOptimizer;
import org.hipparchus.util.Pair;
import org.orekit.propagation.SpacecraftState;

/**
 * Generic trajectory optimizer using nonlinear least squares.
 *
 * <p>Does not know the physics of the problem: delegates propagation to a {@link TrajectoryProblem}
 * and residual evaluation to a {@link MissionObjective}.
 */
public class TrajectoryOptimizer {
  private final TrajectoryProblem problem;
  private final MissionObjective objective;

  private int maxEvaluations = 500;
  private int maxIterations = 100;
  private double jacobianStep = 5e-4;
  private double tolerance = 1e-2;

  public TrajectoryOptimizer(TrajectoryProblem problem, MissionObjective objective) {
    this.problem = problem;
    this.objective = objective;
  }

  /** Configure limits of the optimizer. */
  public TrajectoryOptimizer withMaxEvaluations(int maxEvaluations) {
    this.maxEvaluations = maxEvaluations;
    return this;
  }

  public TrajectoryOptimizer withMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }

  public TrajectoryOptimizer withJacobianStep(double step) {
    this.jacobianStep = step;
    return this;
  }

  public TrajectoryOptimizer withTolerance(double tolerance) {
    this.tolerance = tolerance;
    return this;
  }

  /**
   * Finds the optimal thrust profile.
   *
   * @return the optimal variables in the unconstrained space
   */
  public OptimizationResult optimize() {
    LevenbergMarquardtOptimizer lm = new LevenbergMarquardtOptimizer();
    ObjectiveStatus status = new ObjectiveStatus();

    double[] guess = problem.buildInitialGuess();
    double[] guessPhysical = problem.toPhysical(problem.buildInitialGuess());
    System.out.printf(
        "Guess physical: burn1 at t=%.0fs dur=%.0fs, burn2 at t=%.0fs dur=%.0fs%n",
        guessPhysical[0], guessPhysical[1], guessPhysical[5], guessPhysical[6]);

    int n = guess.length;

    // Évaluer le guess pour : connaître la dimension des résidus + diagnostic
    SpacecraftState guessState = problem.propagate(guess);
    objective.evaluate(status, guessState);
    int m = status.getError().length;

    LeastSquaresProblem lsp =
        new LeastSquaresBuilder()
            .start(guess)
            .model(params -> evaluateWithJacobian(params.toArray(), status, m, n))
            .target(new double[m])
            .checkerPair(new SimpleVectorValueChecker(tolerance, tolerance))
            .lazyEvaluation(false)
            .maxEvaluations(maxEvaluations)
            .maxIterations(maxIterations)
            .build();

    double[] optimizedVars = lm.optimize(lsp).getPoint().toArray();
    double[] physicalParams = problem.toPhysical(optimizedVars);
    // Évaluer l'état final
    SpacecraftState finalState = problem.propagate(optimizedVars);
    objective.evaluate(status, finalState);

    double[] physicalVars = problem.toPhysical(optimizedVars);
    return new OptimizationResult(
        optimizedVars, physicalParams, finalState, status.getError().clone());
  }

  /** Evaluates the residuals and the Jacobian using centered finite differences. */
  private Pair<RealVector, RealMatrix> evaluateWithJacobian(
      double[] x, ObjectiveStatus status, int m, int n) {

    SpacecraftState state = problem.propagate(x);
    objective.evaluate(status, state);
    double[] residuals = status.getError().clone();

    double[][] jac = new double[m][n];
    double h = jacobianStep;

    for (int j = 0; j < n; j++) {
      double[] xPlus = x.clone();
      xPlus[j] += h;

      objective.evaluate(status, problem.propagate(xPlus));
      double[] errPlus = status.getError().clone();

      for (int i = 0; i < m; i++) {
        jac[i][j] = (errPlus[i] - residuals[i]) / h;
      }
    }

    return new Pair<>(new ArrayRealVector(residuals), new Array2DRowRealMatrix(jac));
  }

  /** Result of the optimization. */
  public record OptimizationResult(
      double[] variables, // espace non contraint (pour diagnostic)
      double[] physicalParams, // paramètres physiques (pour utilisation)
      SpacecraftState finalState,
      double[] residuals) {}
}
