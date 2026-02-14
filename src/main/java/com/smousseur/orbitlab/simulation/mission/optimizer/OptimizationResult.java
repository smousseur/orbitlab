package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.orekit.propagation.SpacecraftState;

/** Result of a trajectory optimization run. */
public record OptimizationResult(
    double[] bestVariables, double bestCost, SpacecraftState bestState, int evaluations) {

  public OptimizationResult(
      double[] bestVariables, double bestCost, SpacecraftState bestState, int evaluations) {
    this.bestVariables = bestVariables.clone();
    this.bestCost = bestCost;
    this.bestState = bestState;
    this.evaluations = evaluations;
  }

  /** Optimal parameter vector. */
  @Override
  public double[] bestVariables() {
    return bestVariables.clone();
  }
}
