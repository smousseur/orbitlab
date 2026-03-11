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

  /**
   * Derives gravity-turn constraints from the mission target altitude.
   *
   * <p>The gravity turn ends on a sub-orbital trajectory whose apogee is below the target. The
   * transfer stage then raises the orbit to the target. Calibrated so that for a 400 km target the
   * constraints reproduce the known-good values (300 km / 350 km).
   *
   * <ul>
   *   <li>targetApogee = targetAltitude × 0.75
   *   <li>maxApogee = targetAltitude × 0.875
   * </ul>
   */
  public static GravityTurnConstraints forTarget(double targetAltitude) {
    return new GravityTurnConstraints(
        targetAltitude * 0.75, targetAltitude * 0.875, 2000.0, 2.0);
  }
}
