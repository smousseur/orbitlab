package com.smousseur.orbitlab.simulation;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Utility class providing orbital mechanics and flight dynamics computations.
 *
 * <p>Includes methods for radial velocity calculation, burn duration estimation via the Tsiolkovsky
 * equation, thrust direction construction, launch azimuth determination, and pitch kick maneuvers.
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
   * Computes the finite-burn duration for a ΔV, capped by the propellant actually available in the
   * burning stage. An undersized stage burns to depletion and stops — a clean under-performance
   * caught by the mission objective — instead of consuming propellant it does not carry.
   *
   * @param dv the required velocity change in m/s
   * @param mass the spacecraft mass at ignition in kg
   * @param isp the specific impulse in seconds
   * @param thrust the engine thrust in Newtons
   * @param remainingFuel the propellant available in the burning stage in kg
   * @return the burn duration in seconds
   */
  public static double computeBurnDurationCapped(
      double dv, double mass, double isp, double thrust, double remainingFuel) {
    double massFlow = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    double depletionDuration = FastMath.max(0.0, remainingFuel) / massFlow;
    return FastMath.min(computeBurnDuration(dv, mass, isp, thrust), depletionDuration);
  }

  /**
   * Builds a thrust direction vector in the TNW (tangential, normal, out-of-plane) frame from
   * in-plane and out-of-plane angles.
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
   * Returns the due-east launch azimuth (90°), the heading of every profile that asks for no
   * particular plane and simply takes the one the site's latitude gives for free.
   *
   * <p><b>The general derivation no longer lives here</b> (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §1.1 and §3.1). The two-argument overload
   * this class used to carry mixed units with its callers — it consumed radians while the ascent
   * documented degrees — and mis-guarded the equatorial polar case, both invisibly, because every
   * caller passed {@code (0, 0)}. Azimuth derivation is now {@code LaunchPlane}'s, the one type that
   * also knows which of the two branches reaching an inclination is being flown and whether the site
   * reaches it at all.
   *
   * @return the launch azimuth in radians, clockwise from north
   */
  public static double getLaunchAzimuth() {
    return FastMath.PI / 2;
  }

  /**
   * Builds the unit horizontal direction pointing at a given azimuth from a given position, in the
   * local topocentric basis {@code (north, east)}.
   *
   * <p><b>The one place that basis is written.</b> The pitch kick and the commanded-plane attitude
   * both need it, and they must agree: a launch commanded at azimuth {@code A} whose kick and whose
   * target plane disagreed on where east is would fly a mirrored plane with a perfectly correct
   * inclination, which no inclination assertion can catch (spec {@code
   * docs/earth-orbit/01-mission-terre-parametrable.md} §4.1).
   *
   * @param position the position the local frame is built at (inertial)
   * @param azimuth the azimuth in radians, clockwise from north — 90° is due east
   * @return the unit horizontal direction at that azimuth
   */
  public static Vector3D localHorizontalDirection(Vector3D position, double azimuth) {
    Vector3D zenith = position.normalize();
    Vector3D northPole = Vector3D.PLUS_K;
    Vector3D north =
        northPole
            .subtract(new Vector3D(Vector3D.dotProduct(northPole, zenith), zenith))
            .normalize();
    // Geographic east (spec §1.1c). This used to read zenith × north, which is WEST: at the equator
    // r̂ = x̂, n̂ = ẑ and x̂ × ẑ = −ŷ, while east is +ŷ. The kick's azimuths were therefore
    // counter-clockwise from north — 0° and 180° right, 90° pointing due west — and every standard
    // azimuth handed to it was mirrored, A → −A. It stayed invisible because every mission commands
    // 90°, where the kick has no authority anyway: the ~2.5 m/s it misplaced were lost in 463 m/s
    // of eastward entrainment, and the mirror is symmetric about the site meridian, so it moved the
    // node and not the plane — which is why no inclination assertion ever saw it.
    Vector3D east = Vector3D.crossProduct(north, zenith).normalize();
    return new Vector3D(
        FastMath.cos(azimuth), north,
        FastMath.sin(azimuth), east);
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

    Vector3D zenith = pos.normalize();
    Vector3D azimuthDir = localHorizontalDirection(pos, launchAzimuth);

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
   * Reads the osculating inclination of a state's orbit, in degrees.
   *
   * <p>The value is expressed in the frame the state is propagated in, which is the frame {@code
   * LaunchPlane.inclinationFrame()} declares for the target — the two are only comparable because
   * they are the same (spec {@code docs/earth-orbit/01-mission-terre-parametrable.md} §3.4).
   *
   * @param state the spacecraft state
   * @return the osculating inclination in degrees
   */
  public static double inclinationDeg(SpacecraftState state) {
    KeplerianOrbit orbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());
    return FastMath.toDegrees(orbit.getI());
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
