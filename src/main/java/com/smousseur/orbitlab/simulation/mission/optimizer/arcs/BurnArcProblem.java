package com.smousseur.orbitlab.simulation.mission.optimizer.arcs;

import com.smousseur.orbitlab.core.Physics;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * Multi-arc trajectory problem with chemical propulsion (smoothed finite burns).
 *
 * <p>Each arc is parameterized by 5 unconstrained variables:
 *
 * <ul>
 *   <li>tStart: ignition time (sigmoid → [0, T])
 *   <li>duration: burn duration (sigmoid → [0, maxBurnDuration])
 *   <li>alpha: in-plane angle (free, radians)
 *   <li>delta: out-of-plane angle (tanh → [-π/2, π/2])
 *   <li>throttle: thrust level (sigmoid → [0, 1])
 * </ul>
 */
public abstract class BurnArcProblem implements TrajectoryProblem {

  protected static final int PARAMS_PER_ARC = 5;

  protected final SpacecraftState initialState;
  protected final AbsoluteDate targetDate;
  protected final PropulsionSystem propulsion;
  protected final int numArcs;
  protected final double totalDuration;
  protected double maxBurnDuration;

  protected BurnArcProblem(
      SpacecraftState initialState,
      AbsoluteDate targetDate,
      PropulsionSystem propulsion,
      int numArcs) {
    this.initialState = initialState;
    this.targetDate = targetDate;
    this.propulsion = propulsion;
    this.numArcs = numArcs;
    this.totalDuration = targetDate.durationFrom(initialState.getDate());

    // TODO compute reasonable maxBurnDuration : ΔV max → durée max de burn
    double maxDv = 2000.0;
    this.maxBurnDuration = maxDv * initialState.getMass() / propulsion.thrust();
  }

  @Override
  public int getNumVariables() {
    return numArcs * PARAMS_PER_ARC;
  }

  // ========== Encoding / Decoding (common to all multi-arc problems) ==========

  /** Converts unconstrained variables into bounded physical parameters. */
  @Override
  public double[] toPhysical(double[] x) {
    double[] physical = new double[x.length];
    for (int arc = 0; arc < numArcs; arc++) {
      int offset = arc * PARAMS_PER_ARC;
      physical[offset] = Physics.sigmoid(x[offset]) * totalDuration;
      physical[offset + 1] = Physics.sigmoid(x[offset + 1]) * maxBurnDuration;
      physical[offset + 2] = x[offset + 2];
      physical[offset + 3] = FastMath.tanh(x[offset + 3]) * FastMath.PI / 2.0;
      physical[offset + 4] = Physics.sigmoid(x[offset + 4]);
    }
    return physical;
  }

  /** Encodes an arc into the array of unconstrained parameters. */
  protected void encodeArc(
      double[] params,
      int arcIndex,
      double tStart,
      double duration,
      double alpha,
      double delta,
      double throttle) {
    int offset = arcIndex * PARAMS_PER_ARC;

    double tFrac = FastMath.max(0.001, FastMath.min(0.999, tStart / totalDuration));
    double dFrac = FastMath.max(0.001, FastMath.min(0.999, duration / maxBurnDuration));
    double thr = FastMath.max(0.01, FastMath.min(0.99, throttle));

    params[offset] = Physics.inverseSigmoid(tFrac, 0.0, 1.0);
    params[offset + 1] = Physics.inverseSigmoid(dFrac, 0.0, 1.0);
    params[offset + 2] = alpha;
    params[offset + 3] = Physics.inverseScaledTanh(delta);
    params[offset + 4] = Physics.inverseSigmoid(thr, 0.0, 1.0);
  }

  // ========== Propagation (common: propagator + BurnArcForceModel) ==========

  @Override
  public SpacecraftState propagate(double[] variables) {
    double[] physical = toPhysical(variables);
    NumericalPropagator propagator = OrekitService.get().getDefaultPropagator();
    AbsoluteDate startDate = initialState.getDate();

    for (int arc = 0; arc < numArcs; arc++) {
      int offset = arc * PARAMS_PER_ARC;
      propagator.addForceModel(
          new BurnArcForceModel(
              propulsion,
              startDate,
              physical[offset], // tStart
              physical[offset + 1], // duration
              physical[offset + 2], // alpha
              physical[offset + 3], // delta
              physical[offset + 4] // throttle
              ));
    }

    propagator.setInitialState(initialState);

    try {
      return propagator.propagate(targetDate);
    } catch (Exception e) {
      return buildFallbackState();
    }
  }

  /**
   * Builds a penalizing fallback state when propagation fails. Subclasses can override to adapt to
   * the orbital regime.
   */
  protected SpacecraftState buildFallbackState() {
    Vector3D badPos = initialState.getPVCoordinates().getPosition().scalarMultiply(0.5);
    Vector3D badVel = initialState.getPVCoordinates().getPosition().normalize().scalarMultiply(1e4);
    PVCoordinates badPV = new PVCoordinates(badPos, badVel);
    CartesianOrbit badOrbit =
        new CartesianOrbit(
            badPV,
            initialState.getFrame(),
            initialState.getDate(),
            initialState.getOrbit().getMu());
    return new SpacecraftState(badOrbit).withMass(initialState.getMass());
  }
}
