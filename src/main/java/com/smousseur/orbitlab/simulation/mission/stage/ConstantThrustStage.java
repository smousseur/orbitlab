package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

public class ConstantThrustStage extends MissionStage {
  protected double duration;

  public ConstantThrustStage(String name) {
    super(name);
  }

  public ConstantThrustStage(String name, double duration) {
    super(name);
    this.duration = duration;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    if (duration == 0) {
      Vehicle vehicle = mission.getVehicle();
      PropulsionSystem propulsion = vehicle.getPropulsion();
      double mDot = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
      this.duration = vehicle.getPropellantMass() / mDot;
    }
    return previousState;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState currentState = mission.getCurrentState();

    PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            currentState.getDate().shiftedBy(1.0e-3),
            this.duration,
            propulsion.thrust(),
            propulsion.isp(),
            getAttitudeProvider(currentState),
            Vector3D.PLUS_I);

    propagator.addForceModel(burn);

    AbsoluteDate mecoDate = currentState.getDate().shiftedBy(this.duration);
    DateDetector mecoDetector =
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });

    propagator.addEventDetector(mecoDetector);
  }

  protected AttitudeProvider getAttitudeProvider(SpacecraftState state) {
    return new LofOffset(state.getFrame(), LOFType.TNW);
  }
}
