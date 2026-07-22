package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import static com.smousseur.orbitlab.simulation.Physics.sq;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Trajectory optimization problem for the gravity turn phase of an ascent mission.
 *
 * <p>Optimizes two variables:
 *
 * <ul>
 *   <li><b>transitionTime</b> -- time at which the gravity turn ends (MECO)
 *   <li><b>exponent</b> -- pitch program exponent controlling the gravity turn profile
 * </ul>
 *
 * <p>The cost function penalizes deviations from the target apogee window, excessive flight path
 * angle, insufficient tangential velocity, unsafe altitudes, hyperbolic orbits, and propellant
 * consumption.
 */
public class GravityTurnProblem implements TrajectoryProblem {
  private static final double W_P = 9.e-5;
  // Soft target toward FPA=0° inside the admissible window. Without a
  // gradient inside [fpaMin, fpaMax] CMA-ES has no preference and may
  // settle on the lower bound (e.g. -0.5° at 200 km targets), handing off
  // a descending state that slows the downstream transfer phase by 2-5×.
  private static final double W_FPA_SOFT = 25.0;

  // Acceptance threshold (bilan 08 §3.6). The W_FPA_SOFT·fpa² term is a tie-breaker toward a level
  // hand-off, not a constraint to drive to zero: at the reference FH mission the profile hands off
  // at fpa ≈ 2.1° while holding the apogee window, leaving an irreducible W_FPA_SOFT·(2.1°)² ≈
  // 0.034
  // that no GT solution can remove. Accept above that floor — sized at the FPA-soft cost of a 2.5°
  // hand-off — so the GT concludes on the first exploration instead of exhausting retries (and
  // logging a WARN) against a structural minimum. A positive residual FPA here is benign: the
  // CMA-ES
  // transfer (spec 06 I6) absorbs it downstream. Derived from W_FPA_SOFT so it tracks a future
  // recalibration of that weight. If a mission ever hands off above 2.5°, the WARN returns — a
  // genuine anomaly worth seeing, not noise.
  //
  // Step 2 (make W_FPA_SOFT asymmetric — penalize only a descending fpa<0 hand-off) was built and
  // sweep-tested, then reverted: dropping the pull toward a level hand-off let the GT hand off
  // less level (FPA 0.23°→0.46° at 600 km), degrading final-orbit circularity ~6× (ecc
  // 1.4e-4→8.5e-4) even on the CMA-ES-optimized transfer — inside the ±7% test margin, but a real
  // regression. The symmetric pull earns its keep as a level-hand-off tie-breaker; the floor it
  // leaves is handled here by accepting above it, not by removing the pull.
  private static final double MAX_EXPECTED_HANDOFF_FPA_RAD = FastMath.toRadians(2.5);
  private static final double ACCEPTABLE_COST = W_FPA_SOFT * sq(MAX_EXPECTED_HANDOFF_FPA_RAD);

  // ── Staging invariant (bilan 10 §5.3) ──
  // The jettison of the first stage is scheduled inside the maneuver by a DateDetector at
  // burn1Duration, so a MECO before that ends the propagation before the detector fires: burn 1 is
  // truncated, the stage is never dropped, and it stays active for every downstream phase — on the
  // GEO profile a 0.4 s shortfall against a 150 s burn 1 stranded 3.3 t in S1 and let the "S2
  // separation" jettison S1 in its place. Such a candidate can score perfectly well on the
  // criteria below (that run cost 0.0089), so the penalty has to dominate any nominal cost
  // outright rather than merely nudge.
  //
  // This is deliberately a cost term and not a search bound: CMA-ES is rank-based and these
  // candidates already sit in the discarded half of every generation (a truncated ascent trips the
  // velocity and apogee guard rails), so penalizing them leaves the search path untouched — where
  // raising the lower bound would rescale the box and perturb every mission.
  private static final double STAGING_PENALTY_BASE = 1e3;

  /** Gradient per second of shortfall, pushing CMA-ES back above the staging floor. */
  private static final double W_STAGING_SHORTFALL = 1.0;

  private final GravityTurnManeuver maneuver;
  private final SpacecraftState initialState;
  private final GravityTurnConstraints constraints;

  // How far the candidate's MECO falls short of staging completion (s), handed from propagate() to
  // computeCost(). Per-thread so parallel CMA-ES exploration runs cannot overwrite each other's
  // value, matching GravityTurnManeuver#lastAltitudeTracker.
  private final ThreadLocal<Double> stagingShortfall = ThreadLocal.withInitial(() -> 0.0);

  /**
   * Creates a gravity turn optimization problem.
   *
   * @param maneuver the gravity turn maneuver that handles propagation
   * @param initialState the spacecraft state at the beginning of the gravity turn
   * @param constraints the target apogee, velocity, and flight path angle constraints
   */
  public GravityTurnProblem(
      GravityTurnManeuver maneuver,
      SpacecraftState initialState,
      GravityTurnConstraints constraints) {
    this.maneuver = maneuver;
    this.initialState = initialState;
    this.constraints = constraints;
  }

  @Override
  public int getNumVariables() {
    return 2;
  }

  @Override
  public double[] buildInitialGuess() {
    double burn1Duration = maneuver.getBurn1Duration();
    return new double[] {burn1Duration + 20.0, 1.0};
  }

  @Override
  public double[] getLowerBounds() {
    // The staging invariant is enforced as a cost penalty, NOT as a bound: Hipparchus normalizes
    // the search space by the box width, so moving this floor would re-encode every candidate and
    // rescale the effective sigma, perturbing the search on missions the invariant never binds
    // (measured: a LEO 300 km hand-off degrading to 290×311 km). See computeCost.
    return new double[] {30.0, 0.1};
  }

  @Override
  public double[] getUpperBounds() {
    // Floor scales linearly between 550 s (≤250 km) and 500 s (500 km), then
    // returns to 450 s above. Combined with the tighter vTan ratio at low and
    // medium altitudes, this gives CMA-ES enough time to accumulate the
    // tangential velocity required by the new constraint.
    double altKm = constraints.targetAltitude() / 1000.0;
    double lowAltFloor;
    if (altKm <= 250.0) lowAltFloor = 550.0;
    else if (altKm <= 500.0) lowAltFloor = 550.0 + (500.0 - 550.0) * (altKm - 250.0) / 250.0;
    else lowAltFloor = 450.0;
    double transitionTimeMax =
        FastMath.max(lowAltFloor, 300.0 + 0.3 * FastMath.sqrt(constraints.targetAltitude()));
    return new double[] {transitionTimeMax, 3.0};
  }

  @Override
  public double[] getInitialSigma() {
    return new double[] {30.0, 0.3};
  }

  @Override
  public double getAcceptableCost() {
    return ACCEPTABLE_COST;
  }

  @Override
  public SpacecraftState propagate(double[] variables) {
    // Recorded for the computeCost() call the executor makes right after, on this same thread.
    stagingShortfall.set(FastMath.max(0.0, maneuver.getStagingCompleteTime() - variables[0]));
    return maneuver.propagateForOptimization(initialState, variables);
  }

  @Override
  public double computeCost(SpacecraftState state) {
    double shortfall = stagingShortfall.get();
    double stagingPenalty =
        shortfall > 0 ? STAGING_PENALTY_BASE + W_STAGING_SHORTFALL * shortfall : 0.0;
    return trajectoryCost(state) + stagingPenalty;
  }

  /** Cost of the hand-off state itself, before the staging invariant is applied. */
  private double trajectoryCost(SpacecraftState state) {
    // Detect penalty states: if propagation failed, the returned state is the initial state
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      // Graded penalty: still high enough to dominate any nominal cost (<100),
      // but proportional to how far underground the trajectory dipped, so CMA-ES
      // gets a usable gradient instead of a flat 1e6 wall.
      MinAltitudeTracker tracker = maneuver.getLastAltitudeTracker();
      if (tracker != null && tracker.getMinAltitude() != Double.MAX_VALUE) {
        double underground = FastMath.max(0.0, -tracker.getMinAltitude());
        return 1e3 + underground / 1000.0;
      }
      return 1e6;
    }

    PVCoordinates pv = state.getPVCoordinates();
    Vector3D pos = pv.getPosition();
    Vector3D vel = pv.getVelocity();

    double alt = pos.getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    double vNorm = vel.getNorm();

    Vector3D zenith = pos.normalize();
    double vRadial = Vector3D.dotProduct(vel, zenith);
    double vTangential = FastMath.sqrt(vNorm * vNorm - vRadial * vRadial);

    KeplerianOrbit orb =
        new KeplerianOrbit(pv, state.getFrame(), state.getDate(), Constants.WGS84_EARTH_MU);
    double ecc = orb.getE();
    double apogee = orb.getA() * (1.0 + ecc) - WGS84_EARTH_EQUATORIAL_RADIUS;
    double periapsis = orb.getA() * (1.0 - ecc) - WGS84_EARTH_EQUATORIAL_RADIUS;

    double flightPathAngle = FastMath.atan2(vRadial, vTangential);

    double cost = 0.0;

    // 2. Apogee window — this is the key for staging
    if (apogee < constraints.targetApogee()) {
      cost += 8.0 * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
    } else if (apogee > constraints.maxApogee()) {
      cost += 3.0 * sq((apogee - constraints.maxApogee()) / constraints.targetApogee());
    }

    // 3. Flight path angle — penalize outside the [fpaMin, fpaMax] window
    double fpaMin = Math.toRadians(constraints.targetFlightPathAngleMinDeg());
    double fpaMax = Math.toRadians(constraints.targetFlightPathAngleMaxDeg());
    if (flightPathAngle < fpaMin) {
      cost += 2.0 * sq(fpaMin - flightPathAngle);
    } else if (flightPathAngle > fpaMax) {
      cost += 2.0 * sq(flightPathAngle - fpaMax);
    }
    // Soft pull toward FPA=0° (ideal Hohmann hand-off). Provides a gradient
    // inside the admissible window so CMA-ES does not stochastically settle
    // on edge solutions like FPA=-0.5° that slow transfer convergence.
    cost += W_FPA_SOFT * sq(flightPathAngle);

    // 4. Tangential velocity — must be high enough for orbit insertion
    double minVtan = constraints.minTangentialVelocity();
    if (vTangential < minVtan) {
      cost += 5.0 * sq((minVtan - vTangential) / minVtan);
    }

    // 5. Smooth guard rails
    if (alt < 30_000) cost += 100.0 * sq((30_000 - alt) / 30_000);
    if (ecc > 1.0) cost += 100.0 * sq(ecc - 1.0);
    if (apogee < 100_000) cost += 50.0 * sq((100_000 - apogee) / 100_000);
    if (vNorm < 2000) cost += 100.0 * sq((2000 - vNorm) / 2000);

    // 6. Periapsis safety: a GT exit with periapsis far below ground gives the
    // transfer phase a near-impossible starting point (Earth-piercing orbit).
    // Penalize trajectories whose orbital periapsis falls more than 200 km
    // below sea level so CMA-ES is pushed towards a near-orbital hand-off.
    double periFloor = -200_000.0;
    if (periapsis < periFloor) {
      cost += 30.0 * sq((periFloor - periapsis) / 200_000.0);
    }

    cost += W_P * (initialState.getMass() - state.getMass()) / initialState.getMass();

    return cost;
  }
}
