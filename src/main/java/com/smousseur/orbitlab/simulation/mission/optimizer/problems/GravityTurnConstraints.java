package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import org.hipparchus.util.FastMath;
import org.hipparchus.util.Pair;
import org.orekit.utils.Constants;

/**
 * Defines the target constraints for gravity turn optimization.
 *
 * <p>These constraints specify the mission target altitude, the acceptable apogee window, the
 * minimum tangential velocity, and the flight path angle window that the gravity turn must
 * achieve before handing off to the transfer stage.
 *
 * @param targetAltitude mission target orbital altitude in meters
 * @param targetApogee desired apogee altitude in meters at the end of the gravity turn
 * @param maxApogee maximum allowable apogee altitude in meters
 * @param minTangentialVelocity minimum tangential velocity in m/s required for orbit insertion
 * @param targetFlightPathAngleMinDeg lower bound of the acceptable flight path angle window
 *     (degrees)
 * @param targetFlightPathAngleMaxDeg upper bound of the acceptable flight path angle window
 *     (degrees)
 */
public record GravityTurnConstraints(
    double targetAltitude,
    double targetApogee,
    double maxApogee,
    double minTangentialVelocity,
    double targetFlightPathAngleMinDeg,
    double targetFlightPathAngleMaxDeg) {

  /**
   * Derives gravity-turn constraints from the mission target altitude.
   *
   * <p>The gravity turn ends on a sub-orbital trajectory whose apogee is below the target. The
   * transfer stage then raises the orbit to the target.
   *
   * @param targetAltitude the mission's target orbital altitude in meters
   * @return constraints suitable for a gravity turn targeting the given altitude
   */
  public static GravityTurnConstraints forTarget(double targetAltitude) {
    Pair<Double, Double> apogeeDatas = getApogeeTargetAndMax(targetAltitude);
    double apogeeTarget = apogeeDatas.getFirst();
    double apogeeMax = apogeeDatas.getSecond();
    double vTanMin = getVTanMin(apogeeTarget, targetAltitude);
    double[] fpaWindow = getFpaWindowDeg(targetAltitude);

    return new GravityTurnConstraints(
        targetAltitude, apogeeTarget, apogeeMax, vTanMin, fpaWindow[0], fpaWindow[1]);
  }

  private static Pair<Double, Double> getApogeeTargetAndMax(double targetAltitude) {
    double targetKm = targetAltitude / 1000.0;
    double ratioApo;
    if (targetKm <= 250) ratioApo = 0.95;
    else if (targetKm <= 800) ratioApo = 0.95 + (0.75 - 0.95) * (targetKm - 250) / (800 - 250);
    else ratioApo = 0.75;

    double apogeeTarget = ratioApo * targetAltitude;
    double apogeeMinSafe = FastMath.max(140_000, 0.6 * targetAltitude);
    apogeeTarget = FastMath.max(apogeeTarget, apogeeMinSafe);
    double apogeeMax = FastMath.min(targetAltitude, apogeeTarget * 1.15);
    apogeeMax = FastMath.max(apogeeMax, apogeeTarget);
    return new Pair<>(apogeeTarget, apogeeMax);
  }

  private static double getVTanMin(double apogeeTarget, double targetAltitude) {
    double rGtEnd = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + apogeeTarget;
    double rTarget = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + targetAltitude;
    double aTransfer = (rGtEnd + rTarget) / 2.0;
    double vMin = FastMath.sqrt(Constants.WGS84_EARTH_MU * (2.0 / rGtEnd - 1.0 / aTransfer));
    return vMin * 0.95; // -5% margin
  }

  private static double[] getFpaWindowDeg(double targetAltitude) {
    double altKm = targetAltitude / 1000.0;
    double fpaMaxDeg;
    if (altKm <= 185.0) fpaMaxDeg = 0.3;
    else if (altKm <= 400.0) fpaMaxDeg = lerp(altKm, 185.0, 400.0, 0.3, 1.5);
    else if (altKm <= 600.0) fpaMaxDeg = lerp(altKm, 400.0, 600.0, 1.5, 2.2);
    else if (altKm <= 1500.0) fpaMaxDeg = lerp(altKm, 600.0, 1500.0, 2.2, 5.0);
    else fpaMaxDeg = 5.0 + (altKm - 1500.0) * (5.0 / 1500.0);

    double margin = 1.0;
    // Allow a small negative FPA at low altitudes so the admissible window isn't
    // contradictory with a short gravity turn that finishes slightly descending.
    double fpaMinDeg = altKm <= 250.0 ? -0.5 : 0.0;
    return new double[] {fpaMinDeg, fpaMaxDeg + margin};
  }

  private static double lerp(double x, double x0, double x1, double y0, double y1) {
    return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
  }
}
