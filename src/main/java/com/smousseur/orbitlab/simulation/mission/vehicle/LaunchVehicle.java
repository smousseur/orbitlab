package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.Objects;

public class LaunchVehicle implements Vehicle {
  private double mass;
  private final PropulsionSystem propulsion;

  public LaunchVehicle(double mass, PropulsionSystem propulsion) {
    this.mass = mass;
    this.propulsion = propulsion;
  }

  @Override
  public double getMass() {
    return mass;
  }

  public void setMass(double mass) {
    this.mass = mass;
  }

  @Override
  public PropulsionSystem getPropulsion() {
    return propulsion;
  }

  public static LaunchVehicle getLauncherVechicle() {
    return new LaunchVehicle(500000, PropulsionSystem.getLauncherPropulsion());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    LaunchVehicle that = (LaunchVehicle) o;
    return Double.compare(mass, that.mass) == 0 && Objects.equals(propulsion, that.propulsion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mass, propulsion);
  }
}
