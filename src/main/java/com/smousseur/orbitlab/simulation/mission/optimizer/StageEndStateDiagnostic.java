package com.smousseur.orbitlab.simulation.mission.optimizer;

import java.util.Locale;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Stateless diagnostics for the spacecraft state at the end of an optimized stage.
 *
 * <p>Used to compare the actual gravity-turn handoff state with the ideal Hohmann
 * starting state for the mission's target altitude — see
 * {@code specs/optimizer/03-robustness-roadmap.md} §0.1 (« État de fin de GT »).
 */
public final class StageEndStateDiagnostic {

  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final double MU = Constants.WGS84_EARTH_MU;

  private StageEndStateDiagnostic() {}

  /**
   * Snapshot of the kinematic indicators relevant to the GT → transfer handoff.
   *
   * @param altitude geometric altitude above Earth's mean radius (m)
   * @param vTan tangential velocity (m/s)
   * @param vRad radial velocity (m/s, positive = outward)
   * @param fpaDeg flight-path angle (degrees, positive = climbing)
   */
  public record EndState(double altitude, double vTan, double vRad, double fpaDeg) {}

  /**
   * Extracts the end-state indicators from a propagated spacecraft state.
   *
   * @param state the spacecraft state (typically the {@code bestState} of an optimization run)
   * @return the snapshot
   */
  public static EndState from(SpacecraftState state) {
    PVCoordinates pv = state.getPVCoordinates();
    Vector3D pos = pv.getPosition();
    Vector3D vel = pv.getVelocity();

    double r = pos.getNorm();
    double altitude = r - EARTH_RADIUS;
    Vector3D zenith = pos.normalize();
    double vRad = Vector3D.dotProduct(vel, zenith);
    double vNorm = vel.getNorm();
    double vTanSq = vNorm * vNorm - vRad * vRad;
    double vTan = vTanSq > 0 ? FastMath.sqrt(vTanSq) : 0.0;
    double fpaDeg = FastMath.toDegrees(FastMath.atan2(vRad, vTan));
    return new EndState(altitude, vTan, vRad, fpaDeg);
  }

  /**
   * Returns the ideal handoff state at the end of a gravity turn so that a pure
   * tangential ballistic arc reaches {@code targetAltitude}.
   *
   * <p>Vis-viva on a Hohmann transfer arc connecting the current altitude
   * (assumed to be the GT exit) to the target circular orbit. The ideal {@code vRad}
   * and {@code fpa} are zero by definition (handoff at periapsis of the transfer
   * ellipse), and {@code vTan} is the periapsis velocity of that ellipse.
   *
   * @param targetAltitude target circular orbit altitude (m)
   * @param currentAltitude actual GT exit altitude (m)
   * @return the ideal end-state at the same altitude
   */
  public static EndState idealHohmannHandoff(double targetAltitude, double currentAltitude) {
    double rGtEnd = EARTH_RADIUS + currentAltitude;
    double rTarget = EARTH_RADIUS + targetAltitude;
    double aTransfer = (rGtEnd + rTarget) / 2.0;
    double vIdeal = FastMath.sqrt(MU * (2.0 / rGtEnd - 1.0 / aTransfer));
    return new EndState(currentAltitude, vIdeal, 0.0, 0.0);
  }

  /**
   * Formats actual vs. ideal as a single-line diagnostic.
   *
   * <p>Example:
   * {@code alt=348142 m | vTan=7320 m/s (Δ=+12) | vRad=132 m/s (Δ=+132) | FPA=2.10° (Δ=+2.10°)}
   *
   * @param actual measured end state
   * @param ideal computed Hohmann reference at the same altitude
   * @return formatted string
   */
  public static String format(EndState actual, EndState ideal) {
    return String.format(
        Locale.ROOT,
        "alt=%.0f m | vTan=%.1f m/s (Δ=%+.1f) | vRad=%.1f m/s (Δ=%+.1f) | FPA=%.2f° (Δ=%+.2f°)",
        actual.altitude(),
        actual.vTan(),
        actual.vTan() - ideal.vTan(),
        actual.vRad(),
        actual.vRad() - ideal.vRad(),
        actual.fpaDeg(),
        actual.fpaDeg() - ideal.fpaDeg());
  }
}
