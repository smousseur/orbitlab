package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.List;

public record VehicleStack(List<Vehicle> vehicles) implements Vehicle {

  @Override
  public double getDryMass() {
    return vehicles.stream().mapToDouble(Vehicle::getDryMass).sum();
  }

  @Override
  public double getPropellantMass() {
    return vehicles.stream().mapToDouble(Vehicle::getPropellantMass).sum();
  }

  @Override
  public double getMass() {
    return vehicles.stream().mapToDouble(Vehicle::getMass).sum();
  }

  @Override
  public double getFullDryMass() {
    return getMass() - vehicles.getFirst().getPropellantMass();
  }

  @Override
  public double getCurrentStagePropellantMass() {
    return vehicles.getFirst().getPropellantMass();
  }

  public Vehicle jettison(int index) {
    return vehicles.remove(index);
  }

  @Override
  public PropulsionSystem getPropulsion() {
    return vehicles.getFirst().getPropulsion();
  }
}
