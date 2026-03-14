package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.Objects;

/**
 * Immutable descriptor holding the visual identity of a planet for rendering purposes.
 *
 * @param body        the solar system body this descriptor represents
 * @param displayName the human-readable display name shown in the UI
 * @param orbitColor  the color used for the orbit line and icon rendering
 */
public record PlanetDescriptor(SolarSystemBody body, String displayName, ColorRGBA orbitColor) {
  public PlanetDescriptor {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(orbitColor, "orbitColor");
  }
}
