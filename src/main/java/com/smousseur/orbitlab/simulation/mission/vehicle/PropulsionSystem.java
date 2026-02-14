package com.smousseur.orbitlab.simulation.mission.vehicle;

public record PropulsionSystem(double isp, double thrust) {
  public static PropulsionSystem getLauncherPropulsion() {
    return new PropulsionSystem(300.0, 1e7);
  }

  /*
   public static PropulsionSystem getSpacecraftPropulsion() {
     return new PropulsionSystem(400.0, 200000);
   }
  */
  public static PropulsionSystem getSpacecraftPropulsion() {
    return new PropulsionSystem(300, 7000);
  }
}
