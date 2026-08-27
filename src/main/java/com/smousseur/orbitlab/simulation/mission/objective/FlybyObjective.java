package com.smousseur.orbitlab.simulation.mission.objective;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Objects;

/**
 * Objective describing a flyby: the trajectory must pass a celestial body at a given closest
 * approach altitude, without inserting into orbit around it.
 *
 * <p><b>Only the closest approach describes the intention.</b> An insertion objective is bounded on
 * both sides — a perigee and an apogee — because an orbit is. A flyby is not: the highest altitude
 * reached on the flown arc is where the trajectory entered the body's sphere of influence, which is
 * a consequence of the geometry and describes nothing anyone aimed for. On the PHY-4 translunar
 * demo that maximum is 67 348 km against a 100 km perilune (spec {@code
 * docs/lunar-flyby/02-baseline-L0.md} §4), which is why the objective this record replaces could
 * never be met.
 *
 * <p><b>The tolerance is carried here, and it is absolute</b> — unlike {@link
 * OrbitInsertionObjective}, which leaves its band to the caller as a ratio. Two targets are covered
 * together by a ratio and callers genuinely choose their band there (±7 % for LEO, ±50 km for GEO).
 * A flyby has one target, and its band is not a caller's choice but a property of the measurement:
 * ~0.9 km of over-read of closest approach at the 60 s coast sampling step, plus the ~1 km the aim
 * secant converges to. Both errors are absolute, so a ratio would mean 7 km on a 100 km approach
 * and 7 000 km on a 100 000 km one — the same nominal band describing two unrelated requirements.
 *
 * @param body the body being flown past
 * @param closestApproachAltitude the aimed closest approach altitude, in meters above the body's
 *     surface — named for the measured quantity rather than after a body-specific term, since the
 *     record is not lunar
 * @param toleranceMeters the ± band on the flown closest approach, in meters
 */
public record FlybyObjective(
    SolarSystemBody body, double closestApproachAltitude, double toleranceMeters)
    implements MissionObjective {

  /**
   * A closest approach at or below the surface is not a mistuned target, it is an impact, and the
   * caller is asking for something no trajectory can satisfy.
   */
  public FlybyObjective {
    Objects.requireNonNull(body, "body");
    if (!Double.isFinite(closestApproachAltitude) || closestApproachAltitude <= 0.0) {
      throw new IllegalArgumentException(
          "closest approach altitude must be finite and above the surface, got "
              + closestApproachAltitude);
    }
    if (!Double.isFinite(toleranceMeters) || toleranceMeters <= 0.0) {
      throw new IllegalArgumentException(
          "tolerance must be finite and positive, got " + toleranceMeters);
    }
  }
}
