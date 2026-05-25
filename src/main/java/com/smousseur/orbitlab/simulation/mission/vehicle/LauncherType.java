package com.smousseur.orbitlab.simulation.mission.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue of launchers selectable from the mission wizard. Each launcher carries the path to its
 * 3D model asset and knows how to assemble its multi-stage {@link VehicleStack} (without payload).
 */
public enum LauncherType {
  FALCON_HEAVY("models/vehicles/heavy_falcon/heavy_falcon.gltf") {
    @Override
    public VehicleStack toVehicleStack() {
      return new VehicleStack(
          new ArrayList<>(List.of(LaunchVehicle.defaultStage1(), LaunchVehicle.defaultStage2())));
    }
  },
  ARIANE_5_ECA("models/vehicles/ariane_5_eca/ariane_5_eca.gltf") {
    @Override
    public VehicleStack toVehicleStack() {
      return new VehicleStack(
          new ArrayList<>(List.of(LaunchVehicle.ariane5Stage1(), LaunchVehicle.ariane5Stage2())));
    }
  };

  private final String modelPath;

  LauncherType(String modelPath) {
    this.modelPath = modelPath;
  }

  /**
   * Returns the JME3 asset path of the 3D model representing this launcher.
   *
   * @return the asset path
   */
  public String modelPath() {
    return modelPath;
  }

  /**
   * Builds a {@link VehicleStack} containing this launcher's stages, ordered bottom-to-top, without
   * any payload. Use {@link com.smousseur.orbitlab.simulation.mission.Mission#buildVehicle} to add a
   * payload on top.
   *
   * @return a vehicle stack of the launcher's stages
   */
  public abstract VehicleStack toVehicleStack();
}
