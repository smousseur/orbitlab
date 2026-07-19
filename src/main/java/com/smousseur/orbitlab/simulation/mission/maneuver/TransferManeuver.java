package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
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
 * Single-burn orbit transfer optimized by CMA-ES (4 parameters).
 *
 * <p>The spacecraft arrives near apoapsis on a highly elliptical sub-orbital orbit. Burn 1 raises
 * the perigee and shapes the orbit to match the target {@code (perigee, apogee)} altitudes. The
 * post-burn state is the final state of the transfer; no circularization is performed at this level
 * — see {@link TransfertTwoManeuver} for the two-burn variant.
 *
 * <p>Optimization parameter vector (4 dimensions):
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
public class TransferManeuver {
  private static final Logger logger = LogManager.getLogger(TransferManeuver.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  protected final Vehicle vehicle;
  protected final double targetAltitude;

  /**
   * Creates a single-burn transfer maneuver. The {@code targetAltitude} is used by the in-flight
   * altitude tracker to derive a maximum-altitude threshold; it does not directly drive the burn
   * parameters (those are optimized).
   *
   * @param vehicle the vehicle performing the transfer
   * @param targetAltitude reference altitude used by the altitude tracker (m)
   */
  public TransferManeuver(Vehicle vehicle, double targetAltitude) {
    this.vehicle = vehicle;
    this.targetAltitude = targetAltitude;
  }

  /**
   * The 4 CMA-ES-optimized parameters for burn 1.
   *
   * @param t1 offset of burn 1 start from epoch (seconds)
   * @param dt1 duration of burn 1 (seconds)
   * @param alpha1 in-plane thrust angle in the TNW frame (radians)
   * @param beta1 out-of-plane thrust angle in the TNW frame (radians)
   */
  public record Burn1Params(double t1, double dt1, double alpha1, double beta1) {}

  /**
   * Decodes a raw CMA-ES variable array into typed burn 1 parameters.
   *
   * @param variables the 4-element optimization variable array [t1, dt1, alpha1, beta1]
   * @return the decoded burn 1 parameters
   */
  public Burn1Params decode(double[] variables) {
    return new Burn1Params(variables[0], variables[1], variables[2], variables[3]);
  }

  /**
   * Full propagation for CMA-ES evaluation: propagates burn 1 and returns the resulting state.
   *
   * @return a {@link TransferResult} with the final state and captured metadata; the {@code
   *     circularizationBurn} field is always {@code null} for this single-burn variant
   */
  public TransferResult propagateForOptimization(SpacecraftState initialState, double[] variables) {
    Burn1Params params = decode(variables);

    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return new TransferResult(initialState, null, null, null);
    }
    KeplerianOrbit orbitPostBurn1 = new KeplerianOrbit(stateAfterBurn1.getOrbit());
    if (orbitPostBurn1.getE() > 0.95
        || orbitPostBurn1.getA() < EARTH_RADIUS
        || orbitPostBurn1.getA() > EARTH_RADIUS + targetAltitude + 2_000_000) {
      return new TransferResult(initialState, orbitPostBurn1, null, null);
    }

    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(initialState);
    MinAltitudeTracker tracker = configure(propagator, initialState, params);
    // dt1 may explore up to full depletion (spec 06 I6): truncate infeasible candidates quietly.
    DepletionGuard.armQuiet(
        propagator, vehicle.resolveActiveStage(initialState.getMass()).depletionFloor());

    double totalTime = totalDuration(params);
    AbsoluteDate endDate = initialState.getDate().shiftedBy(totalTime);
    try {
      SpacecraftState finalState = propagator.propagate(endDate);
      if (Math.abs(finalState.getDate().durationFrom(endDate)) > 1.0) {
        return new TransferResult(initialState, orbitPostBurn1, null, tracker);
      }
      return new TransferResult(finalState, orbitPostBurn1, null, tracker);
    } catch (Exception e) {
      logger.debug("Transfer propagation failed (penalty applied): {}", e.getMessage());
      return new TransferResult(initialState, orbitPostBurn1, null, tracker);
    }
  }

  /**
   * Configures burn 1 on the given propagator and returns the altitude tracker.
   *
   * @return the {@link MinAltitudeTracker} added to the propagator
   */
  public MinAltitudeTracker configure(
      NumericalPropagator propagator, SpacecraftState state, Burn1Params params) {

    AbsoluteDate epoch = state.getDate();
    LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
    ActiveStageInfo stage = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion = stage.propulsion();

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

    double maxAltThreshold = targetAltitude * 2.0;
    MinAltitudeTracker altitudeTracker =
        new MinAltitudeTracker(80_000, maxAltThreshold, burn1Start);
    propagator.addEventDetector(altitudeTracker);
    return altitudeTracker;
  }

  /** Total maneuver duration from epoch to end of burn 1. */
  public double totalDuration(Burn1Params params) {
    return params.t1 + params.dt1;
  }

  /**
   * Quick burn-1 propagation (no tracker, simple gravity) — used for orbit-validity checks and to
   * resolve downstream events such as the next apoapsis.
   */
  protected SpacecraftState propagateBurn1(SpacecraftState initialState, Burn1Params params) {
    NumericalPropagator burn1Propagator = OrekitService.get().createSimplePropagator();
    burn1Propagator.setInitialState(initialState);
    DepletionGuard.armQuiet(
        burn1Propagator, vehicle.resolveActiveStage(initialState.getMass()).depletionFloor());

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
}
