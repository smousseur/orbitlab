package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.orekit.propagation.SpacecraftState;

/**
 * Defines a trajectory problem for the optimizer.
 *
 * <p>Encapsulates everything specific to a maneuver type: the initial guess, variable encoding,
 * propagator configuration, force models, and propagation error handling.
 *
 * <p>Contract of {@link #propagate(double[])}: never throws an exception, always returns a
 * SpacecraftState (possibly a fallback state far from the target).
 */
public interface TrajectoryProblem {

  /**
   * Gets acceptable threshold below which we consider the solution acceptable
   *
   * @return the acceptable cost
   */
  default double getAcceptableCost() {
    return 0.1;
  }

  /** Total number of optimization variables. For a multi-arc problem: numArcs × paramsPerArc. */
  int getNumVariables();

  /**
   * Builds the initial guess in the unconstrained space. The returned vector has exactly {@link
   * #getNumVariables()} elements.
   *
   * @return initial variables (unconstrained space, values from -∞ to +∞)
   */
  double[] buildInitialGuess();

  /**
   * Returns a pure analytical seed (e.g., Hohmann transfer) used by the optimizer to force at least
   * one exploration run to start from the closed-form physical solution. Returns {@code null} when
   * no analytical solution is available for this problem.
   *
   * @return the analytical parameter vector, or {@code null}
   */
  default double[] buildAnalyticalSeed() {
    return null;
  }

  /** Lower bounds for each parameter. */
  double[] getLowerBounds();

  /** Upper bounds for each parameter. */
  double[] getUpperBounds();

  /** Initial standard deviation (sigma) for each parameter (CMA-ES exploration). */
  double[] getInitialSigma();

  /**
   * Propagates the trajectory from the initial state using the given variables.
   *
   * <p>Responsibilities:
   *
   * <ul>
   *   <li>Decode unconstrained variables into physical parameters
   *   <li>Configure the numerical propagator
   *   <li>Add force models (gravity, thrust, etc.)
   *   <li>Propagate to the target date
   *   <li>In case of error: return a penalizing fallback state
   * </ul>
   *
   * @param variables vector in the unconstrained space
   * @return final spacecraft state (never null, never throws an exception)
   */
  SpacecraftState propagate(double[] variables);

  /**
   * Compute the scalar cost from the final state.
   *
   * @param state the final state
   * @return 0.0 for a perfect solution.
   */
  double computeCost(SpacecraftState state);
}
