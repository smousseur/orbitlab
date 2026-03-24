package com.smousseur.orbitlab.core;

import java.util.List;
import java.util.Objects;

/**
 * Enumeration of the solar system bodies supported by the simulation.
 *
 * <p>Each body has a human-readable display name and an optional parent body representing the
 * primary it orbits. Planets orbit the Sun; natural satellites orbit their parent planet.
 */
public enum SolarSystemBody {
  SUN("Sun", null),
  MERCURY("Mercury", SUN),
  VENUS("Venus", SUN),
  EARTH("Earth", SUN),
  MARS("Mars", SUN),
  JUPITER("Jupiter", SUN),
  SATURN("Saturn", SUN),
  URANUS("Uranus", SUN),
  NEPTUNE("Neptune", SUN),
  PLUTO("Pluto", SUN),
  MOON("Moon", EARTH);

  private final String displayName;
  private final SolarSystemBody parent;

  SolarSystemBody(String displayName, SolarSystemBody parent) {
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.parent = parent;
  }

  /**
   * Returns the human-readable display name for this celestial body.
   *
   * @return the display name (e.g., "Earth", "Jupiter")
   */
  public String displayName() {
    return displayName;
  }

  /**
   * Returns the parent body that this body orbits, or {@code null} for the Sun.
   *
   * @return the parent body, or {@code null} if this is the Sun
   */
  public SolarSystemBody parent() {
    return parent;
  }

  /**
   * Returns {@code true} if this body is a natural satellite (orbits a planet, not the Sun).
   *
   * @return {@code true} for satellites like the Moon, {@code false} for planets and the Sun
   */
  public boolean isSatellite() {
    return parent != null && parent != SUN;
  }
}
