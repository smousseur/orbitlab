package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.CMAESTrajectoryOptimizer;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferTwoManeuverProblem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

import java.util.LinkedHashMap;
import java.util.Map;

public class MissionOptimizer {
  private static final Logger logger = LogManager.getLogger(MissionOptimizer.class);

  private final Mission mission;
  private final int maxEvaluations;

  public MissionOptimizer(Mission mission) {
    this(mission, 20_000);
  }

  public MissionOptimizer(Mission mission, int maxEvaluations) {
    this.mission = mission;
    this.maxEvaluations = maxEvaluations;
  }

  public MissionOptimzerResult optimize() {
    Map<String, OptimizationResult> results = new LinkedHashMap<>();

    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        logger.info("Optimizing stage '{}'...", stage.getName());

        TrajectoryProblem problem = optimizable.buildProblem(mission);
        CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, maxEvaluations);
        OptimizationResult result = optimizer.optimize();

        results.put(optimizable.optimizationKey(), result);

        if (problem instanceof TransferTwoManeuverProblem transferProblem) {
          transferProblem.enableCostLogging();
          transferProblem.computeCost(problem.propagate(result.bestVariables()));
        }

        logger.info(
            "Stage '{}' optimized: cost={}, values={}, evaluations={}",
            stage.getName(),
            result.bestCost(),
            result.bestVariables(),
            result.evaluations());
        mission.setCurrentState(problem.propagate(result.bestVariables()));

      } else {
        logger.info("Propagating non-optimizable stage '{}'...", stage.getName());
        SpacecraftState propagated = stage.propagateStandalone(mission.getCurrentState(), mission);
        mission.setCurrentState(propagated);
        logger.info("Stage '{}' done.", stage.getName());
      }
    }

    return new MissionOptimzerResult(results);
  }
}
