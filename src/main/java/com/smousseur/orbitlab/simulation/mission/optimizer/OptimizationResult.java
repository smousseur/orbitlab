package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.orekit.propagation.SpacecraftState;

/** Result of a trajectory optimization run. */
public record OptimizationResult(
    double[] bestVariables,
    double bestCost,
    SpacecraftState bestState,
    int evaluations,
    SpacecraftState stageEntryState) {

  public OptimizationResult(
      double[] bestVariables, double bestCost, SpacecraftState bestState, int evaluations) {
    this(bestVariables.clone(), bestCost, bestState, evaluations, null);
  }

  /** Optimal parameter vector. */
  public double[] bestVariables() {
    return bestVariables.clone();
  }
}
