package com.smousseur.orbitlab.simulation.mission.vehicle.model.stage;

/** Intended role of the stage in a flight profile (hint for profile derivation). */
public enum StageRole {
  /** Strap-on, jettisoned mid-ascent. */
  BOOSTER,
  /** Main/sustainer stage, ground-lit. */
  CORE,
  /** Orbital stage. */
  UPPER,
  /**
   * Payload-integrated motor. Named for the apogee kick motor it was written for, and kept general
   * since: the catalog also holds a 5 500 N lunar insertion engine and a station-keeping thruster
   * under it (spec {@code docs/etagement/01-decoupage.md} §3.6).
   */
  KICK
}
