package com.smousseur.orbitlab.simulation.mission.vehicle;

public record PropulsionSystem(double isp, double thrust) {
  public static PropulsionSystem getLauncherStage1Propulsion() {
    return new PropulsionSystem(300, 8_400_000);
  }

  public static PropulsionSystem getLauncherStage2Propulsion() {
    return new PropulsionSystem(348, 1_250_000);
  }

  public static PropulsionSystem getSpacecraftPropulsion() {
    return new PropulsionSystem(300, 3000);
  }
}
