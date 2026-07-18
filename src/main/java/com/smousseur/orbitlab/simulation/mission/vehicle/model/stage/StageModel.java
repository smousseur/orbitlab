package com.smousseur.orbitlab.simulation.mission.vehicle.model.stage;

import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;

import java.util.Objects;

/**
 * Static description of one stage of a launcher model (design data, not a flying instance). The
 * tank size ({@code propellantCapacity}) is fixed by design; the propellant load is chosen per
 * mission when the stage is instantiated via {@link #toVehicle(double)}.
 *
 * @param name the stage name, for diagnostics and logs (e.g. "S1 (3 cores aggregated)")
 * @param dryMass the structural mass without propellant (kg)
 * @param propellantCapacity the maximum propellant mass the tank can hold (kg)
 * @param propulsion the propulsion system of the stage
 * @param capabilities the physical capabilities of the stage
 */
public record StageModel(
    String name,
    double dryMass,
    double propellantCapacity,
    PropulsionSystem propulsion,
    StageCapabilities capabilities) {

  public StageModel {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(propulsion, "propulsion");
    Objects.requireNonNull(capabilities, "capabilities");
    if (dryMass <= 0) {
      throw new IllegalArgumentException("dryMass must be positive");
    }
    if (Double.isNaN(propellantCapacity) || propellantCapacity < 0) {
      throw new IllegalArgumentException("propellantCapacity cannot be negative");
    }
  }

  /**
   * Instantiates this stage with a mission-specific propellant load.
   *
   * @param propellantLoad the propellant mass to load (kg), within [0, capacity]; solid stages must
   *     be loaded at full capacity
   * @return the flying instance of this stage
   */
  public LaunchVehicle toVehicle(double propellantLoad) {
    if (!(propellantLoad >= 0 && propellantLoad <= propellantCapacity)) {
      throw new IllegalArgumentException(
          "propellantLoad must be within [0, " + propellantCapacity + "]: " + propellantLoad);
    }
    if (!capabilities.variableLoad() && propellantLoad != propellantCapacity) {
      throw new IllegalArgumentException("solid stage flies full: load must equal capacity");
    }
    return new LaunchVehicle(dryMass, propellantCapacity, propellantLoad, propulsion);
  }

  /** Instantiates this stage loaded at full capacity. */
  public LaunchVehicle toVehicleFullyLoaded() {
    return toVehicle(propellantCapacity);
  }
}
