package com.smousseur.orbitlab.simulation.mission.vehicle;

import org.orekit.utils.Constants;

/**
 * Defines the propulsion characteristics of a vehicle stage, including specific impulse and thrust.
 * Provides utility methods for computing propellant consumption and factory methods for standard
 * propulsion configurations.
 *
 * @param isp the specific impulse in seconds
 * @param thrust the engine thrust in Newtons
 */
public record PropulsionSystem(double isp, double thrust) {
  /**
   * Computes the propellant mass consumed over a given burn duration using the rocket equation
   * mass flow rate.
   *
   * @param duration the burn duration in seconds
   * @return the mass of propellant consumed in kilograms
   */
  public double massBurnt(double duration) {
    double massFlowRate = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    return massFlowRate * duration;
  }

  /**
   * Creates a standard first-stage propulsion system with high thrust for atmospheric ascent.
   *
   * @return a first-stage propulsion system
   */
  public static PropulsionSystem getLauncherStage1Propulsion() {
    return new PropulsionSystem(300, 8_400_000);
  }

  /**
   * Creates a standard second-stage propulsion system with higher specific impulse for vacuum
   * operation.
   *
   * @return a second-stage propulsion system
   */
  public static PropulsionSystem getLauncherStage2Propulsion() {
    return new PropulsionSystem(348, 1_250_000);
  }

  /**
   * Creates a standard spacecraft propulsion system for on-orbit maneuvering.
   *
   * @return a spacecraft propulsion system
   */
  public static PropulsionSystem getSpacecraftPropulsion() {
    return new PropulsionSystem(300, 3000);
  }
}
