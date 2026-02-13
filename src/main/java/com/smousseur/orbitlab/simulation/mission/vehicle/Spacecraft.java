package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.Objects;

public class Spacecraft implements Vehicle {
  private double mass;
  private final PropulsionSystem propulsion;

  public Spacecraft(double mass, PropulsionSystem propulsion) {
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

  /*
    public static Spacecraft getSpacecraft() {
      return new Spacecraft(25000, PropulsionSystem.getSpacecraftPropulsion());
    }
  */
  public static Spacecraft getSpacecraft() {
    return new Spacecraft(100, PropulsionSystem.getSpacecraftPropulsion());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Spacecraft that = (Spacecraft) o;
    return Double.compare(mass, that.mass) == 0 && Objects.equals(propulsion, that.propulsion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mass, propulsion);
  }
}
