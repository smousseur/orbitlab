package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;

/**
 * Two-burn transfer from an elliptical orbit to a circular orbit at an altitude above the initial
 * apogee.
 *
 * <p>Parameter vector (8 dimensions): [0] t1 — offset of burn 1 start from epoch (s) [1] dt1 —
 * duration of burn 1 (s) [2] alpha1 — in-plane thrust angle in TNW frame (rad) [3] beta1 —
 * out-of-plane thrust angle in TNW frame (rad) [4] dtCoast — coast duration between end of burn 1
 * and start of burn 2 (s) [5] dt2 — duration of burn 2 (s) [6] alpha2 — in-plane thrust angle in
 * TNW frame (rad) [7] beta2 — out-of-plane thrust angle in TNW frame (rad)
 *
 * <p>The start of burn 2 is derived: t2 = t1 + dt1 + dtCoast.
 */
public class TransferTwoManeuverProblem implements TrajectoryProblem {

  private static final double G0 = Constants.G0_STANDARD_GRAVITY;
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final TransfertTwoManeuver maneuver;
  private final SpacecraftState initialState;

  // Cost function weights
  private static final double W_A = 1.0;
  private static final double W_E = 1.5;
  private static final double W_V = 1.0;
  private static final double W_PERI = 1.0;

  // Precomputed values
  private final double aTarget;
  private final double vCircTarget;

  // Hohmann-like guess values (precomputed)
  private final double guessT1;
  private final double guessDt1;
  private final double guessDtCoast;
  private final double guessDt2;
  private final double transferPeriod;

  /**
   * Instantiates a new Two maneuver transfer problem.
   *
   * @param maneuver the maneuver
   * @param initialState the initial state
   * @param targetAltitude altitude of the target circular orbit (m)
   * @param propulsionSystem the propulsion system
   */
  public TransferTwoManeuverProblem(
      TransfertTwoManeuver maneuver,
      SpacecraftState initialState,
      double targetAltitude,
      PropulsionSystem propulsionSystem) {

    this.initialState = initialState;
    this.maneuver = maneuver;
    KeplerianOrbit initialOrbit = new KeplerianOrbit(initialState.getOrbit());
    double mu = initialOrbit.getMu();

    double rTarget = EARTH_RADIUS + targetAltitude;
    this.aTarget = rTarget;
    this.vCircTarget = FastMath.sqrt(mu / rTarget);

    // Burn immediately from current position to raise apogee to target
    double rCurrent = initialState.getPVCoordinates().getPosition().getNorm();
    double vCurrent = initialState.getPVCoordinates().getVelocity().getNorm();

    // Desired transfer orbit: from rCurrent to rTarget
    double aTransfer = (rCurrent + rTarget) / 2.0;
    this.transferPeriod = 2.0 * FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);

    // Velocity needed at current position on the transfer orbit
    double vNeededForTransfer = FastMath.sqrt(mu * (2.0 / rCurrent - 1.0 / aTransfer));
    double dv1 = vNeededForTransfer - vCurrent;

    // Circularization at apogee
    double vApogeeTransfer = FastMath.sqrt(mu * (2.0 / rTarget - 1.0 / aTransfer));
    double dv2 = vCircTarget - vApogeeTransfer;

    // Burn immediately
    this.guessT1 = 0.0;

    double initialMass = initialState.getMass();
    double thrust = propulsionSystem.thrust();
    double isp = propulsionSystem.isp();

    this.guessDt1 = Physics.computeBurnDuration(FastMath.abs(dv1), initialMass, isp, thrust);

    double massAfterBurn1 = initialMass - (thrust / (isp * G0)) * guessDt1;
    this.guessDt2 = Physics.computeBurnDuration(FastMath.abs(dv2), massAfterBurn1, isp, thrust);

    // Coast = half transfer period (current position to apogee)
    this.guessDtCoast = transferPeriod / 2.0;
  }

  @Override
  public int getNumVariables() {
    return 8;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {guessT1, guessDt1, 0.0, 0.0, guessDtCoast, guessDt2, 0.0, 0.0};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {
      0.0, // t1: can start immediately
      guessDt1 * 0.3,
      -FastMath.PI,
      -FastMath.PI / 6.0,
      guessDtCoast * 0.3, // coast: don't go too short
      guessDt2 * 0.3,
      -FastMath.PI,
      -FastMath.PI / 6.0
    };
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {
      60,
      guessDt1 * 3.0,
      FastMath.PI,
      FastMath.PI / 6.0,
      2.0 * transferPeriod,
      guessDt2 * 3.0,
      FastMath.PI,
      FastMath.PI / 6.0
    };
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {
      10.0,
      guessDt1 * 0.5,
      FastMath.PI / 3.0,
      FastMath.PI / 12.0,
      guessDtCoast * 0.3, // coast: explorer ±30% autour du guess
      guessDt2 * 0.5,
      FastMath.PI / 3.0,
      FastMath.PI / 12.0
    };
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    return maneuver.propagateForOptimization(initialState, variables);
  }

  @Override
  public double computeCost(SpacecraftState state) {
    KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());

    double errA = (finalOrbit.getA() - aTarget) / aTarget;
    double errE = finalOrbit.getE();
    double errV = Physics.computeRadialVelocity(state) / vCircTarget;
    double errorP = 0;

    double periapsis =
        finalOrbit.getA() * (1.0 - finalOrbit.getE()) - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    if (periapsis < 100_000) {
      errorP = (100_000 - periapsis) / 100_000;
    }

    return W_A * errA * errA + W_E * errE * errE + W_V * errV * errV + W_PERI * errorP * errorP;
  }
}
