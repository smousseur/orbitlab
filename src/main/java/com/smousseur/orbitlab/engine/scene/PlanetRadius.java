package com.smousseur.orbitlab.engine.scene;

import com.smousseur.orbitlab.core.SolarSystemBody;

public final class PlanetRadius {

  private PlanetRadius() {}

  public static double radiusFor(SolarSystemBody body) {
    return switch (body) {
      case MERCURY -> 2_439_700;
      case VENUS -> 6_051_800;
      case EARTH -> 6_371_000;
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
