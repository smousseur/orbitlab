package com.smousseur.orbitlab.engine;

import java.util.Objects;

/**
 * Rendering and engine-level configuration defining world-unit scaling and camera defaults.
 * This record has no Orekit dependency and is purely concerned with JME3 rendering parameters.
 *
 * @param systemRadiusWorldUnits the radius of the visible solar system in JME world units (must be finite and positive)
 * @param orbitCamera            the orbit camera tuning configuration
 * @param cameraTransition       the tuning of the animated focus transitions
 */
public record EngineConfig(
    float systemRadiusWorldUnits,
    OrbitCameraConfig orbitCamera,
    CameraTransitionConfig cameraTransition
) {

  public EngineConfig {
    if (!Float.isFinite(systemRadiusWorldUnits) || systemRadiusWorldUnits <= 0f) {
      throw new IllegalArgumentException("systemRadiusWorldUnits must be finite and > 0");
    }
    Objects.requireNonNull(orbitCamera, "orbitCamera");
    Objects.requireNonNull(cameraTransition, "cameraTransition");
  }

  /**
   * Creates a default configuration tuned for viewing the entire solar system
   * with a system radius of 20,000 JME world units.
   *
   * @return an engine configuration with default solar system parameters
   */
  public static EngineConfig defaultSolarSystem() {
    float systemRadius = 20_000f;
    OrbitCameraConfig cam = OrbitCameraConfig.defaultForSystemRadius(systemRadius);
    return new EngineConfig(systemRadius, cam, CameraTransitionConfig.defaults());
  }
}
