package com.smousseur.orbitlab.engine.scene.planet.lod;

import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Centralizes distance tuning for planet LOD. The returned value is a multiplier applied to the
 * planet radius (in current render units).
 *
 * <p>If distance < radius * multiplier => show 3D else => show icon
 */
public final class PlanetLodTuning {
  private PlanetLodTuning() {}

  /**
   * Returns the LOD distance ratio for the given body. The 3D model is shown when
   * the camera distance is less than the planet's radius multiplied by this ratio;
   * otherwise the icon view is used.
   *
   * @param body the solar system body
   * @return the distance-to-radius multiplier for LOD switching
   */
  public static double lodDistanceRatio(SolarSystemBody body) {
    // Start conservative; tweak per planet later.
    return switch (body) {
      case MERCURY -> 80.0;
      case VENUS -> 90.0;
      case EARTH -> 250.0;
      case MARS -> 90.0;
      case JUPITER -> 140.0;
      case SATURN -> 140.0;
      case URANUS -> 120.0;
      case NEPTUNE -> 120.0;
      case PLUTO -> 70.0;
      case SUN -> 200.0;
    };
  }
}
