package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * A single-stage launch vehicle with a fixed dry mass, propellant capacity, and propulsion system.
 * Provides factory methods for standard launcher stage configurations.
 *
 * @param dryMass the structural mass of this stage without propellant (kg)
 * @param propellantCapacity the maximum propellant mass this stage can carry (kg)
 * @param propulsion the propulsion system of this stage
 */
public record LaunchVehicle(double dryMass, double propellantCapacity, PropulsionSystem propulsion)
    implements Vehicle {
  /**
   * Creates a standard first-stage launch vehicle with typical heavy-lift characteristics.
   *
   * @return a first-stage launch vehicle
   */
  public static LaunchVehicle getLauncherStage1Vehicle() {
    return new LaunchVehicle(27000, 425_000, PropulsionSystem.getLauncherStage1Propulsion());
  }

  /**
   * Creates a standard second-stage launch vehicle with upper-stage characteristics.
   *
   * @return a second-stage launch vehicle
   */
  public static LaunchVehicle getLauncherStage2Vehicle() {
    return new LaunchVehicle(10000, 134_000, PropulsionSystem.getLauncherStage2Propulsion());
  }
}
