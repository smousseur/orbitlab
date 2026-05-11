package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferManeuver;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;

/**
 * Optimization problem for a two-burn orbit transfer where only burn 1 is optimized.
 *
 * <p>This is a special case of {@link TransferProblem} where the target orbit is circular: burn 1
 * is followed by a deterministic prograde circularization burn at apoapsis (computed by the
 * maneuver class). Because burn 2 is deterministic, CMA-ES only needs to find the burn 1
 * parameters that produce a transfer orbit from which the circularization yields the best
 * circular orbit.
 *
 * <p>The cost function (inherited from {@link TransferProblem}) evaluates the <b>final</b> orbit
 * after both burns against the target apsides — both equal to the target circular altitude.
 *
 * <p>Parameter vector (4 dimensions, see {@link TransferProblem}):
 *
 * <ul>
 *   <li>[0] t1 — offset of burn 1 start from epoch (s)
 *   <li>[1] dt1 — duration of burn 1 (s)
 *   <li>[2] alpha1 — in-plane thrust angle in TNW frame (rad)
 *   <li>[3] beta1 — out-of-plane thrust angle in TNW frame (rad)
 * </ul>
 */
public class TransferTwoManeuverProblem extends TransferProblem {
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /**
   * Creates a two-burn transfer optimization problem targeting a circular orbit at the given
   * altitude.
   *
   * @param maneuver the two-burn transfer maneuver that handles propagation
   * @param initialState the spacecraft state at the start of the transfer
   * @param targetAltitude target circular orbit altitude in meters above the Earth's surface
   * @param propulsionSystem the propulsion system used for the transfer burns
   * @param vehicleMinMass minimum allowable vehicle mass after burns (dry mass)
   * @param targetInclination target orbital plane inclination in radians
   */
  public TransferTwoManeuverProblem(
      TransfertTwoManeuver maneuver,
      SpacecraftState initialState,
      double targetAltitude,
      PropulsionSystem propulsionSystem,
      double vehicleMinMass,
      double targetInclination) {
    super(
        maneuver,
        initialState,
        targetAltitude,
        targetAltitude,
        propulsionSystem,
        vehicleMinMass,
        targetInclination);
  }

  @Override
  public TransfertTwoManeuver getManeuver() {
    return (TransfertTwoManeuver) maneuver;
  }

  // ════════════════════════════════════════════════════════════════════════
  // Post-mortem diagnostics — see specs/optimizer/03-robustness-roadmap.md §0.1
  // ════════════════════════════════════════════════════════════════════════

  /**
   * Δv decomposition for the two-burn transfer.
   *
   * @param dvBurn1Total the total Δv delivered by burn 1 (Tsiolkovsky), m/s
   * @param dvBurn1Useful the in-plane prograde projection (cos α · cos β), m/s
   * @param dvBurn1Wasted the residual ({@code total − useful}), m/s
   * @param dvBurn2 the deterministic burn-2 circularization Δv, m/s, or {@code NaN} if burn 2 could
   *     not be resolved from the optimal parameters
   */
  public record DvBreakdown(
      double dvBurn1Total, double dvBurn1Useful, double dvBurn1Wasted, double dvBurn2) {}

  /**
   * Computes the Δv breakdown for the optimal solution.
   *
   * <p>Burn 1 total uses the rocket equation with the propulsion characteristics of the stage
   * active at transfer entry. The useful projection is {@code cos α · cos β} of that scalar Δv. The
   * circularization burn is resolved deterministically via {@link
   * TransfertTwoManeuver#resolveCircularizationBurnFromInitial}.
   *
   * @param bestVariables the optimized 4-element parameter vector
   * @return the Δv breakdown
   */
  public DvBreakdown computeDvBreakdown(double[] bestVariables) {
    TransfertTwoManeuver twoManeuver = getManeuver();
    TransferManeuver.Burn1Params params = twoManeuver.decode(bestVariables);

    double m0 = initialState.getMass();
    double massFlow = thrust / (isp * Constants.G0_STANDARD_GRAVITY);
    double mAfter = FastMath.max(m0 - massFlow * params.dt1(), 1.0);
    double dv1Total = isp * Constants.G0_STANDARD_GRAVITY * FastMath.log(m0 / mAfter);
    double useful = dv1Total * FastMath.cos(params.alpha1()) * FastMath.cos(params.beta1());
    double wasted = dv1Total - useful;

    TransfertTwoManeuver.ResolvedCircularizationBurn circBurn =
        twoManeuver.resolveCircularizationBurnFromInitial(initialState, params);
    double dv2 = circBurn != null ? circBurn.dvNeeded() : Double.NaN;

    return new DvBreakdown(dv1Total, useful, wasted, dv2);
  }

  /**
   * Per-barrier diagnostic for the optimal solution.
   *
   * @param periapsisFloor true if the post-burn-2 periapsis is at or below the adaptive periapsis
   *     floor
   * @param altMin true if the in-flight altitude tracker recorded a value at or below {@code
   *     ALT_MIN}
   * @param altMax true if the in-flight altitude tracker exceeded {@code 1.05 · target}
   * @param periapsisContribution the {@code W_BARRIER · barrierBelow(periapsis, periapsisFloor)}
   *     term
   * @param altMinContribution the {@code W_BARRIER · barrierBelow(minAlt, ALT_MIN)} term
   * @param altMaxContribution the {@code W_ALT_MAX · ((excess)/altMax)²} term
   */
  public record BarrierReport(
      boolean periapsisFloor,
      boolean altMin,
      boolean altMax,
      double periapsisContribution,
      double altMinContribution,
      double altMaxContribution) {}

  /**
   * Re-runs propagation for the optimal parameters and isolates each barrier contribution to the
   * cost. Pure, no side effects beyond updating {@code lastResult}.
   *
   * @param bestVariables the optimized parameter vector
   * @return the per-barrier report
   */
  public BarrierReport diagnoseBarriers(double[] bestVariables) {
    SpacecraftState state = propagate(bestVariables);
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      return new BarrierReport(false, false, false, 0.0, 0.0, 0.0);
    }
    KeplerianOrbit finalOrbit = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(state.getOrbit());
    double periapsisAlt = finalOrbit.getA() * (1.0 - finalOrbit.getE()) - EARTH_RADIUS;

    double periBarrier = barrierBelow(periapsisAlt, periapsisFloor);
    boolean periHit = periapsisAlt <= periapsisFloor;

    double altMinBarrier = 0.0;
    double altMaxPenalty = 0.0;
    boolean altMinHit = false;
    boolean altMaxHit = false;
    TransferResult tr = lastResult.get();
    MinAltitudeTracker tracker = tr != null ? tr.altitudeTracker() : null;
    if (tracker != null) {
      altMinBarrier = barrierBelow(tracker.getMinAltitude(), 80_000);
      altMinHit = tracker.getMinAltitude() <= 80_000;
      if (tracker.getMaxAltitude() > altMax) {
        double excess = (tracker.getMaxAltitude() - altMax) / altMax;
        altMaxPenalty = excess * excess;
        altMaxHit = true;
      }
    }
    return new BarrierReport(
        periHit,
        altMinHit,
        altMaxHit,
        W_BARRIER * periBarrier,
        W_BARRIER * altMinBarrier,
        W_ALT_MAX * altMaxPenalty);
  }
}
