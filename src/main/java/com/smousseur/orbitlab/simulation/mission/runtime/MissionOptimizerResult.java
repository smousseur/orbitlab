package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;

import java.util.Map;
import java.util.Optional;

/** Holds all optimization results for a mission, keyed by stage optimization key. */
public record MissionOptimizerResult(Map<String, OptimizationResult> resultsByStageKey) {

  public Optional<OptimizationResult> findFor(String stageKey) {
    return Optional.ofNullable(resultsByStageKey.get(stageKey));
  }
}
