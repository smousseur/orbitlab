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
   * Creates the default first-stage launch vehicle with typical heavy-lift characteristics.
   *
   * @return a first-stage launch vehicle
   */
  public static LaunchVehicle defaultStage1() {
    return new LaunchVehicle(27000, 425_000, PropulsionSystem.getLauncherStage1Propulsion());
  }

  /**
   * Creates the default second-stage launch vehicle with upper-stage characteristics.
   *
   * @return a second-stage launch vehicle
   */
  public static LaunchVehicle defaultStage2() {
    return new LaunchVehicle(5000, 134_000, PropulsionSystem.getLauncherStage2Propulsion());
  }

  /**
   * Creates a first-stage launch vehicle matching Ariane 5 ECA characteristics (EPC core combined
   * with two EAP solid boosters).
   *
   * @return an Ariane 5 ECA first-stage launch vehicle
   */
  public static LaunchVehicle ariane5Stage1() {
    return new LaunchVehicle(80_700, 653_000, PropulsionSystem.ariane5Stage1Propulsion());
  }

  /**
   * Creates a second-stage launch vehicle matching Ariane 5 ECA characteristics (ESC-A cryogenic
   * upper stage).
   *
   * @return an Ariane 5 ECA second-stage launch vehicle
   */
  public static LaunchVehicle ariane5Stage2() {
    return new LaunchVehicle(4_540, 14_900, PropulsionSystem.ariane5Stage2Propulsion());
  }
}
