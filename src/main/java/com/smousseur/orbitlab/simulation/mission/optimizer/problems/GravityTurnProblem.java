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
    return 3;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {200.0, 1.0, 1.0};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {30.0, 0.3, 0.70};
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {300.0, 3.0, 1.0};
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {30.0, 0.3, 0.05};
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    return maneuver.propagateForOptimization(initialState, variables);
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
    cost += 2.0 * sq((alt - constraints.targetAltitude()) / constraints.targetAltitude());

    if (apogee < constraints.targetApogee()) {
      cost += 8.0 * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
    } else if (apogee > constraints.maxApogee()) {
      cost += 3.0 * sq((apogee - constraints.maxApogee()) / constraints.targetApogee());
    }

    double targetFPA = Math.toRadians(10.0);
    cost += 2.0 * sq((flightPathAngle - targetFPA) / targetFPA);

    if (vTangential < 5000.0) {
      cost += 3.0 * sq((5000.0 - vTangential) / 5000.0);
    }

    if (alt < 30_000) cost += 1e4;
    if (alt > 300_000) cost += 1e3;
    if (ecc > 1.0) cost += 1e4;
    if (apogee < 100_000) cost += 1e3;
    if (vNorm < 2000) cost += 1e4;

    return cost;
  }
}
