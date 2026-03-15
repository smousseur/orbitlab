package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.orekit.propagation.SpacecraftState;

/**
 * Result of a trajectory optimization run.
 *
 * @param bestVariables the optimal parameter vector found by the optimizer
 * @param bestCost the lowest cost value achieved
 * @param bestState the spacecraft state resulting from propagation with the optimal parameters
 * @param evaluations the total number of objective function evaluations performed
 * @param stageEntryState the spacecraft state at stage entry, used for reproducible replay; may be
 *     {@code null}
 */
public record OptimizationResult(
    double[] bestVariables,
    double bestCost,
    SpacecraftState bestState,
    int evaluations,
    SpacecraftState stageEntryState) {

  /**
   * Creates an optimization result without a stage entry state.
   *
   * @param bestVariables the optimal parameter vector
   * @param bestCost the lowest cost value achieved
   * @param bestState the spacecraft state at the optimal solution
   * @param evaluations the total number of evaluations performed
   */
  public OptimizationResult(
      double[] bestVariables, double bestCost, SpacecraftState bestState, int evaluations) {
    this(bestVariables.clone(), bestCost, bestState, evaluations, null);
  }

  /** Optimal parameter vector. */
  public double[] bestVariables() {
    return bestVariables.clone();
  }
}
