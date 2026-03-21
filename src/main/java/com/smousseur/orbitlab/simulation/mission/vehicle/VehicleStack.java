package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.List;

/**
 * A composite vehicle made up of multiple stacked stages. The first vehicle in the list represents
 * the currently active (bottom) stage. Mass and propulsion queries delegate to the constituent
 * vehicles.
 *
 * @param vehicles the ordered list of vehicle stages
 */
public record VehicleStack(List<Vehicle> vehicles) implements Vehicle {
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
  public PropulsionSystem propulsion() {
    return vehicles.getFirst().propulsion();
  }
}
