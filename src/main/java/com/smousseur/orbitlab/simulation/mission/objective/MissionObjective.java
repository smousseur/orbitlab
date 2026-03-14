package com.smousseur.orbitlab.simulation.mission.objective;

import org.orekit.propagation.SpacecraftState;

/**
 * Defines the goal of a mission and provides a way to evaluate how well the current spacecraft
 * state meets that goal. Implementations compute error residuals that the optimizer minimizes.
 */
public interface MissionObjective {

  /**
   * Returns the target that this objective is trying to achieve.
   *
   * @return the objective target
   */
  ObjectiveTarget getTarget();

  /**
   * Evaluates the current spacecraft state against the objective target and records the error
   * residuals in the given status object. The residuals are used by the optimizer's fitness
   * function.
   *
   * @param status the status object in which to store the computed error residuals
   * @param state the current spacecraft state to evaluate
   */
  void evaluate(ObjectiveStatus status, SpacecraftState state);
}
