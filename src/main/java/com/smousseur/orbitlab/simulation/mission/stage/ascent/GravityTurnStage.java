package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class GravityTurnStage extends MissionStage
    implements OptimizableMissionStage<GravityTurnProblem> {

  private final double targetInclination;
  private final double ascensionDuration;
  private final double pitchKickAngle;
  private final double launchLatitude;
  private final GravityTurnConstraints constraints;

  private OptimizationResult optimizationResult;

  public GravityTurnStage(
      String name,
      double ascensionDuration,
      double pitchKickAngle,
      GravityTurnConstraints constraints) {
    this(name, ascensionDuration, pitchKickAngle, 0.0, 0.0, constraints);
  }

  public GravityTurnStage(
      String name,
      double ascensionDuration,
      double pitchKickAngle,
      double launchLatitude,
      double targetInclination,
      GravityTurnConstraints constraints) {
    super(name);
    this.ascensionDuration = ascensionDuration;
    this.pitchKickAngle = pitchKickAngle;
    this.launchLatitude = launchLatitude;
    this.targetInclination = targetInclination;
    this.constraints = constraints;
  }

  @Override
  public String optimizationKey() {
    return getName();
  }

  @Override
  public void applyOptimization(OptimizationResult result) {
    this.optimizationResult = result;
  }

  @Override
  public GravityTurnProblem buildProblem(Mission mission) {
    GravityTurnManeuver maneuver = createManeuver(mission);
    return new GravityTurnProblem(maneuver, mission.getCurrentState(), constraints);
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    GravityTurnManeuver maneuver = createManeuver(mission);
    return maneuver.applyKick(previousState);
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "GravityTurnStage '" + getName() + "' requires optimization before execution");
    }

    GravityTurnManeuver maneuver = createManeuver(mission);
    GravityTurnManeuver.GravityTurnParams params =
        maneuver.decode(optimizationResult.bestVariables());

    SpacecraftState state = mission.getCurrentState();
    maneuver.configure(propagator, state, params);

    // MECO event → transition to next stage
    AbsoluteDate mecoDate = state.getDate().shiftedBy(params.remainingBurnTime());
    propagator.addEventDetector(
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  private GravityTurnManeuver createManeuver(Mission mission) {
    Vehicle vehicle = mission.getVehicle();
    double launchAzimuth = Physics.getLaunchAzimuth(launchLatitude, targetInclination);
    return new GravityTurnManeuver(
        vehicle, Math.toRadians(pitchKickAngle), launchAzimuth, ascensionDuration);
  }
}
