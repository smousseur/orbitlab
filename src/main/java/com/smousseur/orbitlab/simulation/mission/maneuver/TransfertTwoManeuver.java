package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.optimizer.CMAESTrajectoryOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Two-burn orbit transfer with a split responsibility:
 *
 * <ul>
 *   <li><b>Burn 1</b> is optimized by CMA-ES (4 parameters: t1, dt1, alpha1, beta1). Its goal is to
 *       place the spacecraft on an elliptical orbit whose apoapsis is at the target altitude.
 *   <li><b>Burn 2</b> is computed deterministically: prograde circularization centered on the first
 *       apoapsis after burn 1. Its duration is derived from Tsiolkovsky and the orbital state at
 *       apoapsis.
 * </ul>
 *
 * <p>Optimization parameter vector (4 dimensions):
 *
 * <ul>
 *   <li>[0] t1 — offset of burn 1 start from epoch (s)
 *   <li>[1] dt1 — duration of burn 1 (s)
 *   <li>[2] alpha1 — in-plane thrust angle in TNW frame (rad)
 *   <li>[3] beta1 — out-of-plane thrust angle in TNW frame (rad)
 * </ul>
 */
public class TransfertTwoManeuver {
  private static final Logger logger = LogManager.getLogger(TransfertTwoManeuver.class);

  private final Vehicle vehicle;
  private final double targetAltitude;

  /** Tracks altitude extremes during the last propagation. */
  private MinAltitudeTracker lastAltitudeTracker;

  /** Resolved burn 2 parameters from the last propagation (available after propagate calls). */
  private ResolvedBurn2 lastResolvedBurn2;

  public TransfertTwoManeuver(Vehicle vehicle, double targetAltitude) {
    this.vehicle = vehicle;
    this.targetAltitude = targetAltitude;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Parameter records
  // ════════════════════════════════════════════════════════════════════════

  /** The 4 CMA-ES-optimized parameters for burn 1 only. */
  public record Burn1Params(double t1, double dt1, double alpha1, double beta1) {}

  /** Deterministically resolved burn 2 parameters. */
  public record ResolvedBurn2(double dtCoast, double dt2, double dvNeeded) {}

  public Burn1Params decode(double[] variables) {
    return new Burn1Params(variables[0], variables[1], variables[2], variables[3]);
  }

  public ResolvedBurn2 getLastResolvedBurn2() {
    return lastResolvedBurn2;
  }

  public MinAltitudeTracker getLastAltitudeTracker() {
    return lastAltitudeTracker;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Optimization entry point (called by the Problem)
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Full propagation for CMA-ES evaluation:
   *
   * <ol>
   *   <li>Propagate burn 1
   *   <li>Coast to apoapsis, compute deterministic burn 2
   *   <li>Full propagation with both burns
   * </ol>
   */
  public SpacecraftState propagateForOptimization(
      SpacecraftState initialState, double[] variables) {
    Burn1Params params = decode(variables);

    // ── Step 1: Propagate burn 1 to get post-burn state ──
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return initialState; // penalty
    }

    // ── Step 2: Resolve burn 2 deterministically ──
    ResolvedBurn2 burn2 = resolveBurn2(stateAfterBurn1);
    if (burn2 == null) {
      return initialState; // penalty
    }
    this.lastResolvedBurn2 = burn2;

    // ── Step 3: Full propagation with both burns using configure() ──
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(initialState);
    configure(propagator, initialState, params, burn2);

    double totalTime = params.t1 + params.dt1 + burn2.dtCoast + burn2.dt2;
    AbsoluteDate endDate = initialState.getDate().shiftedBy(totalTime);
    try {
      SpacecraftState finalState = propagator.propagate(endDate);
      if (Math.abs(finalState.getDate().durationFrom(endDate)) > 1.0) {
        return initialState; // penalty
      }
      return finalState;
    } catch (Exception e) {
      return initialState; // penalty
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // Configure — single source of truth for both optimizer and Stage runtime
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Configures both burns on the given propagator.
   *
   * @param propagator the propagator to configure
   * @param state the initial state (epoch reference)
   * @param params the 4 optimized burn 1 parameters
   * @param burn2 the deterministically resolved burn 2 parameters
   */
  public void configure(
      NumericalPropagator propagator,
      SpacecraftState state,
      Burn1Params params,
      ResolvedBurn2 burn2) {

    AbsoluteDate epoch = state.getDate();
    LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
    PropulsionSystem propulsion = vehicle.getSecondStage().propulsion();

    // ── Burn 1: optimized direction and timing ──
    AbsoluteDate burn1Start = epoch.shiftedBy(params.t1);
    Vector3D thrustDirection1 = Physics.buildThrustDirectionTNW(params.alpha1, params.beta1);

    ConstantThrustManeuver maneuverBurn1 =
        new ConstantThrustManeuver(
            burn1Start,
            params.dt1,
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            thrustDirection1);
    propagator.addForceModel(maneuverBurn1);

    // ── Burn 2: prograde circularization centered on apoapsis ──
    if (burn2.dt2 > 0.0) {
      double t2Start = params.t1 + params.dt1 + burn2.dtCoast;
      AbsoluteDate burn2Start = epoch.shiftedBy(t2Start);
      // Pure prograde in TNW frame
      Vector3D thrustDirection2 = Physics.buildThrustDirectionTNW(0.0, 0.0);

      ConstantThrustManeuver maneuverBurn2 =
          new ConstantThrustManeuver(
              burn2Start,
              burn2.dt2,
              propulsion.thrust(),
              propulsion.isp(),
              attitude,
              thrustDirection2);
      propagator.addForceModel(maneuverBurn2);
    }

    // ── Altitude tracker ──
    double maxAltThreshold = targetAltitude * 1.25;
    lastAltitudeTracker = new MinAltitudeTracker(80_000, maxAltThreshold, burn1Start);
    propagator.addEventDetector(lastAltitudeTracker);
  }

  /** Total maneuver duration from epoch to end of burn 2. */
  public double totalDuration(Burn1Params params, ResolvedBurn2 burn2) {
    return params.t1 + params.dt1 + burn2.dtCoast + burn2.dt2;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Burn 1 propagation (fast, for timing resolution)
  // ════════════════════════════════════════════════════════════════════════

  private SpacecraftState propagateBurn1(SpacecraftState initialState, Burn1Params params) {
    NumericalPropagator burn1Propagator = OrekitService.get().createSimplePropagator();
    burn1Propagator.setInitialState(initialState);

    AbsoluteDate burn1Start = initialState.getDate().shiftedBy(params.t1);
    AbsoluteDate burn1End = burn1Start.shiftedBy(params.dt1);

    LofOffset attitude = new LofOffset(initialState.getFrame(), LOFType.TNW);
    PropulsionSystem propulsion = vehicle.getSecondStage().propulsion();
    Vector3D thrustDir1 = Physics.buildThrustDirectionTNW(params.alpha1, params.beta1);

    burn1Propagator.addForceModel(
        new ConstantThrustManeuver(
            burn1Start, params.dt1, propulsion.thrust(), propulsion.isp(), attitude, thrustDir1));

    try {
      SpacecraftState stateAfterBurn1 = burn1Propagator.propagate(burn1End);
      if (Math.abs(stateAfterBurn1.getDate().durationFrom(burn1End)) > 1.0) {
        return null;
      }
      return stateAfterBurn1;
    } catch (Exception e) {
      return null;
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // Burn 2 resolution — deterministic circularization at apoapsis
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Resolves burn 2 deterministically from the post-burn-1 state:
   *
   * <ol>
   *   <li>Coast to first apoapsis (with J2)
   *   <li>Compute orbital velocity at apoapsis
   *   <li>Compute circular velocity at that altitude
   *   <li>ΔV = vCirc - vAtApo → burn duration via Tsiolkovsky
   *   <li>Center burn on apoapsis: dtCoast = dtApogee - dt2/2
   * </ol>
   *
   * @return resolved burn 2 parameters, or null on failure
   */
  public ResolvedBurn2 resolveBurn2(SpacecraftState stateAfterBurn1) {
    // ── Find apoapsis time ──
    double dtApogee = detectTimeToApoapsis(stateAfterBurn1);
    if (Double.isNaN(dtApogee)) {
      return null;
    }

    // ── Get orbital state at apoapsis for circularization computation ──
    KeplerianOrbit orbitAfterBurn1 = new KeplerianOrbit(stateAfterBurn1.getOrbit());
    double mu = orbitAfterBurn1.getMu();
    double a = orbitAfterBurn1.getA();
    double e = orbitAfterBurn1.getE();
    double rApoapsis = a * (1.0 + e);

    // Velocity at apoapsis on the current elliptical orbit
    double vAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / a));
    // Circular velocity at apoapsis altitude
    double rTarget = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + targetAltitude;
    double vCircTarget = FastMath.sqrt(mu / rTarget);
    // ΔV needed (prograde)
    double dvNeeded = vCircTarget - vAtApoapsis;

    if (dvNeeded <= 0.0) {
      // Already circular or hyperbolic — no burn needed
      return new ResolvedBurn2(dtApogee, 0.0, 0.0);
    }

    // ── Compute burn duration via Tsiolkovsky ──
    PropulsionSystem propulsion = vehicle.getSecondStage().propulsion();
    double massAtApoapsis = stateAfterBurn1.getMass(); // approximate (coast is ballistic)
    double dt2 =
        Physics.computeBurnDuration(
            dvNeeded, massAtApoapsis, propulsion.isp(), propulsion.thrust());

    // ── Center burn on apoapsis ──
    double dtCoast = FastMath.max(dtApogee - dt2 / 2.0, 0.0);

    return new ResolvedBurn2(dtCoast, FastMath.max(dt2, 10.0), dvNeeded);
  }

  /**
   * Re-resolve burn 2 from a given initial state and burn 1 params. Convenience method for the
   * Stage runtime (same logic as during optimization).
   */
  public ResolvedBurn2 resolveBurn2FromInitial(SpacecraftState initialState, Burn1Params params) {
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return null;
    }
    ResolvedBurn2 burn2 = resolveBurn2(stateAfterBurn1);
    logger.info(
        "Burn 2 resolved: dtCoast = {}, dt2 = {}, dvNeeded = {}",
        burn2.dtCoast,
        burn2.dt2,
        burn2.dvNeeded);
    return burn2;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Apoapsis detection
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Detects the first apoapsis after burn 1 using a propagator with J2.
   *
   * @return time from stateAfterBurn1 to apoapsis (s), or NaN on failure
   */
  private double detectTimeToApoapsis(SpacecraftState stateAfterBurn1) {
    NumericalPropagator coastPropagator = OrekitService.get().createOptimizationPropagator();
    coastPropagator.setInitialState(stateAfterBurn1);

    RecordAndContinue recorder = new RecordAndContinue();
    ApsideDetector apsideDetector =
        new ApsideDetector(stateAfterBurn1.getOrbit()).withHandler(recorder);
    coastPropagator.addEventDetector(apsideDetector);

    double maxCoast = stateAfterBurn1.getOrbit().getKeplerianPeriod() * 0.55;
    coastPropagator.propagate(stateAfterBurn1.getDate().shiftedBy(maxCoast));

    for (RecordAndContinue.Event event : recorder.getEvents()) {
      if (!event.isIncreasing()) {
        double dtApogee = event.getState().getDate().durationFrom(stateAfterBurn1.getDate());
        return Math.max(dtApogee, 0.0);
      }
    }
    return Double.NaN;
  }
}
