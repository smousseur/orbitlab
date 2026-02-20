package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

import static com.smousseur.orbitlab.simulation.Physics.sq;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

public class GravityTurnProblem implements TrajectoryProblem {
  private final Vehicle vehicle;
  private final SpacecraftState initialState;
  private final double pitchKickAngle;
  private final double verticalBurnDuration;
  private final GravityTurnConstraints constraints;

  public GravityTurnProblem(
      SpacecraftState initialState,
      Vehicle vehicle,
      double pitchKickAngle,
      double verticalBurnDuration,
      GravityTurnConstraints constraints) {
    this.vehicle = vehicle;
    this.initialState = initialState;
    this.pitchKickAngle = Math.toRadians(pitchKickAngle);
    this.verticalBurnDuration = verticalBurnDuration;
    this.constraints = constraints;
  }

  @Override
  public int getNumVariables() {
    return 3;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {
      200.0, // [0] transitionTime — your current empirical value
      1.0, // [1] exponent — slightly sub-linear
      1.0 // [2] propFraction — burn everything (current behavior)
    };
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {
      30.0, // transitionTime min
      0.3, // exponent min (very aggressive turn)
      0.70 // propFraction min (use at least 70% of propellant)
    };
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {
      300.0, // transitionTime max
      3.0, // exponent max (very conservative turn)
      1.0 // propFraction max (burn everything)
    };
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {
      30.0, // explore ±30s around guess
      0.3, // explore ±0.3 around guess
      0.05 // explore ±5% around guess
    };
  }

  @Override
  public double computeCost(SpacecraftState state) {
    PVCoordinates pv = state.getPVCoordinates();
    Vector3D pos = pv.getPosition();
    Vector3D vel = pv.getVelocity();

    double alt = pos.getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    double vNorm = vel.getNorm();

    // Radial vs tangential velocity
    Vector3D zenith = pos.normalize();
    double vRadial = Vector3D.dotProduct(vel, zenith);
    double vTangential = FastMath.sqrt(vNorm * vNorm - vRadial * vRadial);

    // Osculating orbit
    KeplerianOrbit orb =
        new KeplerianOrbit(pv, state.getFrame(), state.getDate(), Constants.WGS84_EARTH_MU);
    double ecc = orb.getE();
    double apogee = orb.getA() * (1.0 + ecc) - WGS84_EARTH_EQUATORIAL_RADIUS;

    // Flight path angle
    double flightPathAngle = FastMath.atan2(vRadial, vTangential);

    double cost = 0.0;

    // ── (1) Altitude ──
    cost += 2.0 * sq((alt - constraints.targetAltitude()) / constraints.targetAltitude());

    // ── (2) Apogee ──
    if (apogee < constraints.targetApogee()) {
      cost += 8.0 * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
    } else if (apogee > constraints.maxApogee()) {
      cost += 3.0 * sq((apogee - constraints.maxApogee()) / constraints.targetApogee());
    }

    // ── (3) Flight path angle: 5-15° ideal ──
    double targetFPA = Math.toRadians(10.0);
    cost += 2.0 * sq((flightPathAngle - targetFPA) / targetFPA);

    // ── (4) Velocity: maximize tangential ──
    if (vTangential < 5000.0) {
      cost += 3.0 * sq((5000.0 - vTangential) / 5000.0);
    }

    // ── (5) Propellant efficiency bonus ──
    //     Less mass used = better (lower remaining prop in stage 1 is fine,
    //     but we want stage 2 to have an easy job)
    //     Proxy: higher vTangential + higher apogee = less work for stage 2
    //     Already captured by (2) and (4)

    // ── (6) Hard penalties ──
    if (alt < 30_000) cost += 1e4;
    if (alt > 300_000) cost += 1e3;
    if (ecc > 1.0) cost += 1e4; // hyperbolic
    if (apogee < 100_000) cost += 1e3; // won't reach target
    if (vNorm < 2000) cost += 1e4; // stall

    return cost;
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    double transitionTime = variables[0];
    double exponent = variables[1];
    double propFraction = variables[2];

    // ── 1. Apply pitch kick to post-vertical-burn state ──
    SpacecraftState kickedState =
        Physics.applyPitchKick(initialState, pitchKickAngle, Physics.getLaunchAzimuth());

    // ── 2. Compute burn duration from propellant fraction ──
    double propToUse = propFraction * vehicle.getPropellantMass();
    PropulsionSystem propulsion = vehicle.getPropulsion();
    double massFlowRate = propulsion.thrust() / (propulsion.isp() * 9.80665);
    double burnDuration = propToUse / massFlowRate;

    // Subtract propellant already used during vertical burn
    double propUsedVertical = massFlowRate * verticalBurnDuration;
    double remainingBurnTime = (propToUse - propUsedVertical) / massFlowRate;
    remainingBurnTime = FastMath.max(10.0, remainingBurnTime);

    NumericalPropagator propagator = createSimplePropagator();
    propagator.setInitialState(kickedState);

    AbsoluteDate kickDate = kickedState.getDate();
    GravityTurnAttitudeProvider attitudeProvider =
        new GravityTurnAttitudeProvider(kickDate, transitionTime, exponent);
    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            kickDate.shiftedBy(0.1),
            remainingBurnTime,
            propulsion.thrust(),
            propulsion.isp(),
            attitudeProvider,
            Vector3D.PLUS_I);
    propagator.addForceModel(burn);

    AbsoluteDate endDate = kickDate.shiftedBy(remainingBurnTime + 1.0);
    SpacecraftState finalState = propagator.propagate(endDate);
    double stage2Mass = finalState.getMass() - vehicle.getDryMass();
    if (stage2Mass <= 0) {
      return buildPenaltyState(kickedState);
    }

    return finalState.withMass(stage2Mass);
  }

  private SpacecraftState buildPenaltyState(SpacecraftState reference) {
    // Return a state that will produce a high cost
    // (e.g. at surface with zero velocity — will get max penalty)
    return reference;
  }
}
