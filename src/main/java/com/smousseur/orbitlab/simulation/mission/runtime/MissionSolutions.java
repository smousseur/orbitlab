package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What an optimization found, in the only form a replay needs: one solved vector per stage key,
 * plus the launcher loads that were <em>searched</em> rather than derived (spec {@code
 * docs/scenario/01-persistance-missions.md} §5).
 *
 * <p>This is the domain twin of {@code ScenarioSolution}, which carries the same pair. The
 * duplication is deliberate and not redundancy: one is a file format that must not break, the other
 * is a type the optimization core owns. {@code ScenarioSession} is the single place that puts them
 * face to face.
 *
 * @param vectors the solved variables per {@link OptimizableMissionStage#optimizationKey()}
 * @param launcherLoads the per-stage launcher loads actually flown (kg), or {@code null} outside
 *     {@code PRECISE}, where the loads are derived and {@code PropellantBudget} recomputes them
 */
public record MissionSolutions(Map<String, double[]> vectors, double[] launcherLoads) {

  public MissionSolutions {
    Objects.requireNonNull(vectors, "vectors");
    vectors = new LinkedHashMap<>(vectors);
  }

  /**
   * @return {@code true} when the flown launcher loads are known and must be applied before flying
   */
  public boolean hasLauncherLoads() {
    return launcherLoads != null && launcherLoads.length > 0;
  }

  /**
   * @param stageKey the stage key to look up
   * @return the vector to fly that stage at, or {@code null} when this set does not carry it
   */
  public double[] vectorFor(String stageKey) {
    double[] vector = vectors.get(stageKey);
    return vector == null ? null : vector.clone();
  }

  /**
   * Whether these solutions describe <b>exactly</b> the composition of {@code mission}.
   *
   * <p>The replay is all or nothing (§5.1). A missing key would leave one stage to be optimized
   * beside stages that were replayed, producing a trajectory nobody asked for and nothing would
   * report; a surplus key means the file describes stages this composition no longer has, which is
   * the same mismatch seen from the other side. Either way the answer is to fall back on an
   * ordinary optimization, not to fly half a memory.
   *
   * @param mission the composed mission the replay would fly
   * @return {@code true} when every optimizable stage has its vector, and no vector is left over
   */
  public boolean covers(Mission mission) {
    Objects.requireNonNull(mission, "mission");
    Set<String> stageKeys =
        mission.getStages().stream()
            .filter(OptimizableMissionStage.class::isInstance)
            .map(stage -> ((OptimizableMissionStage<?>) stage).optimizationKey())
            .collect(Collectors.toSet());
    return stageKeys.equals(vectors.keySet());
  }

  /**
   * Reads the solved vectors off a completed optimization.
   *
   * @param result the optimizer result to project
   * @param launcherLoads the loads actually flown (kg), or {@code null} outside {@code PRECISE}
   * @return the replayable projection of that result
   */
  public static MissionSolutions from(MissionOptimizerResult result, double[] launcherLoads) {
    Objects.requireNonNull(result, "result");
    Map<String, double[]> vectors = new LinkedHashMap<>();
    result.resultsByStageKey().forEach((key, value) -> vectors.put(key, value.bestVariables()));
    return new MissionSolutions(vectors, launcherLoads);
  }
}
