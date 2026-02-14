package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
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
public class TwoManeuverTransferProblem implements TrajectoryProblem {

  private static final int NUM_VARIABLES = 8;
  private static final double G0 = Constants.G0_STANDARD_GRAVITY;
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  // Cost function weights
  private static final double W_A = 1.0;
  private static final double W_E = 1.0;
  private static final double W_V = 1.0;

  private final KeplerianOrbit initialOrbit;
  private final double initialMass;
  private final double targetAltitude;
  private final double thrust;
  private final double isp;
  private final double mu;

  // Precomputed values
  private final AbsoluteDate epoch;
  private final double rTarget;
  private final double aTarget;
  private final double vCircTarget;
  private final double initialPeriod;

  // Hohmann-like guess values (precomputed)
  private final double guessT1;
  private final double guessDt1;
  private final double guessDtCoast;
  private final double guessDt2;
  private final double transferPeriod;

  /**
   * Instantiates a new Two maneuver transfer problem.
   *
   * @param initialOrbit initial elliptical orbit
   * @param initialMass initial spacecraft mass (kg)
   * @param targetAltitude altitude of the target circular orbit (m)
   * @param propulsionSystem the propulsion system
   */
  public TwoManeuverTransferProblem(
      KeplerianOrbit initialOrbit,
      double initialMass,
      double targetAltitude,
      PropulsionSystem propulsionSystem) {
    this.initialOrbit = initialOrbit;
    this.initialMass = initialMass;
    this.targetAltitude = targetAltitude;
    this.thrust = propulsionSystem.thrust();
    this.isp = propulsionSystem.isp();
    this.mu = initialOrbit.getMu();
    this.epoch = initialOrbit.getDate();

    // Target orbit
    this.rTarget = EARTH_RADIUS + targetAltitude;
    this.aTarget = rTarget; // circular orbit: a = r
    this.vCircTarget = FastMath.sqrt(mu / rTarget);

    // Initial orbit properties
    double a1 = initialOrbit.getA();
    double e1 = initialOrbit.getE();
    double rPerigee = a1 * (1.0 - e1);
    this.initialPeriod = 2.0 * FastMath.PI * FastMath.sqrt(a1 * a1 * a1 / mu);

    // Transfer orbit
    double aTransfer = (rPerigee + rTarget) / 2.0;
    this.transferPeriod = 2.0 * FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);

    // Hohmann-like delta-V computation
    double vPerigeeInitial = FastMath.sqrt(mu * (2.0 / rPerigee - 1.0 / a1));
    double vPerigeeTransfer = FastMath.sqrt(mu * (2.0 / rPerigee - 1.0 / aTransfer));
    double dv1 = vPerigeeTransfer - vPerigeeInitial;

    double vApogeeTransfer = FastMath.sqrt(mu * (2.0 / rTarget - 1.0 / aTransfer));
    double dv2 = vCircTarget - vApogeeTransfer;

    // Time to next perigee passage
    this.guessT1 = computeTimeToPerigee();

    // Convert delta-V to burn durations via Tsiolkovski
    this.guessDt1 = computeBurnDuration(FastMath.abs(dv1), initialMass);

    // Mass after burn 1
    double massAfterBurn1 = initialMass - (thrust / (isp * G0)) * guessDt1;
    this.guessDt2 = computeBurnDuration(FastMath.abs(dv2), massAfterBurn1);

    // Coast = half transfer period (perigee to apogee)
    this.guessDtCoast = transferPeriod / 2.0;
  }

  @Override
  public int getNumVariables() {
    return NUM_VARIABLES;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {
      guessT1, // t1
      guessDt1, // dt1
      0.0, // alpha1 (tangential prograde)
      0.0, // beta1 (in-plane)
      guessDtCoast, // dtCoast
      guessDt2, // dt2
      0.0, // alpha2 (tangential prograde)
      0.0 // beta2 (in-plane)
    };
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {
      0.0, // t1
      1.0, // dt1
      -FastMath.PI, // alpha1
      -FastMath.PI / 2.0, // beta1
      0.0, // dtCoast
      1.0, // dt2
      -FastMath.PI, // alpha2
      -FastMath.PI / 2.0 // beta2
    };
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {
      2.0 * initialPeriod, // t1
      guessDt1 * 3.0, // dt1
      FastMath.PI, // alpha1
      FastMath.PI / 2.0, // beta1
      2.0 * transferPeriod, // dtCoast
      guessDt2 * 3.0, // dt2
      FastMath.PI, // alpha2
      FastMath.PI / 2.0 // beta2
    };
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {
      initialPeriod / 2.0, // t1
      guessDt1 * 0.5, // dt1
      FastMath.PI / 3.0, // alpha1 (30°)
      FastMath.PI / 6.0, // beta1 (10°)
      transferPeriod / 3.0, // dtCoast
      guessDt2 * 0.5, // dt2
      FastMath.PI / 3.0, // alpha2 (30°)
      FastMath.PI / 6.0 // beta2 (10°)
    };
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    // Unpack variables
    double t1 = variables[0];
    double dt1 = variables[1];
    double alpha1 = variables[2];
    double beta1 = variables[3];
    double dtCoast = variables[4];
    double dt2 = variables[5];
    double alpha2 = variables[6];
    double beta2 = variables[7];

    // Derived: start of burn 2
    double t2 = t1 + dt1 + dtCoast;

    // Dates
    AbsoluteDate burn1Start = epoch.shiftedBy(t1);
    AbsoluteDate burn2Start = epoch.shiftedBy(t2);
    AbsoluteDate endDate = epoch.shiftedBy(t2 + dt2);

    // Initial state
    SpacecraftState initialState = new SpacecraftState(initialOrbit).withMass(initialMass);

    // Get propagator
    NumericalPropagator propagator = createSimplePropagator();
    propagator.setInitialState(initialState);

    // Thrust direction vectors in TNW frame
    Vector3D thrustDirection1 = buildThrustDirection(alpha1, beta1);
    Vector3D thrustDirection2 = buildThrustDirection(alpha2, beta2);

    LofOffset attitude = new LofOffset(initialOrbit.getFrame(), LOFType.TNW);
    // Create maneuvers
    ConstantThrustManeuver burn1 =
        new ConstantThrustManeuver(burn1Start, dt1, thrust, isp, attitude, thrustDirection1);
    ConstantThrustManeuver burn2 =
        new ConstantThrustManeuver(burn2Start, dt2, thrust, isp, attitude, thrustDirection2);

    // Attach maneuvers
    propagator.addForceModel(burn1);
    propagator.addForceModel(burn2);

    // Propagate to end of burn 2
    return propagator.propagate(endDate);
  }

  @Override
  public double computeCost(SpacecraftState finalState) {
    KeplerianOrbit finalOrbit =
        (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(finalState.getOrbit());

    double errA = (finalOrbit.getA() - aTarget) / aTarget;
    double errE = finalOrbit.getE();
    double errV = computeRadialVelocity(finalState) / vCircTarget;

    return W_A * errA * errA + W_E * errE * errE + W_V * errV * errV;
  }

  /**
   * Build thrust direction vector in TNW frame from in-plane and out-of-plane angles. alpha = 0,
   * beta = 0 means pure tangential prograde thrust.
   */
  private Vector3D buildThrustDirection(double alpha, double beta) {
    double cosB = FastMath.cos(beta);
    return new Vector3D(
        cosB * FastMath.cos(alpha), // T component
        cosB * FastMath.sin(alpha), // N component
        FastMath.sin(beta) // W component
        );
  }

  /** Compute radial velocity (dot product of position and velocity divided by position norm). */
  private double computeRadialVelocity(SpacecraftState state) {
    Vector3D position = state.getPVCoordinates().getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    return Vector3D.dotProduct(position, velocity) / position.getNorm();
  }

  /** Compute time to next perigee passage from current position on the orbit. */
  private double computeTimeToPerigee() {
    double meanAnomaly = initialOrbit.getMeanAnomaly();
    if (meanAnomaly < 0) {
      meanAnomaly += 2.0 * FastMath.PI;
    }
    // Time from perigee to current position
    double timeSincePerigee = meanAnomaly / (2.0 * FastMath.PI) * initialPeriod;
    // Time to next perigee
    return initialPeriod - timeSincePerigee;
  }

  /**
   * Convert a delta-V to a burn duration using the Tsiolkovski equation. dt = (m * Isp * g0 / F) *
   * (1 - exp(-dv / (Isp * g0)))
   */
  private double computeBurnDuration(double dv, double mass) {
    double ve = isp * G0; // exhaust velocity
    return (mass * ve / thrust) * (1.0 - FastMath.exp(-dv / ve));
  }

  NumericalPropagator createSimplePropagator() {
    double minStep = 0.001;
    double maxStep = 100.0;
    double absTol = 1e-8;
    double relTol = 1e-10;

    DormandPrince853Integrator integrator =
        new DormandPrince853Integrator(minStep, maxStep, absTol, relTol);

    NumericalPropagator propagator = new NumericalPropagator(integrator);
    propagator.addForceModel(new NewtonianAttraction(Constants.WGS84_EARTH_MU));
    return propagator;
  }
}
