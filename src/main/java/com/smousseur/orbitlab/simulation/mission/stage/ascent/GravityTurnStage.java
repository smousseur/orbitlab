package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
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
  private final double pitchKickAngle;
  private final double interstageCoastDuration;
  private final double launchLatitude;
  private final GravityTurnConstraints constraints;

  private OptimizationResult optimizationResult;

  public GravityTurnStage(
      String name,
      double pitchKickAngle,
      double interstageCoastDuration,
      GravityTurnConstraints constraints) {
    this(name, pitchKickAngle, interstageCoastDuration, 0.0, 0.0, constraints);
  }

  public GravityTurnStage(
      String name,
      double pitchKickAngle,
      double interstageCoastDuration,
      double launchLatitude,
      double targetInclination,
      GravityTurnConstraints constraints) {
    super(name);
    this.pitchKickAngle = pitchKickAngle;
    this.interstageCoastDuration = interstageCoastDuration;
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
    SpacecraftState entryState = mission.getCurrentState();
    GravityTurnManeuver maneuver = createManeuver(mission, entryState.getMass());
    return new GravityTurnProblem(maneuver, entryState, constraints);
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    GravityTurnManeuver maneuver = createManeuver(mission, previousState.getMass());
    return maneuver.applyKick(previousState);
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    // Replay uses the same burn-2 invariant the optimizer's own propagator uses.
    return createManeuver(mission, entryState.getMass()).maxStepSeconds();
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (optimizationResult == null) {
      throw new OrbitlabException(
          "GravityTurnStage '" + getName() + "' requires optimization before execution");
    }

    SpacecraftState state = mission.getCurrentState();
    // The pitch kick applied by enter() preserves mass: this is still the entry mass.
    GravityTurnManeuver maneuver = createManeuver(mission, state.getMass());
    GravityTurnManeuver.GravityTurnParams params =
        maneuver.decode(optimizationResult.bestVariables());

    maneuver.configure(propagator, state, params);
    // Replay path: the optimized transition time is supposed to fit the loaded propellant, so a
    // depletion here is a real accounting bug — fail loud.
    DepletionGuard.arm(propagator, maneuver.getDepletionFloor(), getName());

    // MECO event → transition to next stage
    AbsoluteDate mecoDate = state.getDate().shiftedBy(params.transitionTime());
    this.configuredEndDate = mecoDate;
    propagator.addEventDetector(
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  private GravityTurnManeuver createManeuver(Mission mission, double entryMass) {
    Vehicle vehicle = mission.getVehicle();
    double launchAzimuth = Physics.getLaunchAzimuth(launchLatitude, targetInclination);
    return new GravityTurnManeuver(
        vehicle, entryMass, Math.toRadians(pitchKickAngle), launchAzimuth,
        interstageCoastDuration);
  }
}
