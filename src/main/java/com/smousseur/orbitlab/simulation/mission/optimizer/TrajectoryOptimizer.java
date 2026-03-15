package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.orekit.propagation.SpacecraftState;

/**
 * Strategy interface for trajectory optimization algorithms.
 *
 * <p>Implementations solve a {@link TrajectoryProblem} by searching for the parameter vector that
 * minimizes the cost function, subject to the problem's bounds and constraints.
 */
public interface TrajectoryOptimizer {
  /**
   * Runs the optimization and returns the best solution found.
   *
   * @return the optimization result containing the best parameter vector, cost, final spacecraft
   *     state, and evaluation count
   */
  OptimizationResult optimize();
}
