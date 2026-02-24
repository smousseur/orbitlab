package com.smousseur.orbitlab.simulation.mission.vehicle;

public record LaunchVehicle(double dryMass, double propellantCapacity, PropulsionSystem propulsion)
    implements Vehicle {
  public static LaunchVehicle getLauncherStage1Vechicle() {
    return new LaunchVehicle(27000, 425_000, PropulsionSystem.getLauncherStage1Propulsion());
  }

  public static LaunchVehicle getLauncherStage2Vechicle() {
    return new LaunchVehicle(10000, 134_000, PropulsionSystem.getLauncherStage2Propulsion());
  }
}
