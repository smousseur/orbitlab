package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.List;

public class VehicleStack implements Vehicle {
  private final List<Vehicle> vehicles;

  public VehicleStack(List<Vehicle> vehicles) {
    this.vehicles = vehicles;
  }

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

  public Vehicle jettison(int index) {
    return vehicles.remove(index);
  }

  public List<Vehicle> getVehicles() {
    return vehicles;
  }

  @Override
  public PropulsionSystem getPropulsion() {
    return vehicles.getFirst().getPropulsion();
  }
}
