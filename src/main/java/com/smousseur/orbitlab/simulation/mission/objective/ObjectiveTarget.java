package com.smousseur.orbitlab.simulation.mission.objective;

/**
 * Represents the target value of a mission objective. Implementations define what constitutes
 * "success" for a particular type of objective (e.g., a target orbital altitude or eccentricity).
 */
public interface ObjectiveTarget {

  /**
   * Returns a scalar representation of this target, suitable for comparison and error computation.
   * For orbital targets, this is typically the target orbital radius in meters.
   *
   * @return the target value as a scalar
   */
  double getAsScalar();
}
