package com.smousseur.orbitlab.simulation.mission.vehicle;

/**
 * Represents a vehicle in the mission simulation. A vehicle has a dry mass, propellant capacity,
 * and a propulsion system. Vehicles can be single-stage or multi-stage (via {@link VehicleStack}),
 * and support stage retrieval and jettison operations.
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
   * Returns the first stage of this vehicle. For single-stage vehicles, returns itself.
   *
   * @return the first stage vehicle
   */
  default Vehicle getFirstStage() {
    return getStage(0);
  }

  /**
   * Returns the second stage of this vehicle. For single-stage vehicles, returns itself.
   *
   * @return the second stage vehicle
   */
  default Vehicle getSecondStage() {
    return getStage(1);
  }

  /**
   * Returns the stage at the given index. For single-stage vehicles, returns itself regardless of
   * the index.
   *
   * @param index the zero-based stage index
   * @return the vehicle representing the requested stage
   */
  default Vehicle getStage(int index) {
    return this;
  }

  /**
   * Returns the total mass of this vehicle (dry mass plus propellant).
   *
   * @return the total mass in kilograms
   */
  default double getMass() {
    return dryMass() + propellantCapacity();
  }

  /**
   * Returns the total dry mass of all stages combined. For stacked vehicles, this excludes the
   * current stage's propellant but includes all other mass.
   *
   * @return the full dry mass in kilograms
   */
  default double getFullDryMass() {
    return dryMass();
  }

  /**
   * Returns the propellant mass of the current (first) stage only.
   *
   * @return the current stage propellant mass in kilograms
   */
  default double getCurrentStagePropellantMass() {
    return propellantCapacity();
  }

  /**
   * Returns the propulsion system of this vehicle or its active stage.
   *
   * @return the propulsion system
   */
  PropulsionSystem propulsion();

  /**
   * Returns the dry mass of the first stage only.
   *
   * @return the first stage dry mass in kilograms
   */
  default double getFirstStageDryMass() {
    return dryMass();
  }
}
