package com.smousseur.orbitlab.simulation.mission.vehicle;

/** How a stage's burn ends. */
public enum ShutdownMode {
  /** Engine can be cut on command (DateDetector-style termination). */
  COMMANDED,
  /** Burns until flame-out (MassDepletionDetector-style termination). */
  BURN_TO_DEPLETION
}
