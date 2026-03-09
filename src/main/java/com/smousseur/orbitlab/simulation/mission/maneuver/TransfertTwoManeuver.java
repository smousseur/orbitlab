package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
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
 * Three-burn orbit transfer using an <b>inverted Hohmann + circularization</b> strategy:
 *
 * <p>The spacecraft arrives from the gravity turn near apoapsis on a highly elliptical
 * (sub-orbital) orbit. The transfer proceeds as follows:
 *
 * <ol>
 *   <li><b>Burn 1 (near apoapsis)</b> — optimized by CMA-ES (4 parameters). Prograde burn that
 *       raises the perigee, making the orbit less eccentric and survivable.
 *   <li><b>Coast to perigee</b>
 *   <li><b>Burn 2 (at perigee)</b> — deterministic. Prograde burn that raises the apoapsis to the
 *       target altitude. Centered on perigee passage.
 *   <li><b>Coast to apoapsis</b>
 *   <li><b>Burn 3 (at apoapsis)</b> — deterministic. Prograde circularization burn that raises the
 *       perigee to match the apoapsis, producing a circular orbit at the target altitude.
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
 */
public class TransfertTwoManeuver {
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final Vehicle vehicle;
  private final double targetAltitude;

  /** Tracks altitude extremes during the last propagation. */
  private MinAltitudeTracker lastAltitudeTracker;

  /** Resolved deterministic burns from the last propagation. */
  private ResolvedBurns lastResolvedBurns;

  public TransfertTwoManeuver(Vehicle vehicle, double targetAltitude) {
    this.vehicle = vehicle;
    this.targetAltitude = targetAltitude;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Parameter records
  // ════════════════════════════════════════════════════════════════════════

  /** The 4 CMA-ES-optimized parameters for burn 1 only. */
  public record Burn1Params(double t1, double dt1, double alpha1, double beta1) {}

  /** Deterministically resolved burn 2 (at perigee) and burn 3 (at apoapsis). */
  public record ResolvedBurns(
      double dtCoast2, double dt2, double dvBurn2, double dtCoast3, double dt3, double dvBurn3) {}

  public Burn1Params decode(double[] variables) {
    return new Burn1Params(variables[0], variables[1], variables[2], variables[3]);
  }

  public ResolvedBurns getLastResolvedBurns() {
    return lastResolvedBurns;
  }

  public MinAltitudeTracker getLastAltitudeTracker() {
    return lastAltitudeTracker;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Optimization entry point
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Full propagation for CMA-ES evaluation:
   *
   * <ol>
   *   <li>Propagate burn 1 (near apoapsis — raises perigee)
   *   <li>Resolve burn 2 at perigee (raises apoapsis to target)
   *   <li>Resolve burn 3 at apoapsis (circularize)
   *   <li>Full propagation with all three burns
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

    // ── Step 2: Resolve burns 2 and 3 deterministically ──
    ResolvedBurns burns = resolveBurns(stateAfterBurn1);
    if (burns == null) {
      return initialState; // penalty
    }
    this.lastResolvedBurns = burns;

    // ── Step 3: Full propagation with all three burns ──
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(initialState);
    configure(propagator, initialState, params, burns);

    double totalTime = totalDuration(params, burns);
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
  // Configure — single source of truth for optimizer and Stage runtime
  // ════════════════════════════════════════════════════════════════════════

  /** Configures all three burns on the given propagator. */
  public void configure(
      NumericalPropagator propagator,
      SpacecraftState state,
      Burn1Params params,
      ResolvedBurns burns) {

    AbsoluteDate epoch = state.getDate();
    LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
    PropulsionSystem propulsion = vehicle.getSecondStage().propulsion();

    // ── Burn 1: optimized (near apoapsis, raises perigee) ──
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

    // ── Burn 2: prograde at perigee (raises apoapsis to target) ──
    if (burns.dt2 > 0.0) {
      double t2Start = params.t1 + params.dt1 + burns.dtCoast2;
      AbsoluteDate burn2Start = epoch.shiftedBy(t2Start);
      Vector3D thrustDirection2 = Physics.buildThrustDirectionTNW(0.0, 0.0);

      propagator.addForceModel(
          new ConstantThrustManeuver(
              burn2Start,
              burns.dt2,
              propulsion.thrust(),
              propulsion.isp(),
              attitude,
              thrustDirection2));
    }

    // ── Burn 3: prograde at apoapsis (circularize) ──
    if (burns.dt3 > 0.0) {
      double t3Start = params.t1 + params.dt1 + burns.dtCoast2 + burns.dt2 + burns.dtCoast3;
      AbsoluteDate burn3Start = epoch.shiftedBy(t3Start);
      Vector3D thrustDirection3 = Physics.buildThrustDirectionTNW(0.0, 0.0);

      propagator.addForceModel(
          new ConstantThrustManeuver(
              burn3Start,
              burns.dt3,
              propulsion.thrust(),
              propulsion.isp(),
              attitude,
              thrustDirection3));
    }

    // ── Altitude tracker ──
    double maxAltThreshold = targetAltitude * 1.25;
    lastAltitudeTracker = new MinAltitudeTracker(80_000, maxAltThreshold, burn1Start);
    propagator.addEventDetector(lastAltitudeTracker);
  }

  /** Total maneuver duration from epoch to end of burn 3. */
  public double totalDuration(Burn1Params params, ResolvedBurns burns) {
    return params.t1 + params.dt1 + burns.dtCoast2 + burns.dt2 + burns.dtCoast3 + burns.dt3;
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
  // Burns 2 & 3 resolution — deterministic
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Resolves burns 2 and 3 deterministically from the post-burn-1 state:
   *
   * <p><b>Burn 2 (at perigee):</b> Hohmann-style raise of apoapsis to target altitude.
   *
   * <p><b>Burn 3 (at apoapsis):</b> Circularization — raise perigee to match apoapsis, producing a
   * circular orbit at the target altitude.
   *
   * @return resolved parameters for both burns, or null on failure
   */
  public ResolvedBurns resolveBurns(SpacecraftState stateAfterBurn1) {
    KeplerianOrbit orbitAfterBurn1 = new KeplerianOrbit(stateAfterBurn1.getOrbit());
    double mu = orbitAfterBurn1.getMu();
    double a1 = orbitAfterBurn1.getA();
    double e1 = orbitAfterBurn1.getE();
    double rPerigee = a1 * (1.0 - e1);
    double rTarget = EARTH_RADIUS + targetAltitude;

    // ════════════════════════════════
    // Burn 2: at perigee, raise apoapsis to rTarget
    // ════════════════════════════════
    double dtPerigee = detectTimeToPerigee(stateAfterBurn1);
    if (Double.isNaN(dtPerigee)) {
      return null;
    }

    double vAtPerigee = FastMath.sqrt(mu * (2.0 / rPerigee - 1.0 / a1));

    // Transfer orbit: perigee = rPerigee, apoapsis = rTarget
    double aTransfer = (rPerigee + rTarget) / 2.0;
    double vTransferAtPerigee = FastMath.sqrt(mu * (2.0 / rPerigee - 1.0 / aTransfer));
    double dvBurn2 = vTransferAtPerigee - vAtPerigee;

    PropulsionSystem propulsion = vehicle.getSecondStage().propulsion();
    double massAfterBurn1 = stateAfterBurn1.getMass();

    double dt2 = 0.0;
    double dtCoast2;
    if (dvBurn2 > 0.0) {
      dt2 =
          Physics.computeBurnDuration(
              dvBurn2, massAfterBurn1, propulsion.isp(), propulsion.thrust());
      dtCoast2 = FastMath.max(dtPerigee - dt2 / 2.0, 0.0);
    } else {
      dtCoast2 = dtPerigee;
    }

    // ════════════════════════════════
    // Burn 3: at apoapsis of transfer orbit, circularize
    // ════════════════════════════════

    // After burn 2, we're on the transfer orbit (rPerigee × rTarget)
    // We need to coast from perigee to apoapsis of this transfer orbit
    double transferPeriod =
        2.0 * FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);
    double dtCoast3Approx = transferPeriod / 2.0; // half period: perigee → apoapsis

    // Velocity at apoapsis (= rTarget) on the transfer orbit
    double vTransferAtApoapsis = FastMath.sqrt(mu * (2.0 / rTarget - 1.0 / aTransfer));
    // Circular velocity at target altitude
    double vCircTarget = FastMath.sqrt(mu / rTarget);
    double dvBurn3 = vCircTarget - vTransferAtApoapsis;

    // Mass after burn 2
    double massFlowRate = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
    double massAfterBurn2 = massAfterBurn1 - massFlowRate * dt2;

    double dt3 = 0.0;
    double dtCoast3;
    if (dvBurn3 > 0.0) {
      dt3 =
          Physics.computeBurnDuration(
              dvBurn3, massAfterBurn2, propulsion.isp(), propulsion.thrust());
      // Center burn 3 on apoapsis
      dtCoast3 = FastMath.max(dtCoast3Approx - dt3 / 2.0, 0.0);
    } else {
      dtCoast3 = dtCoast3Approx;
    }

    return new ResolvedBurns(dtCoast2, dt2, dvBurn2, dtCoast3, dt3, dvBurn3);
  }

  /** Re-resolve burns from initial state + burn 1 params. Convenience for Stage runtime. */
  public ResolvedBurns resolveBurnsFromInitial(SpacecraftState initialState, Burn1Params params) {
    SpacecraftState stateAfterBurn1 = propagateBurn1(initialState, params);
    if (stateAfterBurn1 == null) {
      return null;
    }
    return resolveBurns(stateAfterBurn1);
  }

  // ════════════════════════════════════════════════════════════════════════
  // Apside detection
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Detects the first perigee after burn 1 using a propagator with J2.
   *
   * @return time from stateAfterBurn1 to perigee (s), or NaN on failure
   */
  private double detectTimeToPerigee(SpacecraftState stateAfterBurn1) {
    NumericalPropagator coastPropagator = OrekitService.get().createOptimizationPropagator();
    coastPropagator.setInitialState(stateAfterBurn1);

    RecordAndContinue recorder = new RecordAndContinue();
    ApsideDetector apsideDetector =
        new ApsideDetector(stateAfterBurn1.getOrbit()).withHandler(recorder);
    coastPropagator.addEventDetector(apsideDetector);

    double maxCoast = stateAfterBurn1.getOrbit().getKeplerianPeriod() * 0.55;
    coastPropagator.propagate(stateAfterBurn1.getDate().shiftedBy(maxCoast));

    for (RecordAndContinue.Event event : recorder.getEvents()) {
      // ApsideDetector g-function = dot(position, velocity)
      //   isIncreasing = true  → perigee (r_dot goes negative to positive)
      //   isIncreasing = false → apoapsis (r_dot goes positive to negative)
      if (event.isIncreasing()) {
        double dtPerigee = event.getState().getDate().durationFrom(stateAfterBurn1.getDate());
        return Math.max(dtPerigee, 0.0);
      }
    }
    return Double.NaN;
  }
}
