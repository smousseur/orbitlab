package com.smousseur.orbitlab.simulation.mission.objective;

import com.smousseur.orbitlab.core.SolarSystemBody;

/**
 * Declarative description of a mission goal. New objective kinds (rendezvous, specific orbit,
 * etc.) are added by declaring a new record that implements this sealed interface.
 */
public sealed interface MissionObjective permits OrbitInsertionObjective {
  /** The central body the mission is targeted at (used to anchor rendering and view focus). */
  SolarSystemBody body();
}
