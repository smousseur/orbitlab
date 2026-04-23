package com.smousseur.orbitlab.engine;

/**
 * Orbit camera tuning parameters controlling world-unit scaling, input sensitivity, distance
 * clamping, and dynamic frustum adjustment. Defaults are intentionally conservative and meant to be
 * adjusted per use case.
 *
 * @param systemRadiusWorldUnits the solar system radius in JME world units
 * @param defaultDistanceFactor factor applied to system radius to compute default camera distance
 * @param rotateSpeedRadPerPixel rotation speed in radians per pixel of mouse movement
 * @param pitchMinRad minimum pitch angle in radians (looking down limit)
 * @param pitchMaxRad maximum pitch angle in radians (looking up limit)
 * @param zoomSpeed exponential zoom speed factor (distance *= exp(-wheelDelta * zoomSpeed))
 * @param invertZoom whether to invert the zoom direction
 * @param panFactor pan speed factor (panSpeed = panFactor * distance, in world units per pixel)
 * @param minDistance minimum allowed camera distance from the target
 * @param maxDistance maximum allowed camera distance from the target
 * @param nearFactor factor for computing the dynamic near clip plane
 * @param nearMin minimum value for the near clip plane
 * @param nearMax maximum value for the near clip plane
 * @param farFactor factor for computing the dynamic far clip plane
 * @param farMin minimum value for the far clip plane
 * @param farMax maximum value for the far clip plane
 */
public record OrbitCameraConfig(
    float systemRadiusWorldUnits,
    float defaultDistanceFactor,
    float rotateSpeedRadPerPixel,
    float pitchMinRad,
    float pitchMaxRad,
    float zoomSpeed,
    boolean invertZoom,
    float panFactor,
    float minDistance,
    float maxDistance,
    float nearFactor,
    float nearMin,
    float nearMax,
    float farFactor,
    float farMin,
    float farMax) {

  /**
   * Computes the default camera distance from the target based on the system radius and the default
   * distance factor.
   *
   * @return the default camera distance in world units
   */
  public float defaultDistance() {
    return systemRadiusWorldUnits * defaultDistanceFactor;
  }

  /**
   * Creates a default orbit camera configuration for the given system radius, with conservative
   * defaults suitable for solar system visualization.
   *
   * @param systemRadiusWorldUnits the solar system radius in JME world units
   * @return a default orbit camera configuration
   */
  public static OrbitCameraConfig defaultForSystemRadius(float systemRadiusWorldUnits) {
    float deg = (float) (Math.PI / 180.0);
    return new OrbitCameraConfig(
        systemRadiusWorldUnits,
        0.2f,
        10f,
        -89f * deg,
        +89f * deg,
        0.12f,
        false,
        0.01f,
        1e-8f,
        10_000_000f,
        0.001f,
        0.001f,
        10_000f,
        10.0f,
        1_000f,
        20_000_000f);
  }
}
