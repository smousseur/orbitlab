package com.smousseur.orbitlab.simulation.mission.vehicle;

public record PropulsionSystem(double isp, double thrust) {
  public static PropulsionSystem getLauncherPropulsion() {
    return new PropulsionSystem(300, 1e7);
  }

  public static PropulsionSystem getLauncherStage1Propulsion() {
    return new PropulsionSystem(300, 5_000_000);
  }

  public static PropulsionSystem getLauncherStage2Propulsion() {
    return new PropulsionSystem(348, 1_500_000);
  }

  /*
   public static PropulsionSystem getSpacecraftPropulsion() {
     return new PropulsionSystem(400.0, 200000);
   }
  */
  public static PropulsionSystem getSpacecraftPropulsion() {
    return new PropulsionSystem(300, 3000);
  }
}
