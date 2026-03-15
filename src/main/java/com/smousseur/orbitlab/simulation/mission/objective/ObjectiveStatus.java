package com.smousseur.orbitlab.simulation.mission.objective;

/**
 * Mutable container for the error residuals produced by a {@link MissionObjective} evaluation.
 * The optimizer reads the error array to compute the fitness value for a given trajectory.
 */
public class ObjectiveStatus {
  private double[] error;

  /**
   * Returns the error residuals from the most recent objective evaluation.
   *
   * @return the array of error residuals, or {@code null} if not yet evaluated
   */
  public double[] getError() {
    return error;
  }

  /**
   * Sets the error residuals from an objective evaluation.
   *
   * @param error the array of error residuals
   */
  public void setError(double[] error) {
    this.error = error;
  }
}
