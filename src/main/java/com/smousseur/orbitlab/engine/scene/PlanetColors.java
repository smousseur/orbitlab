package com.smousseur.orbitlab.engine.scene;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Provides characteristic display colors for each solar system body,
 * used for orbit lines and planet icon rendering.
 */
public final class PlanetColors {

  private PlanetColors() {}

  /**
   * Returns the characteristic display color for the given solar system body.
   *
   * @param body the solar system body
   * @return the RGBA color associated with the body
   */
  public static ColorRGBA colorFor(SolarSystemBody body) {
    return switch (body) {
      case MERCURY -> new ColorRGBA(0.70f, 0.70f, 0.70f, 1.0f);
      case VENUS -> new ColorRGBA(0.95f, 0.85f, 0.40f, 1.0f);
      case EARTH -> new ColorRGBA(0.35f, 0.55f, 1.00f, 1.0f); // teinte bleue
      case MARS -> new ColorRGBA(1.00f, 0.35f, 0.25f, 1.0f);
      case JUPITER -> new ColorRGBA(0.85f, 0.65f, 0.45f, 1.0f);
      case SATURN -> new ColorRGBA(0.95f, 0.85f, 0.55f, 1.0f);
      case URANUS -> new ColorRGBA(0.50f, 0.90f, 0.90f, 1.0f);
      case NEPTUNE -> new ColorRGBA(0.30f, 0.45f, 0.95f, 1.0f);
      case PLUTO -> new ColorRGBA(0.80f, 0.75f, 0.70f, 1.0f);
      case MOON -> new ColorRGBA(0.75f, 0.75f, 0.75f, 1.0f);
      case SUN -> new ColorRGBA(1.00f, 0.95f, 0.50f, 1.0f);
    };
  }
}
