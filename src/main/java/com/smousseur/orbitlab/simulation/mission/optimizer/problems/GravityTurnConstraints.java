package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

/**
 * Defines the target constraints for gravity turn optimization.
 *
 * <p>These constraints specify the acceptable apogee window, minimum tangential velocity, and
 * target flight path angle that the gravity turn must achieve before handing off to the transfer
 * stage.
 *
 * @param targetApogee desired apogee altitude in meters at the end of the gravity turn
 * @param maxApogee maximum allowable apogee altitude in meters
 * @param minTangentialVelocity minimum tangential velocity in m/s required for orbit insertion
 * @param targetFlightPathAngleDeg target flight path angle in degrees (small values indicate
 *     near-horizontal flight)
 */
public record GravityTurnConstraints(
    double targetApogee,
    double maxApogee,
    double minTangentialVelocity,
    double targetFlightPathAngleDeg) {

  /**
   * Creates constraints with sensible defaults for minimum tangential velocity (1500 m/s) and
   * target flight path angle (20 degrees).
   *
   * @param targetApogee desired apogee altitude in meters
   * @param maxApogee maximum allowable apogee altitude in meters
   */
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
   *   <li>targetApogee = targetAltitude x 0.75
   *   <li>maxApogee = targetAltitude x 0.875
   * </ul>
   *
   * @param targetAltitude the mission's target orbital altitude in meters
   * @return constraints suitable for a gravity turn targeting the given altitude
   */
  public static GravityTurnConstraints forTarget(double targetAltitude) {
    return new GravityTurnConstraints(
        targetAltitude * 0.75, targetAltitude * 0.875, 2000.0, 2.0);
  }
}
