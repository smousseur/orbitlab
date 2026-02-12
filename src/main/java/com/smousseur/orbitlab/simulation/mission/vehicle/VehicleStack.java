package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.List;

public class VehicleStack implements Vehicle {
  private final List<Vehicle> vehicles;
  private PropulsionSystem propulsion;

  public VehicleStack(List<Vehicle> vehicles, PropulsionSystem propulsion) {
    this.vehicles = vehicles;
    this.propulsion = propulsion;
  }

  @Override
  public double getMass() {
    return vehicles.stream().mapToDouble(Vehicle::getMass).sum();
  }

  public Vehicle jettison(int index) {
    this.propulsion = vehicles.get(index + 1).getPropulsion();
    return vehicles.remove(index);
  }

  public List<Vehicle> getVehicles() {
    return vehicles;
  }

  @Override
  public PropulsionSystem getPropulsion() {
    return propulsion;
  }

  public static VehicleStack getDefaultStack() {
    return new VehicleStack(
        List.of(LaunchVehicle.getLauncherVechicle(), Spacecraft.getSpacecraft()),
        PropulsionSystem.getLauncherPropulsion());
  }
}
