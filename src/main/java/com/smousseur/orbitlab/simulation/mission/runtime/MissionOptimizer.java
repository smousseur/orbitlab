package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.CMAESTrajectoryOptimizer;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import java.util.LinkedHashMap;
import java.util.Map;

import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferTwoManeuverProblem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Orchestrates the sequential optimization of all stages in a {@link Mission}.
 *
 * <p>Iterates through the mission's stages in order. For each {@link OptimizableMissionStage}, it
 * builds the corresponding {@link TrajectoryProblem}, runs a {@link CMAESTrajectoryOptimizer}, and
 * advances the mission state with the optimal solution. Non-optimizable stages are propagated
 * directly.
 */
public class MissionOptimizer {
  private static final Logger logger = LogManager.getLogger(MissionOptimizer.class);

  private final Mission mission;
  private final int maxEvaluations;

  /**
   * Creates a mission optimizer with a default evaluation budget of 20,000 per stage.
   *
   * @param mission the mission whose stages will be optimized
   */
  public MissionOptimizer(Mission mission) {
    this(mission, 20_000);
  }

  /**
   * Creates a mission optimizer with a specified evaluation budget per stage.
   *
   * @param mission the mission whose stages will be optimized
   * @param maxEvaluations maximum number of objective function evaluations per optimizable stage
   */
  public MissionOptimizer(Mission mission, int maxEvaluations) {
    this.mission = mission;
    this.maxEvaluations = maxEvaluations;
  }

  /**
   * Optimizes all stages of the mission sequentially.
   *
   * <p>Each optimizable stage produces a trajectory problem that is solved via CMA-ES. The
   * resulting optimal parameters and spacecraft state are recorded and used to advance the mission.
   * Non-optimizable stages are propagated in standalone mode.
   *
   * @return the collected optimization results for all optimizable stages, keyed by stage name
   */
  public MissionComputeResult optimize() {
    Map<String, OptimizationResult> results = new LinkedHashMap<>();
    AbsoluteDate launchDate = mission.getCurrentState().getDate();

    for (MissionStage stage : mission.getStages()) {
      logger.info("Current mass = {}", mission.getCurrentState().getMass());
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
        if (problem instanceof TransferTwoManeuverProblem transferProblem) {
          TransferResult transferResult = transferProblem.getLastTransferResult();
          logger.info(
              "Post burn1 orbit: {}",
              transferResult != null ? transferResult.orbitPostBurn1() : null);
          TransfertTwoManeuver.ResolvedBurn2 burn =
              transferResult != null ? transferResult.resolvedBurn2() : null;
          logger.info("Transfert burn 2: {}", burn);
        }
        mission.setCurrentState(problem.propagate(result.bestVariables()));
      } else {
        logger.info("Propagating non-optimizable stage '{}'...", stage.getName());
        SpacecraftState propagated = stage.propagateStandalone(mission.getCurrentState(), mission);
        mission.setCurrentState(propagated);
        logger.info("Stage '{}' done.", stage.getName());
      }
    }

    // Inject optimization results into stages for replay
    MissionOptimizerResult optimResult = new MissionOptimizerResult(results);
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        optimResult
            .findFor(optimizable.optimizationKey())
            .ifPresent(optimizable::applyOptimization);
      }
    }

    // Generate the full ephemeris from the original launch date
    SpacecraftState initialState = mission.getInitialState(launchDate);
    MissionEphemerisGenerator generator = new MissionEphemerisGenerator();
    MissionEphemeris ephemeris = generator.generate(mission, initialState);

    mission.setStatus(MissionStatus.READY);
    return new MissionComputeResult(optimResult, ephemeris);
  }
}
