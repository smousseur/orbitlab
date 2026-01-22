package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.Objects;

public record PlanetDescriptor(SolarSystemBody body, String displayName, ColorRGBA orbitColor) {
  public PlanetDescriptor {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(orbitColor, "orbitColor");
  }
}
