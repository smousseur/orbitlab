package com.smousseur.orbitlab.simulation.mission.optimizer.problems;

import static com.smousseur.orbitlab.simulation.Physics.sq;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentPropagation;
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

  // ── Staging floor penalty: no longer a guard, now a search regularizer ────
  //
  // ORIGINALLY (bilan 10 §5.3) this guarded a real failure mode. The jettison was a DateDetector
  // planted inside the ascent, so a MECO scheduled before it ended the propagation before it fired:
  // burn 1 truncated, the first stage never dropped, and it stayed active for every downstream
  // phase. On the GEO profile a 0.4 s shortfall against a 150 s burn 1 stranded 3.3 t in S1 and let
  // the "S2 separation" jettison S1 in its place — while scoring 0.0089 on the criteria below,
  // which is why the penalty had to dominate outright rather than merely nudge.
  //
  // THAT FAILURE MODE IS GONE. The jettison is a phase of its own ("S1 separation"), so it happens
  // whatever the MECO; a transition time below the floor now just yields a zero-length second burn
  // (spec docs/mission-stages/01-separations-implicites.md §6).
  //
  // THE PENALTY IS KEPT ANYWAY, for the reason the measurement gave when it was removed and the
  // optimization tests re-run (étape 5, see 02-baseline-n2.md §12). Below the floor, transitionTime
  // stops controlling anything — every candidate there flies the same ascent and ends at the same
  // jettison coast — so the region is a plateau of equally mediocre solutions. Without the cliff,
  // CMA-ES spends real budget exploring it: on the Falcon Heavy LEO profile, +47 % evaluations and
  // +57 % wall clock, and the retained solution stopped being reproducible at the fixed seed
  // (transitionTime varying ±0.007 s run to run, because the cross-run early stop then depends on
  // thread scheduling). The final orbit was identical either way. So this is no longer a guard
  // rail — it is what keeps the search out of a degenerate flat region, and it earns its keep on
  // that ground alone.
  //
  // It remains a cost term rather than a search bound on the original grounds: Hipparchus
  // normalizes the search space by the box width, so raising the lower bound would re-encode every
  // candidate and rescale the effective sigma, perturbing missions the floor never binds.
  private static final double STAGING_PENALTY_BASE = 1e3;

  /**
   * Weight of an apogee <b>above</b> the admissible window, against {@link #W_APOGEE_SHORTFALL} =
   * 8.0 below it. The asymmetry is the point: the two errors do not cost the mission the same
   * thing.
   *
   * <p>An apogee short of the window must be made up by the transfer in real ΔV. An apogee past it
   * is absorbed by the trim burn at the next apside for nothing measurable — on the Falcon Heavy
   * 400 km profile, hand-offs 91 km apart in apogee both reached a final apogee of 400.128 km.
   *
   * <p><b>Why it was lowered from 3.0 (measured 2026-08-05).</b> When the first stage burns to
   * flame-out and over-delivers, the gravity turn cannot lower its apogee by thrusting less — its
   * only lever is to pitch up, trading tangential velocity for radial. That trade is a false
   * economy: the radial velocity is cancelled outright by the transfer's first burn, which the cost
   * function barely sees. At 3.0 the ceiling term outbid {@link #W_FPA_SOFT} and bought the trade.
   * Two candidates of the same mission, same loads, same MECO, differing only in pitch exponent:
   *
   * <pre>
   *   exponent 0.354465 → apogee 490 969 m, vRad 284.5 m/s → cost 0.225404 → final 400 000×400 110 m
   *   exponent 0.386137 → apogee 455 066 m, vRad 387.4 m/s → cost 0.130867 → final 251 397×400 128 m
   * </pre>
   *
   * The cost function preferred the candidate that emptied the upper stage 148 km short of target.
   * The apogee term accounted for 0.12259 of the 0.094537 gap, the FPA term for 0.02807 against it;
   * the ranking flips below a weight of 0.687. 0.5 is that threshold with margin, and it leaves the
   * ceiling doing its real job — discouraging an overshoot the vehicle <em>can</em> avoid — without
   * letting it pay for one it cannot.
   */
  private static final double W_APOGEE_OVERSHOOT = 0.5;

  /** Weight of an apogee short of the target window, which the transfer must make up in ΔV. */
  private static final double W_APOGEE_SHORTFALL = 8.0;

  /** Gradient per second of shortfall, pushing CMA-ES back above the staging floor. */
  private static final double W_STAGING_SHORTFALL = 1.0;

  private final GravityTurnManeuver maneuver;
  private final SpacecraftState initialState;
  private final GravityTurnConstraints constraints;
  private final AscentPropagation propagation;

  // How far the candidate's MECO falls short of staging completion (s), handed from propagate() to
  // computeCost(). Per-thread so parallel CMA-ES exploration runs cannot overwrite each other's
  // value, matching GravityTurnManeuver#lastAltitudeTracker.
  private final ThreadLocal<Double> stagingShortfall = ThreadLocal.withInitial(() -> 0.0);

  /**
   * Creates a gravity turn optimization problem flying candidates on the historical single
   * propagator (burn 1, jettison detector and burn 2 on one integration).
   *
   * @param maneuver the gravity turn maneuver that handles propagation
   * @param initialState the spacecraft state at the beginning of the gravity turn
   * @param constraints the target apogee, velocity, and flight path angle constraints
   */
  public GravityTurnProblem(
      GravityTurnManeuver maneuver,
      SpacecraftState initialState,
      GravityTurnConstraints constraints) {
    this(maneuver, initialState, constraints, maneuver.asPropagation());
  }

  /**
   * Creates a gravity turn optimization problem with an explicit way of flying a candidate — the
   * three explicit ascent phases, once the mission is built on {@code AscentSequence} (spec {@code
   * docs/mission-stages/01-separations-implicites.md} §5.4). The cost function is unchanged either
   * way: only the propagation differs.
   *
   * @param maneuver the gravity turn maneuver decoding the variables (burn 1 duration, staging)
   * @param initialState the spacecraft state at the beginning of the gravity turn
   * @param constraints the target apogee, velocity, and flight path angle constraints
   * @param propagation how a candidate is flown from gravity-turn entry to MECO
   */
  public GravityTurnProblem(
      GravityTurnManeuver maneuver,
      SpacecraftState initialState,
      GravityTurnConstraints constraints,
      AscentPropagation propagation) {
    this.maneuver = maneuver;
    this.initialState = initialState;
    this.constraints = constraints;
    this.propagation = propagation;
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
    return propagation.propagate(initialState, variables);
  }

  @Override
  public double computeCost(SpacecraftState state) {
    double shortfall = stagingShortfall.get();
    double stagingPenalty =
        shortfall > 0 ? STAGING_PENALTY_BASE + W_STAGING_SHORTFALL * shortfall : 0.0;
    return trajectoryCost(state) + stagingPenalty;
  }

  /** Cost of the hand-off state itself, before the staging floor penalty is applied. */
  private double trajectoryCost(SpacecraftState state) {
    // Detect penalty states: if propagation failed, the returned state is the initial state
    double elapsed = state.getDate().durationFrom(initialState.getDate());
    if (elapsed < 1.0) {
      // Graded penalty: still high enough to dominate any nominal cost (<100),
      // but proportional to how far underground the trajectory dipped, so CMA-ES
      // gets a usable gradient instead of a flat 1e6 wall.
      MinAltitudeTracker tracker = propagation.lastAltitudeTracker();
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

    // Earth-fixed on purpose (PHY-4 / L1, spec docs/multi-corps/03-conception-L1.md §4.1):
    // multi-arc optimization is out of PHY-4 (docs/multi-corps/01-decoupage.md §1). This µ moves
    // when a CMA-ES cost function has to grade a candidate that crosses an SOI switch.
    KeplerianOrbit orb =
        new KeplerianOrbit(pv, state.getFrame(), state.getDate(), Constants.WGS84_EARTH_MU);
    double ecc = orb.getE();
    double apogee = orb.getA() * (1.0 + ecc) - WGS84_EARTH_EQUATORIAL_RADIUS;
    double periapsis = orb.getA() * (1.0 - ecc) - WGS84_EARTH_EQUATORIAL_RADIUS;

    double flightPathAngle = FastMath.atan2(vRadial, vTangential);

    double cost = 0.0;

    // 2. Apogee window — this is the key for staging. Asymmetric on purpose: see
    // W_APOGEE_OVERSHOOT.
    if (apogee < constraints.targetApogee()) {
      cost +=
          W_APOGEE_SHORTFALL
              * sq((constraints.targetApogee() - apogee) / constraints.targetApogee());
    } else if (apogee > constraints.maxApogee()) {
      cost +=
          W_APOGEE_OVERSHOOT * sq((apogee - constraints.maxApogee()) / constraints.targetApogee());
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
