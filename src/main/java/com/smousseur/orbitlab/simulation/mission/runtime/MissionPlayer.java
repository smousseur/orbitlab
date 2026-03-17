package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Replays a pre-optimized mission by injecting optimization results into the mission's stages.
 *
 * <p>The player takes a {@link MissionOptimizerResult} produced by {@link MissionOptimizer} and
 * pushes each stage's optimal parameters into the corresponding {@link OptimizableMissionStage},
 * enabling deterministic execution of the optimized trajectory.
 */
public class MissionPlayer {
  private static final Logger logger = LogManager.getLogger(MissionPlayer.class);
  private final Mission mission;

  /**
   * Creates a mission player for the given mission.
   *
   * @param mission the mission to replay with optimized parameters
   */
  public MissionPlayer(Mission mission) {
    this.mission = mission;
  }

  /**
   * Injects optimization results into optimizable stages and prepares the mission for execution.
   *
   * <p>Each optimizable stage must have a corresponding entry in the optimization result. If a
   * result is missing for any optimizable stage, an {@link IllegalStateException} is thrown.
   *
   * @param optimResult the optimization results to inject into the mission stages
   * @param startDate the mission start date
   * @throws IllegalStateException if an optimizable stage has no corresponding optimization result
   */
  public void play(MissionOptimizerResult optimResult, AbsoluteDate startDate) {
    // Push optimization results into stages that need them
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        optimResult
            .findFor(optimizable.optimizationKey())
            .ifPresentOrElse(
                result -> {
                  optimizable.applyOptimization(result);
                  logger.info("Applied optimization for stage '{}'", stage.getName());
                },
                () -> {
                  throw new IllegalStateException(
                      "Missing optimization result for stage '" + stage.getName() + "'");
                });
      }
    }

    // Stages will use their injected optimization results when configure() is called.
  }
}
