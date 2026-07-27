package com.smousseur.orbitlab.simulation.mission;

public enum MissionType {
  LEO,
  GEO;

  /**
   * Returns the human-readable label for this type. Goes through this accessor rather than {@link
   * #name()} so the UI never depends on the enum constant spelling.
   *
   * @return the label shown in the UI
   */
  public String displayName() {
    return name();
  }
}
