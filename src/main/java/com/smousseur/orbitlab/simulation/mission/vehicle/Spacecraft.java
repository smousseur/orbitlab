package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.Objects;

public class Spacecraft implements Vehicle {
  private final double dryMass;
  private double propellantMass;
  private final PropulsionSystem propulsion;

  public Spacecraft(double dryMass, double propellantMass, PropulsionSystem propulsion) {
    this.dryMass = dryMass;
    this.propellantMass = propellantMass;
    this.propulsion = propulsion;
  }

  @Override
  public PropulsionSystem getPropulsion() {
    return propulsion;
  }

  /*
    public static Spacecraft getSpacecraft() {
      return new Spacecraft(25000, PropulsionSystem.getSpacecraftPropulsion());
    }
  */
  public static Spacecraft getSpacecraft() {
    return new Spacecraft(18000, 0, PropulsionSystem.getSpacecraftPropulsion());
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
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Spacecraft that = (Spacecraft) o;
    return Double.compare(dryMass, that.dryMass) == 0
        && Double.compare(propellantMass, that.propellantMass) == 0
        && Objects.equals(propulsion, that.propulsion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dryMass, propellantMass, propulsion);
  }
}
