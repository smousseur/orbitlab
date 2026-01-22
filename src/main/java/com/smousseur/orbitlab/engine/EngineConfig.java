package com.smousseur.orbitlab.engine;

import java.util.Objects;

/**
 * Rendering/engine-level configuration (JME world-units, camera defaults, etc.).
 * No Orekit dependency.
 */
public record EngineConfig(
    float systemRadiusWorldUnits,
    OrbitCameraConfig orbitCamera
) {

  public EngineConfig {
    if (!Float.isFinite(systemRadiusWorldUnits) || systemRadiusWorldUnits <= 0f) {
      throw new IllegalArgumentException("systemRadiusWorldUnits must be finite and > 0");
    }
    Objects.requireNonNull(orbitCamera, "orbitCamera");
  }

  /** Default values tuned for "see the whole solar system" in current JME world-units. */
  public static EngineConfig defaultSolarSystem() {
    float systemRadius = 20_000f;
    OrbitCameraConfig cam = OrbitCameraConfig.defaultForSystemRadius(systemRadius);
    return new EngineConfig(systemRadius, cam);
  }
}
