package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger logger = LogManager.getLogger(TransferTwoManeuverProblem.class);

  private static final double G0 = Constants.G0_STANDARD_GRAVITY;
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final TransfertTwoManeuver maneuver;
  private final SpacecraftState initialState;

  // ── Primary objective weights ──
  private static final double W_APO = 1.5;
  private static final double W_PERI = 1.5;
  private static final double W_E = 3.0;
  private static final double W_V = 0.5;

  // ── Constraint barrier weight ──
  private static final double W_BARRIER = 0.1; // soft barriers (altitude min, periapsis)
  private static final double W_ALT_MAX = 1.0; // separate, stronger weight for max altitude

  // ── Constraint thresholds ──
  private static final double ALT_MIN = 80_000;
  private static final double PERIAPSIS_MIN = 100_000;

  private final double altMax; // max altitude ceiling = target + margin

  // Precomputed values
  private final double aTarget;
  private final double vCircTarget;

  // Hohmann-like guess values (precomputed)
  private final double guessT1;
  private final double guessDt1;
  private final double guessDtCoast;
  private final double guessDt2;
  private final double guessAlpha1;

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
    this.altMax = targetAltitude * 1.25;

    double rCurrent = initialState.getPVCoordinates().getPosition().getNorm();
    double vCurrent = initialState.getPVCoordinates().getVelocity().getNorm();

    // ── Account for the actual orbital state (not just altitude) ──
    double aInitial = initialOrbit.getA();
    double eInitial = initialOrbit.getE();

    // Time to apoapsis — burn 1 should happen near apoapsis to raise periapsis
    double trueAnomaly = initialOrbit.getTrueAnomaly();
    double meanAnomaly = initialOrbit.getMeanAnomaly();
    double initialPeriod = 2.0 * FastMath.PI * FastMath.sqrt(aInitial * aInitial * aInitial / mu);

    // Mean anomaly at apoapsis = π
    double meanAnomalyAtApoapsis = FastMath.PI;
    double dMeanAnomaly = meanAnomalyAtApoapsis - meanAnomaly;
    if (dMeanAnomaly < 0) dMeanAnomaly += 2.0 * FastMath.PI;
    double timeToApoapsis = dMeanAnomaly / (2.0 * FastMath.PI) * initialPeriod;

    // Apoapsis radius — this is where burn 1 should happen
    double rApoapsis = aInitial * (1.0 + eInitial);

    // Transfer from apoapsis to target circular orbit
    double aTransfer = (rApoapsis + rTarget) / 2.0;
    double transferPeriod =
        2.0 * FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);

    // Velocity at apoapsis on current orbit
    double vAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / aInitial));

    // Velocity at apoapsis needed for transfer orbit
    double vTransferAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / aTransfer));
    double dv1 = vTransferAtApoapsis - vAtApoapsis;

    // Circularization at target
    double vApogeeTransfer = FastMath.sqrt(mu * (2.0 / rTarget - 1.0 / aTransfer));
    double dv2 = vCircTarget - vApogeeTransfer;

    // Burn at apoapsis → no radial velocity → alpha1 = 0 is correct
    this.guessT1 = timeToApoapsis;
    this.guessAlpha1 = 0.0; // tangential at apoapsis

    double initialMass = initialState.getMass();
    double thrust = propulsionSystem.thrust();
    double isp = propulsionSystem.isp();

    this.guessDt1 = Physics.computeBurnDuration(FastMath.abs(dv1), initialMass, isp, thrust);
    double massAfterBurn1 = initialMass - (thrust / (isp * G0)) * guessDt1;
    this.guessDt2 = Physics.computeBurnDuration(FastMath.abs(dv2), massAfterBurn1, isp, thrust);

    // Coast = half transfer period (apoapsis to target apogee)
    this.guessDtCoast = transferPeriod / 2.0;
  }

  @Override
  public int getNumVariables() {
    return 8;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {guessT1, guessDt1, guessAlpha1, 0.0, guessDtCoast, guessDt2, 0.0, 0.0};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {
      0.0,
      guessDt1 * 0.5,
      -FastMath.PI / 4.0, // burn 1: ±45° (near-tangential raise)
      -FastMath.PI / 12.0,
      guessDtCoast * 0.5,
      guessDt2 * 0.3,
      -FastMath.PI, // burn 2: full ±180° (may need retrograde component)
      -FastMath.PI / 6.0
    };
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {
      guessT1 * 2.0 + 120.0,
      guessDt1 * 2.0,
      FastMath.PI / 4.0,
      FastMath.PI / 12.0,
      guessDtCoast * 2.0,
      guessDt2 * 10.0,
      FastMath.PI, // burn 2: full ±180°
      FastMath.PI / 6.0
    };
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {
      FastMath.max(guessT1 * 0.3, 30.0),
      guessDt1 * 0.3,
      FastMath.PI / 8.0,
      FastMath.PI / 24.0,
      guessDtCoast * 0.2,
      guessDt2 * 2.0,
      FastMath.PI / 3.0, // wider exploration for burn 2 angle
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

    // ── Primary objective: circular orbit at target altitude ──
    double apoapsis = finalOrbit.getA() * (1.0 + finalOrbit.getE());
    double periapsis = finalOrbit.getA() * (1.0 - finalOrbit.getE());
    double rTarget = aTarget;

    double errApo = (apoapsis - rTarget) / rTarget;
    double errPeri = (periapsis - rTarget) / rTarget;
    double errE = finalOrbit.getE();
    double errV = Physics.computeRadialVelocity(state) / vCircTarget;

    double objective =
        W_APO * errApo * errApo
            + W_PERI * errPeri * errPeri
            + W_E * errE * errE
            + W_V * errV * errV;

    // ── Soft barriers for safety constraints ──
    double barrier = 0.0;

    double periapsisAlt = periapsis - EARTH_RADIUS;
    barrier += barrierBelow(periapsisAlt, PERIAPSIS_MIN);

    // ── Max altitude: direct quadratic penalty (simpler, more predictable) ──
    double altMaxPenalty = 0.0;
    double barrierAltLow = 0;

    MinAltitudeTracker tracker = maneuver.getLastAltitudeTracker();
    if (tracker != null) {
      barrierAltLow = barrierBelow(tracker.getMinAltitude(), ALT_MIN);
      barrier += barrierAltLow;

      // Direct quadratic penalty for exceeding max altitude corridor
      if (tracker.getMaxAltitude() > altMax) {
        double excess = (tracker.getMaxAltitude() - altMax) / altMax;
        altMaxPenalty = excess * excess;
      }
    }

    return objective + W_BARRIER * barrier + W_ALT_MAX * altMaxPenalty;
  }

  /**
   * Smooth penalty when value drops below threshold. Returns 0 when value >= threshold, grows
   * smoothly below. Uses a "softplus" shape: penalty = log(1 + exp(-k * (value - threshold) /
   * threshold))
   */
  private static double barrierBelow(double value, double threshold) {
    double normalized = (value - threshold) / FastMath.abs(threshold);
    double k = 10.0;
    if (normalized > 5.0 / k) return 0.0;
    return FastMath.log1p(FastMath.exp(-k * normalized));
  }

  /** Smooth penalty when value exceeds threshold. */
  private static double barrierAbove(double value, double threshold) {
    double normalized = (value - threshold) / FastMath.abs(threshold);
    double k = 10.0;
    if (normalized < -5.0 / k) return 0.0; // well below threshold → no penalty
    return FastMath.log1p(FastMath.exp(k * normalized));
  }
}
