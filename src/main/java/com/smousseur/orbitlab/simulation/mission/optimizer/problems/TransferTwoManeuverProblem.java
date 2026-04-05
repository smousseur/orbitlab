package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
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
  private static final Logger logger = LogManager.getLogger(TransferTwoManeuverProblem.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final TransfertTwoManeuver maneuver;
  private final SpacecraftState initialState;
  private TransferResult lastResult;

  // ── Primary objective weights ──
  private static final double W_APO = 3.0;
  private static final double W_PERI = 10.0;
  private static final double W_E = 2.0;
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

  // Physical upper bound on burn 1 duration (from available propellant)
  private final double dt1MaxPhysical;

  /**
   * Creates a two-burn transfer optimization problem.
   *
   * <p>Precomputes Hohmann-like initial guesses for burn timing and duration, applies a J2
   * short-period altitude compensation to the target, and determines physical upper bounds on burn
   * duration from available propellant.
   *
   * @param maneuver the two-burn transfer maneuver that handles propagation
   * @param initialState the spacecraft state at the start of the transfer
   * @param targetAltitude the desired circular orbit altitude in meters above the Earth's surface
   * @param propulsionSystem the propulsion system used for the transfer burns
   * @param vehicleMinMass minimum allowable vehicle mass after burns (dry mass)
   */
  public TransferTwoManeuverProblem(
      TransfertTwoManeuver maneuver,
      SpacecraftState initialState,
      double targetAltitude,
      PropulsionSystem propulsionSystem,
      double vehicleMinMass) {

    this.initialState = initialState;
    this.maneuver = maneuver;
    KeplerianOrbit initialOrbit = new KeplerianOrbit(initialState.getOrbit());
    double mu = initialOrbit.getMu();

    // ── J2 short-period altitude compensation ──
    // The osculating radius oscillates around the mean with amplitude ~J2*Re²/a
    // To center the geodetic altitude excursions on targetAltitude,
    // we target a slightly higher mean altitude
    double rNominal = EARTH_RADIUS + targetAltitude;
    double j2 = 1.0826e-3; // J2 coefficient
    double sinI = FastMath.sin(initialOrbit.getI());
    double j2Amplitude = j2 * EARTH_RADIUS * EARTH_RADIUS / rNominal * (1.0 - 1.5 * sinI * sinI);
    double altitudeOffset = j2Amplitude / 2.0;

    double effectiveTargetAlt = targetAltitude + altitudeOffset;
    logger.info(
        "J2 altitude offset: {} m, effective target: {} m", altitudeOffset, effectiveTargetAlt);

    double rTarget = EARTH_RADIUS + effectiveTargetAlt;

    this.aTarget = rTarget;
    this.vCircTarget = FastMath.sqrt(mu / rTarget);
    this.altMax = targetAltitude * 1.05;

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

    // Physical upper bound: 90% of the time to exhaust available propellant
    double massFlow = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    double availablePropellant = initialMass - vehicleMinMass;
    this.dt1MaxPhysical = (availablePropellant * 0.90) / massFlow;

    logger.info("Initial guess for burn 1: T1={}, dt1={}, dv1={}", guessT1, guessDt1, dv1);
    logger.info(
        "Physical dt1 max: {}s (propellant available: {}kg)", dt1MaxPhysical, availablePropellant);
  }

  @Override
  public double getAcceptableCost() {
    return 8e-4;
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
      guessT1 * 2.0 + 120.0,
      FastMath.min(guessDt1 * 2.0, dt1MaxPhysical),
      FastMath.PI / 4.0,
      FastMath.PI / 12.0
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
    lastResult = maneuver.propagateForOptimization(initialState, variables);
    return lastResult.finalState();
  }

  /**
   * Returns the transfer result from the most recent propagation, containing the post-burn-1 orbit
   * and the resolved burn-2 parameters.
   *
   * @return the last transfer result, or {@code null} if no propagation has been performed yet
   */
  public TransferResult getLastTransferResult() {
    return lastResult;
  }

  @Override
  public double computeCost(SpacecraftState state) {
    // Detect penalty states: if propagation failed, the returned state is the initial state
    // (no time advancement). Assign a very high cost so CMA-ES avoids these solutions.
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      return 1e6;
    }

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
    MinAltitudeTracker tracker = lastResult != null ? lastResult.altitudeTracker() : null;
    if (tracker != null) {
      barrier += barrierBelow(tracker.getMinAltitude(), ALT_MIN);
      if (tracker.getMaxAltitude() > altMax) {
        double excess = (tracker.getMaxAltitude() - altMax) / altMax;
        altMaxPenalty = excess * excess;
      }
    }

    return objective + W_BARRIER * barrier + W_ALT_MAX * altMaxPenalty;
  }

  private static double barrierBelow(double value, double threshold) {
    double normalized = (value - threshold) / FastMath.abs(threshold);
    double k = 10.0;
    if (normalized > 5.0 / k) return 0.0;
    return FastMath.log1p(FastMath.exp(-k * normalized));
  }

  /**
   * Returns the underlying two-burn transfer maneuver.
   *
   * @return the transfer maneuver instance
   */
  public TransfertTwoManeuver getManeuver() {
    return maneuver;
  }
}
