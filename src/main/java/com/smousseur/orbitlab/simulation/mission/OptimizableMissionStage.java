package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import org.orekit.propagation.SpacecraftState;

/**
 * Marks a {@link MissionStage} that requires parameter optimization before execution.
 *
 * @param <P> the type of trajectory problem this stage produces
 */
public interface OptimizableMissionStage<P extends TrajectoryProblem> {
  /** Builds the optimization problem for this stage, given the current mission context. */
  P buildProblem(Mission mission);

  /** Stable key used to store/retrieve the optimization result for this stage. */
  String optimizationKey();

  /** Injects the optimization result before execution. */
  void applyOptimization(OptimizationResult result);

  /**
   * Retrieves the entry {@link SpacecraftState} of the current stage, if available. This state is
   * primarily used to ensure reproducibility for stages requiring optimization.
   *
   * @return the entry {@link SpacecraftState} of the current stage, or {@code null} if no specific
   *     entry state is defined.
   */
  default SpacecraftState getEntryState() {
    return null;
  }
}
