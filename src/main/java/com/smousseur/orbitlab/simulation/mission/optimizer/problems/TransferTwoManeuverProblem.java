package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;

/**
 * Optimization problem for a two-burn orbit transfer where only burn 1 is optimized.
 *
 * <p>Burn 1 (4 CMA-ES parameters) places the spacecraft on an elliptical transfer orbit. Burn 2 is
 * a deterministic prograde circularization at apoapsis, computed by the maneuver class.
 *
 * <p>The cost function evaluates the <b>final</b> orbit after both burns — this is what matters.
 * Because burn 2 is deterministic, CMA-ES only needs to find the burn 1 parameters that produce a
 * transfer orbit from which the circularization yields the best circular orbit.
 *
 * <p>Parameter vector (4 dimensions):
 *
 * <ul>
 *   <li>[0] t1 — offset of burn 1 start from epoch (s)
 *   <li>[1] dt1 — duration of burn 1 (s)
 *   <li>[2] alpha1 — in-plane thrust angle in TNW frame (rad)
 *   <li>[3] beta1 — out-of-plane thrust angle in TNW frame (rad)
 * </ul>
 */
public class TransferTwoManeuverProblem implements TrajectoryProblem {
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final TransfertTwoManeuver maneuver;
  private final SpacecraftState initialState;

  // ── Primary objective weights ──
  private static final double W_APO = 3.0;
  private static final double W_PERI = 5.0;
  private static final double W_E = 10.0;
  private static final double W_V = 1.0;

  // ── Constraint barrier weight ──
  private static final double W_BARRIER = 0.1;
  private static final double W_ALT_MAX = 1.0;

  // ── Constraint thresholds ──
  private static final double ALT_MIN = 80_000;
  private static final double PERIAPSIS_MIN = 100_000;

  private final double altMax;

  // Precomputed values
  private final double aTarget;
  private final double vCircTarget;

  // Hohmann-like guess values (precomputed)
  private final double guessT1;
  private final double guessDt1;

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

    double aInitial = initialOrbit.getA();
    double eInitial = initialOrbit.getE();

    // ── Time to apoapsis ──
    double meanAnomaly = initialOrbit.getMeanAnomaly();
    double initialPeriod = 2.0 * FastMath.PI * FastMath.sqrt(aInitial * aInitial * aInitial / mu);
    double dMeanAnomaly = FastMath.PI - meanAnomaly;
    if (dMeanAnomaly < 0) dMeanAnomaly += 2.0 * FastMath.PI;
    double timeToApoapsis = dMeanAnomaly / (2.0 * FastMath.PI) * initialPeriod;

    // ── Hohmann estimate for burn 1 ──
    double rApoapsis = aInitial * (1.0 + eInitial);
    double aTransfer = (rApoapsis + rTarget) / 2.0;

    double vAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / aInitial));
    double vTransferAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / aTransfer));
    double dv1 = vTransferAtApoapsis - vAtApoapsis;

    this.guessT1 = timeToApoapsis;

    double initialMass = initialState.getMass();
    double thrust = propulsionSystem.thrust();
    double isp = propulsionSystem.isp();

    this.guessDt1 = Physics.computeBurnDuration(FastMath.abs(dv1), initialMass, isp, thrust);
  }

  @Override
  public double getAcceptableCost() {
    return 5e-7;
  }

  @Override
  public int getNumVariables() {
    return 4;
  }

  @Override
  public double[] buildInitialGuess() {
    return new double[] {guessT1, guessDt1, 0.0, 0.0};
  }

  @Override
  public double[] getLowerBounds() {
    return new double[] {
      0.0,
      guessDt1 * 0.5,
      -FastMath.PI / 4.0, // alpha1: prograde ± 45°
      -FastMath.PI / 12.0 // beta1: small out-of-plane
    };
  }

  @Override
  public double[] getUpperBounds() {
    return new double[] {
      guessT1 * 2.0 + 120.0, guessDt1 * 2.0, FastMath.PI / 4.0, FastMath.PI / 12.0
    };
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {
      FastMath.max(guessT1 * 0.3, 30.0), guessDt1 * 0.3, FastMath.PI / 8.0, FastMath.PI / 24.0
    };
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    return maneuver.propagateForOptimization(initialState, variables);
  }

  @Override
  public double computeCost(SpacecraftState state) {
    KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());

    double apoapsis = finalOrbit.getA() * (1.0 + finalOrbit.getE());
    double periapsis = finalOrbit.getA() * (1.0 - finalOrbit.getE());
    double rTarget = aTarget;

    // Normalize by target altitude — sensitive to km-scale errors
    double targetAlt = rTarget - EARTH_RADIUS;
    double apoAlt = apoapsis - EARTH_RADIUS;
    double periAlt = periapsis - EARTH_RADIUS;

    double errApo = (apoAlt - targetAlt) / targetAlt;
    double errPeri = (periAlt - targetAlt) / targetAlt;
    double errE = finalOrbit.getE();
    double errV = Physics.computeRadialVelocity(state) / vCircTarget;

    double objective =
        W_APO * errApo * errApo
            + W_PERI * errPeri * errPeri
            + W_E * errE * errE
            + W_V * errV * errV;

    double barrier = 0.0;
    double periapsisAlt = periapsis - EARTH_RADIUS;
    barrier += barrierBelow(periapsisAlt, PERIAPSIS_MIN);

    double altMaxPenalty = 0.0;
    MinAltitudeTracker tracker = maneuver.getLastAltitudeTracker();
    if (tracker != null) {
      barrier += barrierBelow(tracker.getMinAltitude(), ALT_MIN);
      if (tracker.getMaxAltitude() > altMax) {
        double excess = (tracker.getMaxAltitude() - altMax) / altMax;
        altMaxPenalty = excess * excess;
      }
    }

    return objective + W_BARRIER * barrier + W_ALT_MAX * altMaxPenalty;
  }

  public TransfertTwoManeuver getManeuver() {
    return maneuver;
  }

  private static double barrierBelow(double value, double threshold) {
    double normalized = (value - threshold) / FastMath.abs(threshold);
    double k = 10.0;
    if (normalized > 5.0 / k) return 0.0;
    return FastMath.log1p(FastMath.exp(-k * normalized));
  }
}
