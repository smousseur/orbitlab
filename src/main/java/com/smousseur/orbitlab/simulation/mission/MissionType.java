package com.smousseur.orbitlab.simulation.mission;

public enum MissionType {
  /**
   * Nothing is handed over to the payload here, so a propelled one keeps whatever its own ΔV budget
   * loaded: since PHY-8 / L6 that tank is filled by {@code PropellantBudget.loadsForLeo} and rides
   * to orbit unburnt, the direct chain never dropping the upper stage that flies the trim.
   */
  LEO("LEO", false),

  /** Delegates the apogee circularization to the payload's kick motor. */
  GEO("GEO", true),

  /**
   * Ground to a lunar flyby (MIS-4). The payload is inert: the translunar injection is the last
   * burn of the chain and nothing is handed over afterwards, so no kick motor is required.
   */
  LUNAR_FLYBY("LUNAR FLYBY", false),

  /**
   * Ground to a circular lunar orbit (MIS-5). The launcher's top stage is dropped just after the
   * translunar injection, so the insertion burn is the payload's own: no launcher stage can hold a
   * four-day coast (découpage §2.3 pt 1), and an inert probe cannot fly this at all.
   */
  LUNAR_ORBIT("LUNAR ORBIT", true);

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
   * @return true when the payload must carry propulsion of its own
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
