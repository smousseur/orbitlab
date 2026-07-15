package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.ArrayList;
import java.util.List;

public final class Launchers {
  private Launchers() {}

  public static VehicleStack FalconHeavy(
      double stage1Load, double stage2Load, Spacecraft spacecraft) {
    List<Vehicle> vehicles =
        new ArrayList<>(
            List.of(
                new LaunchVehicle(
                    66_000, 1_233_000, stage1Load, new PropulsionSystem(311, 22_800_000)),
                new LaunchVehicle(4_000, 107_000, stage2Load, new PropulsionSystem(348, 981_000))));
    vehicles.add(spacecraft);
    return new VehicleStack(vehicles);
  }
}
