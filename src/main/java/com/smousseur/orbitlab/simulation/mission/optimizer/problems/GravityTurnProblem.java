package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import static com.smousseur.orbitlab.simulation.Physics.sq;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Trajectory optimization problem for the gravity turn phase of an ascent mission.
 *
 * <p>Optimizes two variables:
 *
 * <ul>
 *   <li><b>transitionTime</b> -- time at which the gravity turn ends (MECO)
 *   <li><b>exponent</b> -- pitch program exponent controlling the gravity turn profile
 * </ul>
 *
 * <p>The cost function penalizes deviations from the target apogee window, excessive flight path
 * angle, insufficient tangential velocity, unsafe altitudes, hyperbolic orbits, and propellant
 * consumption.
 */
public class GravityTurnProblem implements TrajectoryProblem {
  private static final double W_P = 9.e-5;

  private final GravityTurnManeuver maneuver;
  private final SpacecraftState initialState;
  private final GravityTurnConstraints constraints;

  /**
   * Creates a gravity turn optimization problem.
   *
   * @param maneuver the gravity turn maneuver that handles propagation
   * @param initialState the spacecraft state at the beginning of the gravity turn
   * @param constraints the target apogee, velocity, and flight path angle constraints
   */
  public GravityTurnProblem(
      GravityTurnManeuver maneuver,
      SpacecraftState initialState,
      GravityTurnConstraints constraints) {
    this.maneuver = maneuver;
    this.initialState = initialState;
    this.constraints = constraints;
  }

  @Override
  public int getNumVariables() {
    return 2;
  }

  @Override
  public double[] buildInitialGuess() {
    double burn1Duration = maneuver.getBurn1Duration();
    return new double[] {burn1Duration + 20.0, 1.0};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {30.0, 0.3};
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {450.0, 3.0};
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {30.0, 0.3};
  }

  @Override
  public double getAcceptableCost() {
    return 1e-4;
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    return maneuver.propagateForOptimization(initialState, variables);
  }

  @Override
  public double computeCost(SpacecraftState state) {
    // Detect penalty states: if propagation failed, the returned state is the initial state
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      return 1e6;
    }

    PVCoordinates pv = state.getPVCoordinates();
    Vector3D pos = pv.getPosition();
    Vector3D vel = pv.getVelocity();

    double alt = pos.getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    double vNorm = vel.getNorm();

    Vector3D zenith = pos.normalize();
    double vRadial = Vector3D.dotProduct(vel, zenith);
    double vTangential = FastMath.sqrt(vNorm * vNorm - vRadial * vRadial);

    KeplerianOrbit orb =
        new KeplerianOrbit(pv, state.getFrame(), state.getDate(), Constants.WGS84_EARTH_MU);
    double ecc = orb.getE();
    double apogee = orb.getA() * (1.0 + ecc) - WGS84_EARTH_EQUATORIAL_RADIUS;

    double flightPathAngle = FastMath.atan2(vRadial, vTangential);

    double cost = 0.0;

    // 2. Apogee window — this is the key for staging
    if (apogee < constraints.targetApogee()) {
      cost += 8.0 * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
    } else if (apogee > constraints.maxApogee()) {
      cost += 3.0 * sq((apogee - constraints.maxApogee()) / constraints.targetApogee());
    }

    // 3. Flight path angle — small = nearly horizontal
    double targetFPA = Math.toRadians(constraints.targetFlightPathAngleDeg());
    cost += 2.0 * sq(flightPathAngle - targetFPA);

    // 4. Tangential velocity — must be high enough for orbit insertion
    double minVtan = constraints.minTangentialVelocity();
    if (vTangential < minVtan) {
      cost += 5.0 * sq((minVtan - vTangential) / minVtan);
    }

    // 5. Smooth guard rails
    if (alt < 30_000) cost += 100.0 * sq((30_000 - alt) / 30_000);
    if (ecc > 1.0) cost += 100.0 * sq(ecc - 1.0);
    if (apogee < 100_000) cost += 50.0 * sq((100_000 - apogee) / 100_000);
    if (vNorm < 2000) cost += 100.0 * sq((2000 - vNorm) / 2000);

    cost += W_P * (initialState.getMass() - state.getMass()) / initialState.getMass();

    return cost;
  }
}
