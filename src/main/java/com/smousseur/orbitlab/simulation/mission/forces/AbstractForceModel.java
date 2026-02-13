package com.smousseur.orbitlab.simulation.mission.forces;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.orekit.forces.ForceModel;
import org.orekit.propagation.SpacecraftState;

public abstract class AbstractForceModel implements ForceModel {
  private static final double G0 = 9.80665;

  protected final PropulsionSystem propulsion;

  protected AbstractForceModel(PropulsionSystem propulsion) {
    this.propulsion = propulsion;
  }

  @Override
  public double getMassDerivative(SpacecraftState state, double[] parameters) {
    return -propulsion.thrust() / (propulsion.isp() * G0);
  }
}
