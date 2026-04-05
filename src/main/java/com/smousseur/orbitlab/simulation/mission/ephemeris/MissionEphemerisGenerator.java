package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;

/**
 * Generates the complete mission ephemeris by replaying all stages with their optimized parameters
 * injected. Uses Orekit numerical propagation with a fixed-step handler to sample the trajectory.
 */
public final class MissionEphemerisGenerator {
  private static final Logger logger = LogManager.getLogger(MissionEphemerisGenerator.class);

  private static final double DEFAULT_STEP_SECONDS = 1.0;
  private static final double DEFAULT_COAST_DURATION_SECONDS = 5400.0; // 90 min (one LEO orbit)

  /**
   * Re-propagates the mission from initialState through all stages, sampling the trajectory at
   * regular intervals.
   *
   * @param mission the mission with optimization results injected into stages
   * @param initialState the spacecraft state at T_start
   * @return the complete mission ephemeris
   */
  public MissionEphemeris generate(Mission mission, SpacecraftState initialState) {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    SpacecraftState currentState = initialState;

    List<MissionStage> stages = mission.getStages();
    for (int stageIdx = 0; stageIdx < stages.size(); stageIdx++) {
      MissionStage stage = stages.get(stageIdx);
      boolean isLastStage = (stageIdx == stages.size() - 1);

      logger.info("Generating ephemeris for stage '{}'", stage.getName());

      // Enter the stage
      currentState = stage.enter(currentState, mission);

      // If optimizable with saved entry state, use it for reproducibility
      if (stage instanceof OptimizableMissionStage<?> opt && opt.getEntryState() != null) {
        currentState = opt.getEntryState();
      }

      mission.setCurrentState(currentState);

      // Create and configure propagator
      NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
      propagator.setInitialState(currentState);
      stage.configure(propagator, mission);

      // Collect samples via fixed-step handler
      String stageName = stage.getName();
      propagator.getMultiplexer().add(DEFAULT_STEP_SECONDS, (OrekitFixedStepHandler) state -> {
        Vector3D pos = state.getPosition();
        Vector3D vel = state.getPVCoordinates().getVelocity();
        double alt = mission.computeAltitudeMeters(state);
        points.add(
            new MissionEphemerisPoint(
                state.getDate(), pos, vel, stageName, state.getMass(), alt));
      });

      // Propagate to the exact end date configured by the stage. Using the precise end date
      // avoids numerical issues where adaptive-step integrators might miss ConstantThrustManeuver
      // boundary events when propagating far past the actual stage duration.
      AbsoluteDate endDate;
      if (isLastStage) {
        endDate = currentState.getDate().shiftedBy(DEFAULT_COAST_DURATION_SECONDS);
      } else if (stage.getConfiguredEndDate() != null) {
        endDate = stage.getConfiguredEndDate();
      } else {
        endDate = currentState.getDate().shiftedBy(7200.0); // fallback safety
      }

      SpacecraftState finalState;
      try {
        finalState = propagator.propagate(endDate);
      } catch (Exception e) {
        logger.warn("Propagation failed for stage '{}': {}", stage.getName(), e.getMessage());
        finalState = mission.getCurrentState();
      }

      // Add the final state of this stage as a sample point
      Vector3D pos = finalState.getPosition();
      Vector3D vel = finalState.getPVCoordinates().getVelocity();
      double alt = mission.computeAltitudeMeters(finalState);
      points.add(
          new MissionEphemerisPoint(
              finalState.getDate(), pos, vel, stageName, finalState.getMass(), alt));

      currentState = finalState;
      mission.setCurrentState(currentState);

      logger.info(
          "Stage '{}': {} points, ended at {}",
          stage.getName(),
          points.size(),
          finalState.getDate());
    }

    logger.info("Total ephemeris points: {}", points.size());
    return new MissionEphemeris(points);
  }
}
