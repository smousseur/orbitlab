package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import org.hipparchus.util.FastMath;
import org.hipparchus.util.Pair;
import org.orekit.utils.Constants;

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
    double vTanMin = getVTanMin(targetAltitude);
    Pair<Double, Double> apogeeDatas = getApogeeTargetAndMax(targetAltitude);

    Double rTarget = apogeeDatas.getFirst();
    Double rTargetMax = apogeeDatas.getSecond();

    return new GravityTurnConstraints(rTarget, rTargetMax, vTanMin, 2.0);
  }

  private static Pair<Double, Double> getApogeeTargetAndMax(double targetAltitude) {
    double targetKm = targetAltitude / 1000.0;
    double ratioApo;
    if (targetKm <= 250) ratioApo = 0.95;
    else if (targetKm <= 800) ratioApo = 0.95 + (0.75 - 0.95) * (targetKm - 250) / (800 - 250);
    else ratioApo = 0.75;

    double apogeeTarget = ratioApo * targetAltitude;
    double apogeeMax = FastMath.min(targetAltitude, apogeeTarget * 1.15);
    double apogeeMinSafe = FastMath.max(140_000, 0.6 * targetAltitude);
    apogeeTarget = FastMath.max(apogeeTarget, apogeeMinSafe);
    return new Pair<>(apogeeTarget, apogeeMax);
  }

  private static double getVTanMin(double targetAltitude) {
    double rGtEnd =
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + targetAltitude * 0.75; // = apogeeTarget
    double rTarget = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + targetAltitude;
    double aTransfer = (rGtEnd + rTarget) / 2.0;
    double vMin = FastMath.sqrt(Constants.WGS84_EARTH_MU * (2.0 / rGtEnd - 1.0 / aTransfer));
    return vMin * 0.95; // marge -5%
  }
}
