package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.ArrayList;
import java.util.List;

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
