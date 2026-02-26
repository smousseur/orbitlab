package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

public record GravityTurnConstraints(
    double targetAltitude,
    double targetApogee,
    double maxApogee,
    double minTangentialVelocity,
    double targetFlightPathAngleDeg) {

  /** Convenience constructor with sensible defaults. */
  public GravityTurnConstraints(double targetAltitude, double targetApogee, double maxApogee) {
    this(targetAltitude, targetApogee, maxApogee, 3000.0, 5.0);
  }
}
