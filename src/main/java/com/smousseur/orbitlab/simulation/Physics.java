package com.smousseur.orbitlab.simulation;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public final class Physics {
  private Physics() {}

  /** Compute radial velocity (dot product of position and velocity divided by position norm). */
  public static double computeRadialVelocity(SpacecraftState state) {
    Vector3D position = state.getPVCoordinates().getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    return Vector3D.dotProduct(position, velocity) / position.getNorm();
  }

  /**
   * Convert a delta-V to a burn duration using the Tsiolkovski equation. dt = (m * Isp * g0 / F) *
   * (1 - exp(-dv / (Isp * g0)))
   */
  public static double computeBurnDuration(double dv, double mass, double isp, double thrust) {
    double ve = isp * Constants.G0_STANDARD_GRAVITY; // exhaust velocity
    return (mass * ve / thrust) * (1.0 - FastMath.exp(-dv / ve));
  }

  /**
   * Build thrust direction vector in TNW frame from in-plane and out-of-plane angles. alpha = 0,
   * beta = 0 means pure tangential prograde thrust.
   */
  public static Vector3D buildThrustDirectionTNW(double alpha, double beta) {
    double cosB = FastMath.cos(beta);
    return new Vector3D(
        cosB * FastMath.cos(alpha), // T component
        cosB * FastMath.sin(alpha), // N component
        FastMath.sin(beta) // W component
        );
  }

  /**
   * Gets launch azimuth.
   *
   * @param launchLatitude the launch latitude
   * @param targetInclination the target inclination
   * @return the launch azimuth
   */
  public static double getLaunchAzimuth(double launchLatitude, double targetInclination) {
    double result = FastMath.PI / 2; // 90° = due east
    if (launchLatitude != 0 && targetInclination != 0) {
      result = FastMath.asin(FastMath.cos(targetInclination) / FastMath.cos(launchLatitude));
    }
    return result;
  }

  /**
   * Apply instantaneous pitch kick: rotate the velocity vector by pitchKickAngle away from zenith,
   * toward the launch azimuth.
   *
   * @param state spacecraft state at end of vertical phase
   * @param pitchKickAngle kick angle from vertical (rad)
   * @param launchAzimuth azimuth direction for the kick (rad from North, clockwise) 90° = East
   *     (prograde equatorial)
   * @return new state with rotated velocity, same position and mass
   */
  public static SpacecraftState applyPitchKick(
      SpacecraftState state, double pitchKickAngle, double launchAzimuth) {
    Vector3D pos = state.getPVCoordinates().getPosition();
    Vector3D vel = state.getPVCoordinates().getVelocity();

    // Local topocentric frame
    Vector3D zenith = pos.normalize();
    Vector3D northPole = Vector3D.PLUS_K;
    Vector3D north =
        northPole
            .subtract(new Vector3D(Vector3D.dotProduct(northPole, zenith), zenith))
            .normalize();
    Vector3D east = Vector3D.crossProduct(zenith, north).normalize();

    // Kick direction in horizontal plane
    Vector3D azimuthDir =
        new Vector3D(
            FastMath.cos(launchAzimuth), north,
            FastMath.sin(launchAzimuth), east);

    // Instead of rotating velocity, compute the NEW thrust direction
    // and apply an instantaneous delta-v in that direction.
    // The kick "redirects" the vertical burn component, not the whole velocity.

    // Decompose velocity into:
    //  - radial (along zenith) = the part from the vertical burn
    //  - tangential (horizontal) = mostly Earth rotation
    double vRadial = Vector3D.dotProduct(vel, zenith);
    Vector3D vTangential = vel.subtract(new Vector3D(vRadial, zenith));

    // Rotate ONLY the radial component by pitchKickAngle
    // from zenith toward azimuthDir
    Vector3D newRadialDir =
        new Vector3D(
            FastMath.cos(pitchKickAngle), zenith, FastMath.sin(pitchKickAngle), azimuthDir);

    // Reconstruct velocity
    Vector3D newVel = new Vector3D(vRadial, newRadialDir).add(vTangential);

    PVCoordinates newPV = new PVCoordinates(pos, newVel);
    CartesianOrbit newOrbit =
        new CartesianOrbit(newPV, state.getFrame(), state.getDate(), state.getOrbit().getMu());

    return new SpacecraftState(newOrbit).withMass(state.getMass());
  }

  /**
   * Sq double.
   *
   * @param x the x
   * @return the double
   */
  public static double sq(double x) {
    return x * x;
  }

  public static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }
}
