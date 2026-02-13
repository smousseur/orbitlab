package com.smousseur.orbitlab.simulation.mission.forces;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.forces.ForceModel;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.ParameterDriver;

import java.util.List;

public class GravityTurnForceModel implements ForceModel {
  private Vector3D acceleration;
  private final double speedThreshold;

  public GravityTurnForceModel(Vector3D acceleration, double speedThreshold) {
    this.acceleration = acceleration;
    this.speedThreshold = speedThreshold;
  }

  @Override
  public Vector3D acceleration(SpacecraftState s, double[] parameters) {
    Vector3D velocity = s.getVelocity();
    double speed = velocity.getNorm();
    if (speed < speedThreshold) {}

    return acceleration;
  }

  @Override
  public <T extends CalculusFieldElement<T>> FieldVector3D<T> acceleration(
      FieldSpacecraftState<T> s, T[] parameters) {
    return FieldVector3D.getZero(s.getDate().getField());
  }

  @Override
  public boolean dependsOnPositionOnly() {
    return false;
  }

  @Override
  public List<ParameterDriver> getParametersDrivers() {
    return List.of();
  }
}
