package com.smousseur.orbitlab.simulation.mission;

public enum MissionType {
  /**
   * Nothing is handed over to the payload here, so a propelled one keeps whatever its own ΔV budget
   * loaded: since PHY-8 / L6 that tank is filled by {@code PropellantBudget.loadsForLeo} and rides
   * to orbit unburnt, the direct chain never dropping the upper stage that flies the trim.
   *
   * <p>It hands the payload no in-flight burn, yet it delivers it to a <em>stable</em> orbit: the
   * satellite stays, so PHY-10 requires it to carry propulsion to dispose of itself at end of life.
   * This is the one type where the two predicates disagree.
   */
  LEO("LEO", false, true),

  /** Delegates the apogee circularization to the payload's kick motor. */
  GEO("GEO", true, true),

  /**
   * Ground to a lunar flyby (MIS-4). The payload is inert: the translunar injection is the last
   * burn of the chain and nothing is handed over afterwards, so no kick motor is required. It does
   * not stay in orbit, so PHY-10 does not require it to carry disposal propulsion either.
   */
  LUNAR_FLYBY("LUNAR FLYBY", false, false),

  /**
   * Ground to a circular lunar orbit (MIS-5). The launcher's top stage is dropped just after the
   * translunar injection, so the insertion burn is the payload's own: no launcher stage can hold a
   * four-day coast, and an inert probe cannot fly this at all.
   */
  LUNAR_ORBIT("LUNAR ORBIT", true, true);

  private final boolean requiresPayloadPropulsion;

  private final boolean deliversToStableOrbit;

  private final String displayName;

  MissionType(
      String displayName, boolean requiresPayloadPropulsion, boolean deliversToStableOrbit) {
    this.displayName = displayName;
    this.requiresPayloadPropulsion = requiresPayloadPropulsion;
    this.deliversToStableOrbit = deliversToStableOrbit;
  }

  /**
   * Tells whether this mission type can only be flown by a payload carrying its own propulsion. GEO
   * hands the apogee circularization to the payload's kick motor whatever the {@code
   * OptimizationType} — {@code MissionComposer} offers a single (analytic) GEO composition — so an
   * inert payload cannot fly it.
   *
   * <p><b>Different from {@link #deliversToStableOrbit()}</b>, which they only disagree on for LEO:
   * this one asks whether the mission delegates a burn to the payload <em>during</em> the flight,
   * the other whether the payload <em>stays</em> in orbit and must be able to dispose of itself.
   *
   * @return true when the payload must carry propulsion of its own
   */
  public boolean requiresPayloadPropulsion() {
    return requiresPayloadPropulsion;
  }

  /**
   * Tells whether this mission type leaves its payload in a stable orbit — where it must be able to
   * dispose of itself at end of life (PHY-10). True for every orbital type (LEO, GEO, LUNAR_ORBIT);
   * false for a lunar flyby, which passes the Moon and does not stay.
   *
   * @return true when the payload is delivered to a stable orbit
   */
  public boolean deliversToStableOrbit() {
    return deliversToStableOrbit;
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
