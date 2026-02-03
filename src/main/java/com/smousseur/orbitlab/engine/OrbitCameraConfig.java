package com.smousseur.orbitlab.engine;

/**
 * Orbit camera tuning parameters (world-units and input sensitivity). Defaults are intentionally
 * conservative and meant to be adjusted.
 */
public record OrbitCameraConfig(
    // World scale / default framing
    float systemRadiusWorldUnits,
    float defaultDistanceFactor, // defaultDistance = systemRadius * factor

    // Orbit (turntable)
    float rotateSpeedRadPerPixel,
    float pitchMinRad,
    float pitchMaxRad,

    // Dolly (exponential)
    float zoomSpeed, // distance *= exp(-wheelDelta * zoomSpeed)
    boolean invertZoom,

    // Pan (screen-plane): panSpeed = panFactor * distance (worldUnits/pixel)
    float panFactor,

    // Distance clamp
    float minDistance,
    float maxDistance,

    // Dynamic frustum
    float nearFactor,
    float nearMin,
    float nearMax,
    float farFactor,
    float farMin,
    float farMax) {

  public float defaultDistance() {
    return systemRadiusWorldUnits * defaultDistanceFactor;
  }

  public static OrbitCameraConfig defaultForSystemRadius(float systemRadiusWorldUnits) {
    float deg = (float) (Math.PI / 180.0);
    return new OrbitCameraConfig(
        systemRadiusWorldUnits,
        0.75f,
        10f,
        -89f * deg,
        +89f * deg,
        0.12f,
        false,
        0.01f,
        0.001f,
        10_000_000f,
        0.001f,
        0.001f,
        10_000f,
        10.0f,
        1_000f,
        20_000_000f);
  }
}
