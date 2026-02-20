package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.stage.MissionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

public class GravityTurnStage extends MissionStage {
  private final double targetInclination;
  private final double ascensionDuration;
  private final double pitchKickAngle;
  private final double launchLatitude;
  private double maxBurnTime;

  public GravityTurnStage(String name, double ascensionDuration, double pitchKickAngle) {
    this(name, ascensionDuration, pitchKickAngle, 0.0, 0.0);
  }

  public GravityTurnStage(
      String name,
      double ascensionDuration,
      double pitchKickAngle,
      double launchLatitude,
      double targetInclination) {
    super(name);
    this.ascensionDuration = ascensionDuration;
    this.targetInclination = targetInclination;
    this.pitchKickAngle = pitchKickAngle;
    this.launchLatitude = launchLatitude;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    Vehicle vehicle = mission.getVehicle();
    SpacecraftState state =
        Physics.applyPitchKick(
            previousState,
            pitchKickAngle,
            Physics.getLaunchAzimuth(launchLatitude, targetInclination));
    PropulsionSystem propulsion = vehicle.getPropulsion();
    double mDot = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
    double propellantBurned = ascensionDuration * mDot;
    double propellantRemaining = vehicle.getPropellantMass() - propellantBurned;
    this.maxBurnTime = propellantRemaining / mDot; // 114
    return state;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
    AbsoluteDate kickDate = state.getDate().shiftedBy(1.0e-3);
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            kickDate,
            this.maxBurnTime,
            propulsion.thrust(),
            propulsion.isp(),
            new GravityTurnAttitudeProvider(kickDate, 110.0, 1),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn);

    AbsoluteDate mecoDate = state.getDate().shiftedBy(this.maxBurnTime);
    DateDetector mecoDetector =
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });

    propagator.addEventDetector(mecoDetector);
  }
}
