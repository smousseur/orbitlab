package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.Objects;

public class LaunchVehicle implements Vehicle {
  private final double dryMass;
  private double propellantMass;
  private final PropulsionSystem propulsion;

  public LaunchVehicle(double dryMass, double propellantMass, PropulsionSystem propulsion) {
    this.dryMass = dryMass;
    this.propellantMass = propellantMass;
    this.propulsion = propulsion;
  }

  @Override
  public double getDryMass() {
    return dryMass;
  }

  @Override
  public double getPropellantMass() {
    return propellantMass;
  }

  public void setPropellantMass(double propellantMass) {
    this.propellantMass = propellantMass;
  }

  @Override
  public PropulsionSystem getPropulsion() {
    return propulsion;
  }

  public static LaunchVehicle getLauncherVechicle() {
    return new LaunchVehicle(500000, 40000, PropulsionSystem.getLauncherPropulsion());
  }

  public static LaunchVehicle getLauncherStage1Vechicle() {
    return new LaunchVehicle(25000, 400_000, PropulsionSystem.getLauncherStage1Propulsion());
  }

  public static LaunchVehicle getLauncherStage2Vechicle() {
    return new LaunchVehicle(4000, 100_000, PropulsionSystem.getLauncherStage2Propulsion());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    LaunchVehicle that = (LaunchVehicle) o;
    return Double.compare(dryMass, that.dryMass) == 0
        && Double.compare(propellantMass, that.propellantMass) == 0
        && Objects.equals(propulsion, that.propulsion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dryMass, propellantMass, propulsion);
  }
}
