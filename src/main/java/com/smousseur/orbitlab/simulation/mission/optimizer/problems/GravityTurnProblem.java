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
 * The type Gravity turn problem. 3 variables : transitionTime, exponent for pitch kick,
 * propFraction)
 */
public class GravityTurnProblem implements TrajectoryProblem {
  private final GravityTurnManeuver maneuver;
  private final SpacecraftState initialState;
  private final GravityTurnConstraints constraints;
  private final double minAllowableMass;

  public GravityTurnProblem(
      GravityTurnManeuver maneuver,
      SpacecraftState initialState,
      GravityTurnConstraints constraints) {
    this.maneuver = maneuver;
    this.initialState = initialState;
    this.minAllowableMass = maneuver.getVehicle().getFullDryMass();
    this.constraints = constraints;
  }

  @Override
  public double getAcceptableCost() {
    return 0.01;
  }

  @Override
  public int getNumVariables() {
    return 3;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {100.0, 1.0, 0.95};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {30.0, 0.3, 0.70};
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {450.0, 3.0, 1.0};
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {30.0, 0.3, 0.05};
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    return maneuver.propagateForOptimization(initialState, minAllowableMass, variables);
  }

  @Override
  public double computeCost(SpacecraftState state) {
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

    // 1. Altitude at MECO — primary objective
    cost += 2.0 * sq((alt - constraints.targetAltitude()) / constraints.targetAltitude());

    // 2. Apogee window — this is the key for staging
    if (apogee < constraints.targetApogee()) {
      cost += 6.0 * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
    } else if (apogee > constraints.maxApogee()) {
      cost += 2.0 * sq((apogee - constraints.maxApogee()) / constraints.targetApogee());
    }

    // 3. Flight path angle — normalized by target for consistent scale across altitudes
    double targetFPA = Math.toRadians(constraints.targetFlightPathAngleDeg());
    cost += 5.0 * sq((flightPathAngle - targetFPA) / targetFPA);

    // 4. Tangential velocity — must be high enough for orbit insertion
    double minVtan = constraints.minTangentialVelocity();
    if (vTangential < minVtan) {
      cost += 5.0 * sq((minVtan - vTangential) / minVtan);
    }

    // 5. Smooth guard rails — scaled to mission constraints
    double minSafeAlt = constraints.targetAltitude() * 0.45;
    if (alt < minSafeAlt) cost += 50.0 * sq((minSafeAlt - alt) / minSafeAlt);
    if (ecc > 1.0) cost += 100.0 * sq(ecc - 1.0);
    double minSafeApogee = constraints.targetApogee() * 0.5;
    if (apogee < minSafeApogee) cost += 50.0 * sq((minSafeApogee - apogee) / minSafeApogee);
    if (vNorm < minVtan) cost += 50.0 * sq((minVtan - vNorm) / minVtan);

    return cost;
  }
}
