package com.smousseur.orbitlab.simulation.mission;

import org.orekit.propagation.SpacecraftState;

/**
 * Listener interface for receiving notifications about mission lifecycle events. Implementations
 * can react to stage transitions, for example to update the UI or record telemetry.
 */
public interface MissionListener {

  /**
   * Called when the mission transitions from one stage to the next.
   *
   * @param mission the mission that is transitioning
   * @param stateAtEvent the spacecraft state at the moment of the stage transition
   */
  void onStageTransition(Mission mission, SpacecraftState stateAtEvent);
}
