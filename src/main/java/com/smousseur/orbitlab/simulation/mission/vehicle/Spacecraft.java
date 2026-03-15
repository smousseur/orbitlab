package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Represents a spacecraft payload with its own dry mass, propellant capacity, and propulsion
 * system. Typically used as the uppermost element in a {@link VehicleStack}.
 *
 * @param dryMass the structural mass of the spacecraft without propellant (kg)
 * @param propellantCapacity the maximum propellant mass the spacecraft can carry (kg)
 * @param propulsion the spacecraft's propulsion system
 */
public record Spacecraft(double dryMass, double propellantCapacity, PropulsionSystem propulsion)
    implements Vehicle {
  /**
   * Creates a standard spacecraft with default mass and propulsion characteristics.
   *
   * @return a default spacecraft instance
   */
  public static Spacecraft getSpacecraft() {
    return new Spacecraft(15000, 0, PropulsionSystem.getSpacecraftPropulsion());
  }
}
