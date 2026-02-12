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
   * Decodes unconstrained variables into readable physical parameters. Useful for diagnostics and
   * result display.
   */
  double[] toPhysical(double[] variables);
}
