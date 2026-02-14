package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.orekit.propagation.SpacecraftState;

public interface TrajectoryOptimizer {
  /**
   * Run the optimization on the given problem. Returns the result (best vector, best cost, final
   * state).
   */
  OptimizationResult optimize();
}
