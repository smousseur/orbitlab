package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

public record GravityTurnConstraints(
    double targetApogee,
    double maxApogee,
    double minTangentialVelocity,
    double targetFlightPathAngleDeg) {

  /** Convenience constructor with sensible defaults. */
  public GravityTurnConstraints(double targetApogee, double maxApogee) {
    this(targetApogee, maxApogee, 1500.0, 20.0);
  }
}
