package com.smousseur.orbitlab.simulation.mission.objective;

import org.orekit.propagation.SpacecraftState;

/** The interface Mission objective. */
public interface MissionObjective {

  /**
   * Gets target.
   *
   * @return the target
   */
  ObjectiveTarget getTarget();

  /**
   * Evaluate.
   *
   * @param status the status
   * @param state the state
   */
  void evaluate(ObjectiveStatus status, SpacecraftState state);
}
