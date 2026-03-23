package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Two-burn orbit transfer:
 *
 * <ol>
 *   <li><b>Burn 1 (near apoapsis)</b> — optimized by CMA-ES (4 parameters). The spacecraft arrives
 *       from the gravity turn near apoapsis on a highly elliptical sub-orbital orbit. Burn 1 does
 *       the heavy lifting: it raises the perigee and quasi-circularizes the orbit near the target
 *       altitude. The result is a near-circular orbit with a slight residual eccentricity.
 *   <li><b>Coast to next apoapsis</b> — the spacecraft coasts approximately one full orbit to reach
 *       the apoapsis of the post-burn-1 orbit.
 *   <li><b>Burn 2 (at apoapsis)</b> — deterministic prograde circularization. Raises the perigee to
 *       match the apoapsis, producing a circular orbit. Centered on the apoapsis passage.
 * </ol>
 *
 * <p>Optimization parameter vector (4 dimensions — only burn 1):
 *
 * <ul>
 *   <li>[0] t1 — offset of burn 1 start from epoch (s)
 *   <li>[1] dt1 — duration of burn 1 (s)
 *   <li>[2] alpha1 — in-plane thrust angle in TNW frame (rad)
 *   <li>[3] beta1 — out-of-plane thrust angle in TNW frame (rad)
 * </ul>
 *
 * <p>The active stage propulsion is resolved automatically from the spacecraft mass via {@link
 * Vehicle#resolveActiveStage(double)}.
 */
public class TransfertTwoManeuver {
  private static final Logger logger = LogManager.getLogger(TransfertTwoManeuver.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final Vehicle vehicle;
  private final double targetAltitude;
  private final Burn2Resolver burn2Resolver;

  /**
   * Creates a two-burn transfer maneuver targeting the specified circular orbit altitude.
   *
   * @param vehicle the vehicle performing the transfer
   * @param targetAltitude the target circular orbit altitude above Earth's surface in meters
   */
  public TransfertTwoManeuver(Vehicle vehicle, double targetAltitude) {
    this.vehicle = vehicle;
    this.targetAltitude = targetAltitude;
    this.burn2Resolver = new Burn2Resolver(vehicle);
  }

  // ════════════════════════════════════════════════════════════════════════
  // Parameter records
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The 4 CMA-ES-optimized parameters for burn 1 (orbit insertion near apoapsis).
   *
   * @param t1 offset of burn 1 start from epoch (seconds)
   * @param dt1 duration of burn 1 (seconds)
   * @param alpha1 in-plane thrust angle in the TNW frame (radians)
   * @param beta1 out-of-plane thrust angle in the TNW frame (radians)
   */
  public record Burn1Params(double t1, double dt1, double alpha1, double beta1) {}

  /**
   * Deterministically resolved burn 2 parameters for circularization at the next apoapsis.
   *
   * @param dtCoast coast duration from end of burn 1 to start of burn 2 (seconds)
   * @param dt2 duration of burn 2 (seconds)
   * @param dvNeeded the delta-V required for circularization (m/s)
   */
  public record ResolvedBurn2(double dtCoast, double dt2, double dvNeeded) {}

  /**
   * Decodes a raw CMA-ES variable array into typed burn 1 parameters.
   *
   * @param variables the 4-element optimization variable array [t1, dt1, alpha1, beta1]
   * @return the decoded burn 1 parameters
   */
  public Burn1Params decode(double[] variables) {
    return new Burn1Params(variables[0], variables[1], variables[2], variables[3]);
  }

  // ════════════════════════════════════════════════════════════════════════
  // Optimization entry point
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Full propagation for CMA-ES evaluation:
   *
   * <ol>
   *   <li>Propagate burn 1 (near apoapsis — quasi-circularizes)
   *   <li>Resolve burn 2 at next apoapsis (circularization correction)
   *   <li>Full propagation with both burns
   * </ol>
   *
   * @return a {@link TransferResult} with the final state and captured metadata; fields are null in
   *     penalty cases
   */
  public TransferResult propagateForOptimization(SpacecraftState initialState, double[] variables) {
    Burn1Params params = decode(variables);

    // ── Step 1: Propagate burn 1 to get post-burn state ──
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return new TransferResult(initialState, null, null, null); // penalty
    }
    KeplerianOrbit orbit = new KeplerianOrbit(stateAfterBurn1.getOrbit());
    if (orbit.getE() > 0.95
        || orbit.getA() < EARTH_RADIUS
        || orbit.getA() > EARTH_RADIUS + 2_000_000) {
      return new TransferResult(initialState, null, null, null); // non-viable orbit, penalty
    }

    KeplerianOrbit orbitPostBurn1 = orbit;

    // ── Step 2: Resolve burn 2 at next apoapsis ──
    ResolvedBurn2 burn2 = burn2Resolver.resolveBurn2(stateAfterBurn1);
    if (burn2 == null) {
      return new TransferResult(initialState, orbitPostBurn1, null, null); // penalty
    }

    // ── Step 3: Full propagation with both burns ──
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(initialState);
    MinAltitudeTracker tracker = configure(propagator, initialState, params, burn2);

    double totalTime = totalDuration(params, burn2);
    AbsoluteDate endDate = initialState.getDate().shiftedBy(totalTime);
    try {
      SpacecraftState finalState = propagator.propagate(endDate);
      if (Math.abs(finalState.getDate().durationFrom(endDate)) > 1.0) {
        return new TransferResult(initialState, orbitPostBurn1, burn2, tracker); // penalty
      }
      return new TransferResult(finalState, orbitPostBurn1, burn2, tracker);
    } catch (Exception e) {
      logger.debug("Transfer propagation failed (penalty applied): {}", e.getMessage());
      return new TransferResult(initialState, orbitPostBurn1, burn2, tracker); // penalty
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // Configure — single source of truth for optimizer and Stage runtime
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Configures both burns on the given propagator and returns the altitude tracker.
   *
   * @return the {@link MinAltitudeTracker} added to the propagator
   */
  public MinAltitudeTracker configure(
      NumericalPropagator propagator,
      SpacecraftState state,
      Burn1Params params,
      ResolvedBurn2 burn2) {

    AbsoluteDate epoch = state.getDate();
    LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
    ActiveStageInfo stage = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion = stage.propulsion();

    // ── Burn 1: optimized (near apoapsis, quasi-circularizes) ──
    AbsoluteDate burn1Start = epoch.shiftedBy(params.t1);
    Vector3D thrustDirection1 = Physics.buildThrustDirectionTNW(params.alpha1, params.beta1);

    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn1Start,
            params.dt1,
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            thrustDirection1));

    // ── Burn 2: prograde circularization at next apoapsis ──
    if (burn2.dt2 > 0.0) {
      double t2Start = params.t1 + params.dt1 + burn2.dtCoast;
      AbsoluteDate burn2Start = epoch.shiftedBy(t2Start);
      Vector3D thrustDirection2 = Physics.buildThrustDirectionTNW(0.0, 0.0);

      propagator.addForceModel(
          new ConstantThrustManeuver(
              burn2Start,
              burn2.dt2,
              propulsion.thrust(),
              propulsion.isp(),
              attitude,
              thrustDirection2));
    }

    // ── Altitude tracker ──
    double maxAltThreshold = targetAltitude * 1.25;
    MinAltitudeTracker altitudeTracker =
        new MinAltitudeTracker(80_000, maxAltThreshold, burn1Start);
    propagator.addEventDetector(altitudeTracker);
    return altitudeTracker;
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
    ActiveStageInfo stage = vehicle.resolveActiveStage(initialState.getMass());
    PropulsionSystem propulsion = stage.propulsion();
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
      logger.debug("Burn 1 propagation failed (penalty applied): {}", e.getMessage());
      return null;
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // Burn 2 resolution — delegates to Burn2Resolver
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Resolves burn 2 deterministically from the post-burn-1 state.
   *
   * @return resolved burn 2 parameters, or null on failure
   */
  public ResolvedBurn2 resolveBurn2(SpacecraftState stateAfterBurn1) {
    return burn2Resolver.resolveBurn2(stateAfterBurn1);
  }

  /** Re-resolve burn 2 from initial state + burn 1 params. Convenience for Stage runtime. */
  public ResolvedBurn2 resolveBurn2FromInitial(SpacecraftState initialState, Burn1Params params) {
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return null;
    }
    return burn2Resolver.resolveBurn2(stateAfterBurn1);
  }
}
