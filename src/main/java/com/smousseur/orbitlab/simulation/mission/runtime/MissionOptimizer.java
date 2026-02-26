package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.CMAESTrajectoryOptimizer;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem;
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

        // Store the entry state so the runtime can start from exactly the same point
        SpacecraftState entryState = mission.getCurrentState();
        result =
            new OptimizationResult(
                result.bestVariables(),
                result.bestCost(),
                result.bestState(),
                result.evaluations(),
                entryState);
        results.put(optimizable.optimizationKey(), result);
        logger.info(
            "Stage '{}' optimized: cost={}, values={}, evaluations={}",
            stage.getName(),
            result.bestCost(),
            result.bestVariables(),
            result.evaluations());
        mission.setCurrentState(problem.propagate(result.bestVariables()));
        SpacecraftState st = mission.getCurrentState();
        logger.info(">>> Optimizer state after '{}' at t={}", stage.getName(), st.getDate());
        logger.info(
            "    pos={} vel={} mass={}",
            st.getPVCoordinates().getPosition(),
            st.getPVCoordinates().getVelocity(),
            st.getMass());
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
