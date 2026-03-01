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
    // At 185 km: MECO ~50 km, apogee target ~155 km, max apogee ~200 km, FPA ~10°
    // At 400 km: MECO  65 km, apogee target  200 km, max apogee  350 km, FPA  15°

    // Non-linear blend: use t^0.6 to give more weight to low-altitude behavior
    double blend = FastMath.pow(t, 0.6);

    // MECO altitude
    double mecoAltitude = lerp(50_000, 65_000, blend);
    mecoAltitude = FastMath.clamp(mecoAltitude, 45_000, 200_000);

    // Target apogee — wider window for better optimizer convergence
    double targetApogee = lerp(155_000, 200_000, blend);
    targetApogee = FastMath.max(targetApogee, mecoAltitude * 2.0);

    // Max apogee — generous window so the optimizer can explore freely
    double maxApogee = lerp(200_000, 350_000, blend);
    maxApogee = FastMath.max(maxApogee, targetApogee * 1.25);

    // Min tangential velocity (~25% of v_circ at target)
    double rTarget = rEarth + missionTargetAltitude;
    double vCircTarget = FastMath.sqrt(mu / rTarget);
    double minTangentialVelocity = vCircTarget * 0.25;

    // Flight path angle: lower orbit = more horizontal MECO
    double fpa = lerp(10.0, 15.0, blend);
    fpa = FastMath.clamp(fpa, 8.0, 28.0);

    return new GravityTurnConstraints(
        mecoAltitude, targetApogee, maxApogee, minTangentialVelocity, fpa);
  }
}
