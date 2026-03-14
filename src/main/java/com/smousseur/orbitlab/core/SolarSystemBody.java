package com.smousseur.orbitlab.core;

import java.util.List;
import java.util.Objects;

/**
 * Enumeration of the major solar system bodies supported by the simulation.
 *
 * <p>Each body has a human-readable display name used in the UI and logging. The enum values
 * correspond to the celestial bodies for which Orekit can compute ephemeris data.
 */
public enum SolarSystemBody {
  SUN("Sun"),
  MERCURY("Mercury"),
  VENUS("Venus"),
  EARTH("Earth"),
  MARS("Mars"),
  JUPITER("Jupiter"),
  SATURN("Saturn"),
  URANUS("Uranus"),
  NEPTUNE("Neptune"),
  PLUTO("Pluto");

  private final String displayName;

  SolarSystemBody(String displayName) {
    this.displayName = Objects.requireNonNull(displayName, "displayName");
  }

  /**
   * Returns the human-readable display name for this celestial body.
   *
   * @return the display name (e.g., "Earth", "Jupiter")
   */
  public String displayName() {
    return displayName;
  }
}
