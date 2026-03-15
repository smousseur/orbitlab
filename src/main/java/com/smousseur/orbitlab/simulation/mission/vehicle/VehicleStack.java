package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * A composite vehicle made up of multiple stacked stages. The first vehicle in the list
 * represents the currently active (bottom) stage. Stages can be jettisoned by index,
 * removing them from the stack. Mass and propulsion queries delegate to the constituent
 * vehicles, with the active stage providing the propulsion system.
 *
 * @param vehicles the ordered list of vehicle stages, from active (first) to uppermost (last)
 */
public record VehicleStack(List<Vehicle> vehicles) implements Vehicle {

  /** Compact canonical constructor: defensive copy to prevent external mutation. */
  public VehicleStack {
    vehicles = new ArrayList<>(vehicles);
  }

  @Override
  public Vehicle getStage(int index) {
    return vehicles.get(index);
  }

  @Override
  public double dryMass() {
    return vehicles.stream().mapToDouble(Vehicle::dryMass).sum();
  }

  @Override
  public double propellantCapacity() {
    return vehicles.stream().mapToDouble(Vehicle::propellantCapacity).sum();
  }

  @Override
  public double getMass() {
    return vehicles.stream().mapToDouble(Vehicle::getMass).sum();
  }

  @Override
  public double getFullDryMass() {
    return getMass() - vehicles.getFirst().propellantCapacity();
  }

  @Override
  public double getCurrentStagePropellantMass() {
    return vehicles.getFirst().propellantCapacity();
  }

  @Override
  public double getFirstStageDryMass() {
    return vehicles.getFirst().dryMass();
  }

  @Override
  public void jettison(int index) {
    vehicles.remove(index);
  }

  @Override
  public PropulsionSystem propulsion() {
    return vehicles.getFirst().propulsion();
  }
}
