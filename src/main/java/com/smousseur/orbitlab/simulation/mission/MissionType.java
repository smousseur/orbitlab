package com.smousseur.orbitlab.simulation.mission;

public enum MissionType {
  /** The AKM has no role here: an AKM-equipped payload simply flies with an empty tank. */
  LEO("LEO", false),

  /** Delegates the apogee circularization to the payload's kick motor. */
  GEO("GEO", true),

  /**
   * Ground to a lunar flyby (MIS-4). The payload is inert: the translunar injection is the last
   * burn of the chain and nothing is handed over afterwards, so no kick motor is required.
   */
  LUNAR_FLYBY("LUNAR FLYBY", false);

  private final boolean requiresPayloadPropulsion;

  private final String displayName;

  MissionType(String displayName, boolean requiresPayloadPropulsion) {
    this.displayName = displayName;
    this.requiresPayloadPropulsion = requiresPayloadPropulsion;
  }

  /**
   * Tells whether this mission type can only be flown by a payload carrying its own propulsion. GEO
   * hands the apogee circularization to the payload's kick motor whatever the {@code
   * OptimizationType} — {@code MissionComposer} offers a single (analytic) GEO composition — so an
   * inert payload cannot fly it.
   *
   * @return true when the payload must have an AKM
   */
  public boolean requiresPayloadPropulsion() {
    return requiresPayloadPropulsion;
  }

  /**
   * Returns the human-readable label for this type.
   *
   * @return the label shown in the UI
   */
  public String displayName() {
    return displayName;
  }
}
