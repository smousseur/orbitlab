package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.FailFastEnvelope;
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
 *   <li><b>Circularization burn (at apoapsis)</b> — deterministic prograde correction. Raises the
 *       perigee to match the apoapsis, producing a circular orbit. Centered on the apoapsis
 *       passage.
 * </ol>
 *
 * <p>This is a special case of {@link TransferManeuver} where the target orbit is circular: burn 1
 * is followed by an analytic circularization burn. Optimization parameters are the same 4
 * parameters of burn 1 (the circularization burn is fully determined by the post-burn-1 state).
 */
public class TransfertTwoManeuver extends TransferManeuver {
  private static final Logger logger = LogManager.getLogger(TransfertTwoManeuver.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final CircularizationBurnResolver circularizationBurnResolver;
  private final FailFastEnvelope failFast;

  /**
   * Creates a two-burn transfer maneuver targeting the specified circular orbit altitude with the
   * default {@link FailFastEnvelope}.
   *
   * @param vehicle the vehicle performing the transfer
   */
  public TransfertTwoManeuver(Vehicle vehicle, double targetAltitude) {
    this(vehicle, targetAltitude, FailFastEnvelope.defaults());
  }

  /**
   * Creates a two-burn transfer maneuver with an explicit fail-fast envelope.
   *
   * <p>The envelope is consumed by {@link #propagateForOptimization} to short-circuit Step 3 when
   * the post-burn-1 orbit lies outside the configured eccentricity / semi-major-axis bounds.
   */
  public TransfertTwoManeuver(
      Vehicle vehicle, double targetAltitude, FailFastEnvelope failFast) {
    super(vehicle, targetAltitude);
    this.circularizationBurnResolver = new CircularizationBurnResolver(vehicle);
    this.failFast = failFast;
  }

  /**
   * Deterministically resolved circularization burn parameters (next apoapsis).
   *
   * @param dtCoast coast duration from end of burn 1 to start of the circularization burn (seconds)
   * @param dt2 duration of the circularization burn (seconds)
   * @param dvNeeded the delta-V required for circularization (m/s)
   */
  public record ResolvedCircularizationBurn(double dtCoast, double dt2, double dvNeeded) {}

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
  @Override
  public TransferResult propagateForOptimization(SpacecraftState initialState, double[] variables) {
    Burn1Params params = decode(variables);

    // ── Step 1: Propagate burn 1 to get post-burn state ──
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return new TransferResult(initialState, null, null, null); // penalty
    }
    KeplerianOrbit orbitPostBurn1 = new KeplerianOrbit(stateAfterBurn1.getOrbit());
    double aMax = EARTH_RADIUS + targetAltitude + failFast.semiMajorAxisOffsetMax();
    if (orbitPostBurn1.getE() > failFast.eccentricityMax()
        || orbitPostBurn1.getA() < EARTH_RADIUS
        || orbitPostBurn1.getA() > aMax) {
      // Pass orbitPostBurn1 through so the cost function can grade the failure
      // by orbital-element distance instead of falling back to a flat 1e6 wall.
      return new TransferResult(initialState, orbitPostBurn1, null, null);
    }

    // ── Step 2: Resolve circularization burn at next apoapsis ──
    ResolvedCircularizationBurn circBurn =
        circularizationBurnResolver.resolveCircularizationBurn(stateAfterBurn1);
    if (circBurn == null) {
      return new TransferResult(initialState, orbitPostBurn1, null, null); // penalty
    }

    // ── Step 3: Full propagation with both burns ──
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(initialState);
    MinAltitudeTracker tracker = configure(propagator, initialState, params, circBurn);

    double totalTime = totalDuration(params, circBurn);
    AbsoluteDate endDate = initialState.getDate().shiftedBy(totalTime);
    try {
      SpacecraftState finalState = propagator.propagate(endDate);

      if (Math.abs(finalState.getDate().durationFrom(endDate)) > 1.0) {
        /*
               logger.warn(
                   "PENALTY[step3-truncated] expected={}s, actual={}s, postBurn1: a={}, e={}, dvNeeded={}",
                   totalTime,
                   finalState.getDate().durationFrom(initialState.getDate()),
                   orbitPostBurn1.getA(),
                   orbitPostBurn1.getE(),
                   circBurn.dvNeeded);
        */
        return new TransferResult(initialState, orbitPostBurn1, circBurn, tracker);
      }
      return new TransferResult(finalState, orbitPostBurn1, circBurn, tracker);
    } catch (Exception e) {
      /*
      logger.warn(
          "PENALTY[step3-exception] {}, postBurn1: a={}, e={}, totalTime={}, dvNeeded={}",
          e.getMessage(),
          orbitPostBurn1.getA(),
          orbitPostBurn1.getE(),
          totalTime,
          circBurn.dvNeeded);

       */
      return new TransferResult(initialState, orbitPostBurn1, circBurn, tracker);
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
      ResolvedCircularizationBurn circBurn) {

    // ── Burn 1 + altitude tracker (delegated to the base class) ──
    MinAltitudeTracker altitudeTracker = super.configure(propagator, state, params);

    // ── Circularization burn: prograde at next apoapsis ──
    if (circBurn.dt2 > 0.0) {
      AbsoluteDate epoch = state.getDate();
      LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
      ActiveStageInfo stage = vehicle.resolveActiveStage(state.getMass());
      PropulsionSystem propulsion = stage.propulsion();

      double t2Start = params.t1() + params.dt1() + circBurn.dtCoast;
      AbsoluteDate burn2Start = epoch.shiftedBy(t2Start);
      Vector3D thrustDirection2 = Physics.buildThrustDirectionTNW(0.0, 0.0);

      propagator.addForceModel(
          new ConstantThrustManeuver(
              burn2Start,
              circBurn.dt2,
              propulsion.thrust(),
              propulsion.isp(),
              attitude,
              thrustDirection2));
    }

    return altitudeTracker;
  }

  /** Total maneuver duration from epoch to end of the circularization burn. */
  public double totalDuration(Burn1Params params, ResolvedCircularizationBurn circBurn) {
    return super.totalDuration(params) + circBurn.dtCoast + circBurn.dt2;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Circularization burn resolution — delegates to CircularizationBurnResolver
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Resolves the circularization burn deterministically from the post-burn-1 state.
   *
   * @return resolved circularization burn parameters, or null on failure
   */
  public ResolvedCircularizationBurn resolveCircularizationBurn(SpacecraftState stateAfterBurn1) {
    return circularizationBurnResolver.resolveCircularizationBurn(stateAfterBurn1);
  }

  /**
   * Re-resolve the circularization burn from initial state + burn 1 params. Convenience for Stage
   * runtime.
   */
  public ResolvedCircularizationBurn resolveCircularizationBurnFromInitial(
      SpacecraftState initialState, Burn1Params params) {
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return null;
    }
    return circularizationBurnResolver.resolveCircularizationBurn(stateAfterBurn1);
  }
}
