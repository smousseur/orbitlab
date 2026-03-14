package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * Resolves burn 2 (circularization at next apoapsis) deterministically from a post-burn-1 state.
 *
 * <p>Pure computation — no mutable state.
 */
final class Burn2Resolver {

  private final Vehicle vehicle;

  Burn2Resolver(Vehicle vehicle) {
    this.vehicle = vehicle;
  }

  /**
   * Resolves burn 2 from the post-burn-1 state:
   *
   * <ol>
   *   <li>Detect the next apoapsis after burn 1 (with J2)
   *   <li>Compute orbital velocity at apoapsis
   *   <li>Compute circular velocity at that altitude
   *   <li>ΔV = vCirc - vAtApoapsis → burn duration via Tsiolkovsky
   *   <li>Center burn on apoapsis: dtCoast = dtApoapsis - dt2/2
   * </ol>
   *
   * @return resolved burn 2 parameters, or null on failure
   */
  TransfertTwoManeuver.ResolvedBurn2 resolveBurn2(SpacecraftState stateAfterBurn1) {
    double dtApoapsis = detectTimeToApoapsis(stateAfterBurn1);
    if (Double.isNaN(dtApoapsis)) {
      return null;
    }

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
    double dvNeeded = vCirc - vAtApoapsis;

    if (dvNeeded <= 0.0) {
      // Already circular or hyperbolic — no burn needed
      return new TransfertTwoManeuver.ResolvedBurn2(dtApoapsis, 0.0, 0.0);
    }

    PropulsionSystem propulsion = vehicle.getSecondStage().propulsion();
    double massAtApoapsis = stateAfterBurn1.getMass(); // approximate (coast is ballistic)
    double dt2 =
        Physics.computeBurnDuration(
            dvNeeded, massAtApoapsis, propulsion.isp(), propulsion.thrust());

    // Center burn on apoapsis
    double dtCoast = FastMath.max(dtApoapsis - dt2 / 2.0, 0.0);

    return new TransfertTwoManeuver.ResolvedBurn2(dtCoast, dt2, dvNeeded);
  }

  /**
   * Detects the next apoapsis after burn 1 using a propagator with J2.
   *
   * @return time from stateAfterBurn1 to next apoapsis (s), or NaN on failure
   */
  private double detectTimeToApoapsis(SpacecraftState stateAfterBurn1) {
    NumericalPropagator coastPropagator = OrekitService.get().createOptimizationPropagator();
    coastPropagator.setInitialState(stateAfterBurn1);

    RecordAndContinue recorder = new RecordAndContinue();
    ApsideDetector apsideDetector =
        new ApsideDetector(stateAfterBurn1.getOrbit()).withHandler(recorder);
    coastPropagator.addEventDetector(apsideDetector);

    // Search up to 1.1 orbital periods — enough for one full orbit + margin
    double maxCoast =
        FastMath.min(
            stateAfterBurn1.getOrbit().getKeplerianPeriod() * 1.1,
            7000.0 // max ~2h, protects against highly eccentric orbits
            );
    coastPropagator.propagate(stateAfterBurn1.getDate().shiftedBy(maxCoast));

    // Skip apoapsis events that are too close (< half a period)
    double minCoast = stateAfterBurn1.getOrbit().getKeplerianPeriod() * 0.4;

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
