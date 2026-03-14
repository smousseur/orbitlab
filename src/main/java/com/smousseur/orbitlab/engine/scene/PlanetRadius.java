package com.smousseur.orbitlab.engine.scene;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.orekit.utils.Constants;

/**
 * Provides the mean equatorial radius in meters for each solar system body.
 * Uses the WGS84 constant for Earth; other values are standard IAU approximations.
 */
public final class PlanetRadius {

  private PlanetRadius() {}

  /**
   * Returns the mean equatorial radius of the given solar system body in meters.
   *
   * @param body the solar system body
   * @return the equatorial radius in meters
   */
  public static double radiusFor(SolarSystemBody body) {
    return switch (body) {
      case MERCURY -> 2_439_700;
      case VENUS -> 6_051_800;
      case EARTH -> Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
      case MARS -> 3_389_500;
      case JUPITER -> 69_911_000;
      case SATURN -> 58_232_000;
      case URANUS -> 25_362_000;
      case NEPTUNE -> 24_622_000;
      case PLUTO -> 1_188_300;
      case SUN -> 696_340_000;
    };
  }
}
