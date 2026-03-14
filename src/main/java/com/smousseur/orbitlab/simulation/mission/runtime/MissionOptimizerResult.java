package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;

import java.util.Map;
import java.util.Optional;

/**
 * Holds all optimization results for a mission, keyed by stage optimization key.
 *
 * @param resultsByStageKey map from stage optimization key to the corresponding optimization result
 */
public record MissionOptimizerResult(Map<String, OptimizationResult> resultsByStageKey) {

  /**
   * Looks up the optimization result for a given stage.
   *
   * @param stageKey the optimization key of the stage
   * @return an {@link Optional} containing the result if found, or empty if no result exists for
   *     the given key
   */
  public Optional<OptimizationResult> findFor(String stageKey) {
    return Optional.ofNullable(resultsByStageKey.get(stageKey));
  }
}
