package com.smousseur.orbitlab.simulation.mission.vehicle;

public record Spacecraft(double dryMass, double propellantCapacity, PropulsionSystem propulsion)
    implements Vehicle {
  public static Spacecraft getSpacecraft() {
    return new Spacecraft(15000, 0, PropulsionSystem.getSpacecraftPropulsion());
  }
}
