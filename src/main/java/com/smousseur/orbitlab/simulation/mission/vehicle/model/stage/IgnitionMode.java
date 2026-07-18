package com.smousseur.orbitlab.simulation.mission.vehicle.model.stage;

/** How a stage's engines are ignited. */
public enum IgnitionMode {
  /** Lit on the pad — candidate for the initial ascent phase. */
  GROUND,
  /** Lit in flight — upper stage or kick stage. */
  AIRSTART
}
