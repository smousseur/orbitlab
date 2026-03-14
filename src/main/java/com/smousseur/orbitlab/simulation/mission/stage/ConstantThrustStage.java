package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
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

/**
 * A mission stage that applies a constant-thrust maneuver along the velocity direction in the
 * TNW (Tangential-Normal-Binormal) local orbital frame. The stage terminates after the specified
 * burn duration. If the duration is set to zero, it is automatically computed from the vehicle's
 * available propellant and thrust at entry time.
 */
public class ConstantThrustStage extends MissionStage {
  protected double duration;

  /**
   * Creates a constant thrust stage with the given burn duration.
   *
   * @param name the human-readable name of this stage
   * @param duration the burn duration in seconds; if zero, duration is computed from available
   *     propellant at stage entry
   */
  public ConstantThrustStage(String name, double duration) {
    super(name);
    this.duration = duration;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    if (duration == 0) {
      Vehicle vehicle = mission.getVehicle();
      PropulsionSystem propulsion = vehicle.propulsion();
      double mDot = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
      this.duration = vehicle.propellantCapacity() / mDot;
    }
    return previousState;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState currentState = mission.getCurrentState();

    PropulsionSystem propulsion = mission.getVehicle().propulsion();
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

  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    SpacecraftState stateAfterEnter = enter(currentState, mission);

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(stateAfterEnter);

    PropulsionSystem propulsion = mission.getVehicle().propulsion();
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            stateAfterEnter.getDate().shiftedBy(1.0e-3),
            this.duration,
            propulsion.thrust(),
            propulsion.isp(),
            getAttitudeProvider(stateAfterEnter),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn);

    return propagator.propagate(stateAfterEnter.getDate().shiftedBy(this.duration));
  }
}
