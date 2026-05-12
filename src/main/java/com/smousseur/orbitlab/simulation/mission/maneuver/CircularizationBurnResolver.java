package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * Resolves the circularization burn (at next apoapsis) deterministically from a post-burn-1 state.
 *
 * <p>Pure computation — no mutable state. The active stage propulsion is resolved automatically
 * from the spacecraft mass via {@link Vehicle#resolveActiveStage(double)}.
 */
final class CircularizationBurnResolver {

  private final Vehicle vehicle;

  CircularizationBurnResolver(Vehicle vehicle) {
    this.vehicle = vehicle;
  }

  /**
   * Resolves the circularization burn from the post-burn-1 state:
   *
   * <ol>
   *   <li>Detect the next apoapsis after burn 1 (with J2)
   *   <li>Compute orbital velocity at apoapsis
   *   <li>Compute circular velocity at that altitude
   *   <li>ΔV = vCirc - vAtApoapsis → burn duration via Tsiolkovsky
   *   <li>Center burn on apoapsis: dtCoast = dtApoapsis - dt2/2
   * </ol>
   *
   * @return resolved circularization burn parameters, or null on failure
   */
  TransfertTwoManeuver.ResolvedCircularizationBurn resolveCircularizationBurn(
      SpacecraftState stateAfterBurn1) {
    double dtApoapsis = detectTimeToApoapsis(stateAfterBurn1);
    if (Double.isNaN(dtApoapsis)) {
      return null;
    }

    double dvNeeded = getDvNeeded(stateAfterBurn1);

    if (dvNeeded <= 0.0) {
      // Already circular or hyperbolic — no burn needed
      return new TransfertTwoManeuver.ResolvedCircularizationBurn(dtApoapsis, 0.0, 0.0);
    }

    ActiveStageInfo stage = vehicle.resolveActiveStage(stateAfterBurn1.getMass());
    PropulsionSystem propulsion = stage.propulsion();
    double massAtApoapsis = stateAfterBurn1.getMass(); // approximate (coast is ballistic)
    double dt2 =
        Physics.computeBurnDuration(
            dvNeeded, massAtApoapsis, propulsion.isp(), propulsion.thrust());

    // Sanity check: a circularization burn longer than the orbital period
    // (or longer than 1 hour) usually means the post-burn-1 orbit is so
    // off-target that the prograde-only model is no longer meaningful.
    // Bail out to a graded penalty instead of forcing a very long Step 3 propagation.
    double period = stateAfterBurn1.getOrbit().getKeplerianPeriod();
    if (!Double.isFinite(dt2) || dt2 > FastMath.min(period, 3600.0)) {
      return null;
    }

    // Center burn on apoapsis
    double dtCoast = FastMath.max(dtApoapsis - dt2 / 2.0, 0.0);

    return new TransfertTwoManeuver.ResolvedCircularizationBurn(dtCoast, dt2, dvNeeded);
  }

  private static double getDvNeeded(SpacecraftState stateAfterBurn1) {
    KeplerianOrbit orbitAfterBurn1 = new KeplerianOrbit(stateAfterBurn1.getOrbit());
    double mu = orbitAfterBurn1.getMu();
    double a = orbitAfterBurn1.getA();
    double e = orbitAfterBurn1.getE();
    double rApoapsis = a * (1.0 + e);

    // Velocity at apoapsis on the current orbit
    double vAtApoapsis = FastMath.sqrt(mu * (2.0 / rApoapsis - 1.0 / a));
    // Circular velocity at apoapsis altitude
    double vCirc = FastMath.sqrt(mu / rApoapsis);
    // ΔV needed (prograde — raises perigee to circularize)
    return vCirc - vAtApoapsis;
  }

  /**
   * Detects the next apoapsis after burn 1 using a propagator with J2.
   *
   * @return time from stateAfterBurn1 to next apoapsis (s), or NaN on failure
   */
  private static double detectTimeToApoapsis(SpacecraftState stateAfterBurn1) {
    NumericalPropagator coastPropagator = OrekitService.get().createOptimizationPropagator();
    coastPropagator.setInitialState(stateAfterBurn1);

    RecordAndContinue recorder = new RecordAndContinue();
    ApsideDetector apsideDetector =
        new ApsideDetector(stateAfterBurn1.getOrbit()).withHandler(recorder);
    coastPropagator.addEventDetector(apsideDetector);

    double period = stateAfterBurn1.getOrbit().getKeplerianPeriod();
    double maxCoast = period * 1.1;
    coastPropagator.propagate(stateAfterBurn1.getDate().shiftedBy(maxCoast));

    // Skip apoapsis events that are too close (< half a period)
    double minCoast = period * 0.4;

    for (RecordAndContinue.Event event : recorder.getEvents()) {
      if (!event.isIncreasing()) {
        double dt = event.getState().getDate().durationFrom(stateAfterBurn1.getDate());
        if (dt > minCoast) {
          return Math.max(dt, 0.0);
        }
      }
    }
    return Double.NaN;
  }
}
