package com.smousseur.orbitlab.simulation.mission.vehicle;

import org.orekit.utils.Constants;

public record PropulsionSystem(double isp, double thrust) {
  public double massBurnt(double duration) {
    double massFlowRate = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    return massFlowRate * duration;
  }

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
