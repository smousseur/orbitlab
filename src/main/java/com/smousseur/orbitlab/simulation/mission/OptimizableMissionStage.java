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

  /**
   * Whether this stage's problem propagates a <b>chain</b> of stages that the mission loop will
   * itself replay — the case of the ascent, optimized as a whole across {@code Gravity turn (S1) →
   * S1 separation → Gravity turn (S2)} (spec {@code
   * docs/mission-stages/01-separations-implicites.md} §5.6).
   *
   * <p>When true, {@code MissionOptimizer} must not advance the mission state from {@code
   * problem.propagate()} — doing so would fly the following phases twice. It instead injects the
   * result immediately (the following phases read the plan it publishes) and advances phase by
   * phase, as it does for a non-optimizable stage.
   *
   * @return {@code false} for a stage whose problem propagates only itself, the default
   */
  default boolean advancesByReplay() {
    return false;
  }
}
