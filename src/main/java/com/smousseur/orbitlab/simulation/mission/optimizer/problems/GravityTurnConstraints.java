package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import org.hipparchus.util.FastMath;
import org.orekit.utils.Constants;

import static com.smousseur.orbitlab.simulation.Physics.lerp;

public record GravityTurnConstraints(
    double targetAltitude,
    double targetApogee,
    double maxApogee,
    double minTangentialVelocity,
    double targetFlightPathAngleDeg) {

  /** Convenience constructor with sensible defaults. */
  public GravityTurnConstraints(double targetAltitude, double targetApogee, double maxApogee) {
    this(targetAltitude, targetApogee, maxApogee, 1500.0, 20.0);
  }

  /**
   * Derives gravity turn end-of-burn constraints from the mission target altitude.
   *
   * <p>Uses two anchor points (185 km and 400 km) with non-linear interpolation/extrapolation. The
   * relationship is non-linear because lower orbits require the gravity turn to do proportionally
   * more work (higher MECO altitude ratio, higher apogee ratio).
   *
   * @param missionTargetAltitude the target circular orbit altitude (m)
   * @return constraints adapted to the mission
   */
  public static GravityTurnConstraints forMissionAltitude(double missionTargetAltitude) {
    double mu = Constants.WGS84_EARTH_MU;
    double rEarth = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

    // ── Blend factor: 0 at 185 km, 1 at 400 km, extrapolates beyond ──
    double t = (missionTargetAltitude - 185_000) / (400_000 - 185_000);
    t = FastMath.max(0, t); // clamp below

    // ── Anchor values (empirical) ──
    // At 185 km: MECO ~55 km, apogee target ~150 km, max apogee ~178 km, FPA ~12°
    // At 400 km: MECO  65 km, apogee target  170 km, max apogee  280 km, FPA  18°

    // Non-linear blend: use t^0.6 to give more weight to low-altitude behavior
    double blend = FastMath.pow(t, 0.6);

    // MECO altitude
    double mecoAltitude = lerp(55_000, 65_000, blend);
    mecoAltitude = FastMath.clamp(mecoAltitude, 50_000, 200_000);

    // Target apogee — at low alt, apogee must be very close to target
    double targetApogee = lerp(150_000, 170_000, blend);
    targetApogee = FastMath.max(targetApogee, mecoAltitude * 1.5);

    // Max apogee — at low alt, very tight window; at high alt, more margin
    double maxApogee = lerp(178_000, 280_000, blend);
    maxApogee = FastMath.max(maxApogee, targetApogee * 1.15);

    // Min tangential velocity (~20% of v_circ at target)
    double rTarget = rEarth + missionTargetAltitude;
    double vCircTarget = FastMath.sqrt(mu / rTarget);
    double minTangentialVelocity = vCircTarget * 0.20;

    // Flight path angle: lower orbit = more horizontal MECO
    double fpa = lerp(12.0, 18.0, blend);
    fpa = FastMath.clamp(fpa, 10.0, 28.0);

    return new GravityTurnConstraints(
        mecoAltitude, targetApogee, maxApogee, minTangentialVelocity, fpa);
  }
}
