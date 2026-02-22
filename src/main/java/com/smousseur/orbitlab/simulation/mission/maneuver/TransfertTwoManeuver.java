package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class TransfertTwoManeuver {
  private final Vehicle vehicle;

  public TransfertTwoManeuver(Vehicle vehicle) {
    this.vehicle = vehicle;
  }

  public record TransfertTwoManeuverParams(
      double t1,
      double dt1,
      double alpha1,
      double beta1,
      double dtCoast,
      double dt2,
      double alpha2,
      double beta2) {}

  public TransfertTwoManeuverParams decode(double[] variables) {
    return new TransfertTwoManeuverParams(
        variables[0],
        variables[1],
        variables[2],
        variables[3],
        variables[4],
        variables[5],
        variables[6],
        variables[7]);
  }

  public void configure(
      NumericalPropagator propagator, SpacecraftState state, TransfertTwoManeuverParams params) {
    AbsoluteDate epoch = state.getDate();
    // Derived: start of burn 2
    double t2 = params.t1 + params.dt1 + params.dtCoast;

    AbsoluteDate burn1Start = epoch.shiftedBy(params.t1);
    AbsoluteDate burn2Start = epoch.shiftedBy(t2);
    // Thrust direction vectors in TNW frame
    Vector3D thrustDirection1 = Physics.buildThrustDirectionTNW(params.alpha1, params.beta1);
    Vector3D thrustDirection2 = Physics.buildThrustDirectionTNW(params.alpha2, params.beta2);

    LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
    PropulsionSystem propulsion = vehicle.getPropulsion();
    // Create maneuvers
    ConstantThrustManeuver burn1 =
        new ConstantThrustManeuver(
            burn1Start,
            params.dt1,
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            thrustDirection1);
    ConstantThrustManeuver burn2 =
        new ConstantThrustManeuver(
            burn2Start,
            params.dt2,
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            thrustDirection2);

    // Attach maneuvers
    propagator.addForceModel(burn1);
    propagator.addForceModel(burn2);
  }

  public SpacecraftState propagateForOptimization(
      SpacecraftState initialState, double[] variables) {
    TransfertTwoManeuverParams params = decode(variables);
    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(initialState);
    configure(propagator, initialState, params);

    double t2 = params.t1 + params.dt1 + params.dtCoast;
    AbsoluteDate endDate = initialState.getDate().shiftedBy(t2 + params.dt2);
    return propagator.propagate(endDate);
  }
}
