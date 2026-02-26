package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

public class MissionPlayer {
  private static final Logger logger = LogManager.getLogger(MissionPlayer.class);
  private final Mission mission;

  public MissionPlayer(Mission mission) {
    this.mission = mission;
  }

  /** Injects optimization results into optimizable stages, then starts the mission. */
  public void play(MissionOptimzerResult optimResult, AbsoluteDate startDate) {
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

    // Start the mission — stages will use their injected results in configure()
    // mission.start(startDate);
  }
}
