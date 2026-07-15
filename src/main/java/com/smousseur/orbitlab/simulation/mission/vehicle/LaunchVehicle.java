package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * A single-stage launch vehicle with a fixed dry mass, propellant capacity, and propulsion system.
 * Provides factory methods for standard launcher stage configurations.
 *
 * @param dryMass the structural mass of this stage without propellant (kg)
 * @param propellantCapacity the maximum propellant mass this stage can carry (kg)
 * @param propulsion the propulsion system of this stage
 */
public record LaunchVehicle(
    double dryMass, double propellantCapacity, double propellantLoad, PropulsionSystem propulsion)
    implements Vehicle {
  public LaunchVehicle {
    if (propellantLoad > propellantCapacity) {
      throw new IllegalArgumentException("propellantLoad cannot exceed propellantCapacity");
    }
  }

  public LaunchVehicle(double dryMass, double propellantCapacity, PropulsionSystem propulsion) {
    this(dryMass, propellantCapacity, propellantCapacity, propulsion);
  }

  /**
   * Creates a standard first-vehicle launch vehicle with typical heavy-lift characteristics.
   *
   * @return a first-vehicle launch vehicle
   */
  public static LaunchVehicle getLauncherStage1Vehicle() {
    return new LaunchVehicle(27000, 425_000, PropulsionSystem.getLauncherStage1Propulsion());
  }

  /**
   * Creates a standard second-vehicle launch vehicle with upper-vehicle characteristics.
   *
   * @return a second-vehicle launch vehicle
   */
  public static LaunchVehicle getLauncherStage2Vehicle() {
    return new LaunchVehicle(5000, 134_000, PropulsionSystem.getLauncherStage2Propulsion());
  }
}
