package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Represents a vehicle in the mission simulation. A vehicle has a dry mass, propellant capacity,
 * and a propulsion system. Vehicles can be single-stage or multi-stage (via {@link VehicleStack}).
 *
 * <p>The active stage is resolved dynamically via {@link #resolveActiveStage(double)} based on the
 * current spacecraft mass, eliminating the need for explicit stage-index lookups.
 */
public interface Vehicle {
  /**
   * Returns the dry mass of this vehicle (structural mass without propellant).
   *
   * @return the dry mass in kilograms
   */
  double dryMass();

  /**
   * Returns the maximum propellant capacity of this vehicle.
   *
   * @return the propellant capacity in kilograms
   */
  double propellantCapacity();

  /**
   * Returns the propellant actually loaded into the vehicle.
   *
   * @return the propellant loaded in kilograms
   */
  double propellantLoad();

  /**
   * Returns the propulsion system of this vehicle or its active vehicle.
   *
   * @return the propulsion system
   */
  PropulsionSystem propulsion();

  /**
   * Returns the total mass of this vehicle (dry mass plus propellant loaded).
   *
   * @return the total mass in kilograms
   */
  default double getMass() {
    return dryMass() + propellantLoad();
  }

  /**
   * Resolves the active vehicle based on the current spacecraft mass. For single-vehicle vehicles,
   * always returns itself. For multi-vehicle vehicles ({@link VehicleStack}), determines which
   * vehicle is active by comparing the current mass against cumulative vehicle mass thresholds.
   *
   * @param currentMass the current spacecraft mass from SpacecraftState
   * @return the active vehicle information
   */
  default ActiveStageInfo resolveActiveStage(double currentMass) {
    return new ActiveStageInfo(0, this, 0, 0);
  }
}
