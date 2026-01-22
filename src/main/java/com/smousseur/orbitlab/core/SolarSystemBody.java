package com.smousseur.orbitlab.core;

import java.util.List;
import java.util.Objects;

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

  public String displayName() {
    return displayName;
  }
}
