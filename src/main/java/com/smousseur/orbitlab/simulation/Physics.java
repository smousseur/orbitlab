package com.smousseur.orbitlab.simulation;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Utility class providing orbital mechanics and flight dynamics computations.
 *
 * <p>Includes methods for radial velocity calculation, burn duration estimation via the
 * Tsiolkovsky equation, thrust direction construction, launch azimuth determination,
 * and pitch kick maneuvers.
 */
public final class Physics {
  private Physics() {}

  /**
   * Computes the radial velocity component of a spacecraft state.
   *
   * <p>Radial velocity is the projection of the velocity vector onto the position direction,
   * calculated as the dot product of position and velocity divided by the position magnitude.
   *
   * @param state the spacecraft state containing position and velocity
   * @return the radial velocity in m/s (positive = moving away from center)
   */
  public static double computeRadialVelocity(SpacecraftState state) {
    Vector3D position = state.getPVCoordinates().getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    return Vector3D.dotProduct(position, velocity) / position.getNorm();
  }

  /**
   * Converts a delta-V to a burn duration using the Tsiolkovsky rocket equation.
   *
   * <p>The formula is: {@code dt = (m * Isp * g0 / F) * (1 - exp(-dv / (Isp * g0)))}
   *
   * @param dv the desired velocity change in m/s
   * @param mass the initial spacecraft mass in kg
   * @param isp the specific impulse in seconds
   * @param thrust the engine thrust in Newtons
   * @return the required burn duration in seconds
   */
  public static double computeBurnDuration(double dv, double mass, double isp, double thrust) {
    double ve = isp * Constants.G0_STANDARD_GRAVITY; // exhaust velocity
    return (mass * ve / thrust) * (1.0 - FastMath.exp(-dv / ve));
  }

  /**
   * Builds a thrust direction vector in the TNW (tangential, normal, out-of-plane) frame
   * from in-plane and out-of-plane angles.
   *
   * <p>When both angles are zero, the result is pure tangential prograde thrust.
   *
   * @param alpha in-plane angle from the tangential direction (radians)
   * @param beta out-of-plane angle (radians)
   * @return the unit thrust direction vector in TNW coordinates
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
   * Returns the default launch azimuth for an equatorial due-east launch (90 degrees).
   *
   * @return the launch azimuth in radians
   */
  public static double getLaunchAzimuth() {
    return getLaunchAzimuth(0, 0);
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
      double cosLat = FastMath.cos(launchLatitude);
      if (FastMath.abs(cosLat) < 1e-10) {
        throw new IllegalArgumentException(
            "Launch latitude too close to a pole, azimuth is undefined: " + launchLatitude);
      }
      result = FastMath.asin(FastMath.cos(targetInclination) / cosLat);
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
   * Returns the square of a value.
   *
   * @param x the value to square
   * @return {@code x * x}
   */
  public static double sq(double x) {
    return x * x;
  }
}
