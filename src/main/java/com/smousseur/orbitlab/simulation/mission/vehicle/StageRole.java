package com.smousseur.orbitlab.simulation.mission.vehicle;

/** Intended role of the stage in a flight profile (hint for profile derivation). */
public enum StageRole {
  /** Strap-on, jettisoned mid-ascent. */
  BOOSTER,
  /** Main/sustainer stage, ground-lit. */
  CORE,
  /** Orbital stage. */
  UPPER,
  /** Payload-integrated apogee motor (AKM). */
  KICK
}
