package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Deterministic two-burn Hohmann transfer from a near-circular parking orbit to a target orbit
 * defined by perigee, apogee and inclination. The circular case is recovered when {@code
 * targetPerigeeAltitude == targetApogeeAltitude}. No optimization variables — all burn parameters
 * are computed in closed form from the entry state and the target.
 *
 * <p>Geometry assumption: the entry state lies at an equatorial node of the parking orbit. This is
 * normally guaranteed by chaining this stage after a {@link CoastingStage} configured with {@code
 * stopAtNode = true}. Under this assumption:
 *
 * <ul>
 *   <li>Burn 1 is a pure prograde Hohmann burn; the perigee of the transfer ellipse sits at the
 *       node and matches the target-orbit perigee.
 *   <li>The transfer ellipse retains the parking-orbit plane; its apogee sits at the antipodal
 *       node, also on the equator.
 *   <li>Burn 2 is a single vector burn at apogee that sets the velocity to the elliptical
 *       target-orbit apogee velocity (collapses to the circularization velocity when {@code rp ==
 *       ra}) and performs the plane change to the target inclination, in inertial-frame components
 *       projected onto the spacecraft TNW frame.
 * </ul>
 *
 * <p>The stage does not implement {@link
 * com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage}; the mission optimizer
 * propagates it via {@link #propagateStandalone(SpacecraftState, Mission)} without running CMA-ES.
 *
 * <p>Accepted approximations (v2):
 *
 * <ul>
 *   <li>Burn 1's tangential ΔV magnitude is computed from the impulsive Hohmann formula. The full
 *       ΔV vector cancels any pre-burn radial velocity and adds the tangential boost, so the
 *       post-burn velocity is purely tangential. This is essential when the parking orbit has a
 *       non-zero eccentricity at the node (a residual of the analytic parking insertion under J2).
 *   <li>Burn 2's epoch, position and velocity are computed by propagating the post-burn-1 state
 *       under the full 8×8 gravity model up to the next apogee (detected via {@link
 *       ApsideDetector}). This captures J2/J3+ effects exactly rather than approximating them with
 *       Brouwer secular formulae.
 *   <li>Both burns use {@link FrameAlignedProvider} with a constant inertial direction equal to the
 *       impulsive ΔV direction, so the integrated finite burn matches the impulsive equivalent
 *       (within Tsiolkovsky-rectangle quadrature error).
 *   <li>Active-stage propulsion is resolved twice (at burn 1 entry mass and at the post-burn-1 mass
 *       estimated by Tsiolkovsky), so a stage transition between burns is handled correctly.
 * </ul>
 */
public class AnalyticHohmannTransferStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticHohmannTransferStage.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /** How far above its target an apogee must sit before the descending branch is worth firing. */
  private static final double DESCENT_THRESHOLD_M = 5_000.0;

  /** Convergence threshold on the perigee the insertion branch aims at (m). */
  private static final double INSERTION_PERIGEE_TOLERANCE_M = 2_000.0;

  /** Durations tried across the search span before the rising branch is refined. */
  private static final int INSERTION_SCAN_POINTS = 14;

  /** Bisection steps refining the rising branch once the target is bracketed. */
  private static final int INSERTION_REFINE_STEPS = 12;

  /** How far past the impulsive circularization the scan looks, in multiples of it. */
  private static final double INSERTION_SPAN_FACTOR = 2.5;

  private final double targetPerigeeAltitude;
  private final double targetApogeeAltitude;
  private final double targetInclination;

  /**
   * @param name human-readable stage name
   * @param targetPerigeeAltitude target perigee altitude (m, above the Earth surface); equals
   *     {@code targetApogeeAltitude} for a circular orbit
   * @param targetApogeeAltitude target apogee altitude (m, above the Earth surface)
   * @param targetInclination target orbital plane inclination (rad); 0 for an equatorial GEO
   */
  public AnalyticHohmannTransferStage(
      String name,
      double targetPerigeeAltitude,
      double targetApogeeAltitude,
      double targetInclination) {
    super(name);
    this.targetPerigeeAltitude = targetPerigeeAltitude;
    this.targetApogeeAltitude = targetApogeeAltitude;
    this.targetInclination = targetInclination;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    AnalyticBurnPlan plan =
        computeBurnPlan(state, mission.getVehicle(), flightContext(state, mission));

    addBurns(propagator, state, plan, mission.getVehicle());

    AbsoluteDate endDate = state.getDate().shiftedBy(plan.totalDuration());
    this.configuredEndDate = endDate;
    propagator.addEventDetector(
        new DateDetector(endDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    return burnLimitedMaxStep(entryState, mission.getVehicle());
  }

  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    FlightContext context = flightContext(currentState, mission);
    AnalyticBurnPlan plan = computeBurnPlan(currentState, mission.getVehicle(), context);

    // 8×8 gravity, matching the ephemeris generator (bilan 11 §3.9): this standalone flight
    // advances
    // the state the next stage plans from, so a Newtonian point-mass field here would diverge from
    // the flown 8×8 trajectory.
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(
                context, burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    ReentryGuard.armQuiet(propagator, context.gravity());
    addBurns(propagator, currentState, plan, mission.getVehicle());

    return propagator.propagate(currentState.getDate().shiftedBy(plan.totalDuration()));
  }

  // ════════════════════════════════════════════════════════════════════════
  // Analytical computation
  // ════════════════════════════════════════════════════════════════════════

  private record AnalyticBurnPlan(
      double dtInsertion,
      Vector3D insertionDirectionInertial,
      double dt1,
      Vector3D burn1DirectionInertial,
      double dtCoast,
      double dt2,
      Vector3D burn2DirectionInertial,
      double totalDuration,
      double dv1,
      double dv2) {}

  private AnalyticBurnPlan computeBurnPlan(
      SpacecraftState state, Vehicle vehicle, FlightContext context) {
    return computeBurnPlan(state, vehicle, context, true);
  }

  /**
   * @param mayInsert whether an unreachable apogee may be answered by inserting first; false on the
   *     re-plan that follows an insertion, so a state that is still unplannable fails loudly rather
   *     than looping
   */
  private AnalyticBurnPlan computeBurnPlan(
      SpacecraftState state, Vehicle vehicle, FlightContext context, boolean mayInsert) {
    AnalyticBurnPlan descending = computeDescendingPlan(state, vehicle, context);
    if (descending != null) {
      return descending;
    }
    double mu = state.getOrbit().getMu();

    Vector3D r1 = state.getPVCoordinates().getPosition();
    Vector3D v1 = state.getPVCoordinates().getVelocity();
    double r1Mag = r1.getNorm();
    Vector3D rHat = r1.normalize();
    double vRadial1 = v1.dotProduct(rHat);
    Vector3D vTangential1 = v1.subtract(rHat.scalarMultiply(vRadial1));

    // ── Newton iteration on r2Aim to make the simulated post-burn-1 apogee = r2_target ──
    // Finite burn 1 loses ~200 km of effective tangential ΔV to steering (thrust direction
    // fixed inertial while the local tangential direction rotates ~3° during the burn) plus
    // gravity-loss on the small radial component used to cancel parking-orbit eccentricity at
    // the node. Newton on r2Aim converges in 1-2 iterations to drive the apogee under finite
    // burn dynamics to r2_target. The trim stage downstream zeros the residual eccentricity
    // and the small finite-burn-2 plane-change miss.
    double rPerigeeTarget = EARTH_RADIUS + targetPerigeeAltitude;
    double r2 = EARTH_RADIUS + targetApogeeAltitude;
    Vector3D tHat = vTangential1.normalize();
    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    double g0Ve = propulsion1.isp() * Constants.G0_STANDARD_GRAVITY;
    double maxStep = burnLimitedMaxStep(state, vehicle);

    double r2Aim = r2;
    double dv1 = 0.0;
    double dt1 = 0.0;
    Vector3D deltaV1 = Vector3D.ZERO;
    Vector3D vAfterBurn1;
    SpacecraftState stateAtApogee = null;
    for (int iter = 0; iter < 4; iter++) {
      double aTransfer = (r1Mag + r2Aim) / 2.0;
      double vTransferPerigee = FastMath.sqrt(mu * (2.0 / r1Mag - 1.0 / aTransfer));
      vAfterBurn1 = tHat.scalarMultiply(vTransferPerigee);
      deltaV1 = vAfterBurn1.subtract(v1);
      dv1 = deltaV1.getNorm();
      dt1 =
          Physics.computeBurnDurationCapped(
              dv1,
              state.getMass(),
              propulsion1.isp(),
              propulsion1.thrust(),
              stage1.remainingFuel(state.getMass()));
      double transferHalfPeriod =
          FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);
      stateAtApogee =
          simulateBurn1AndFindApogee(
              context,
              state,
              deltaV1.normalize(),
              dt1,
              propulsion1.thrust(),
              propulsion1.isp(),
              transferHalfPeriod,
              maxStep);
      if (stateAtApogee == null) {
        break;
      }
      double rApoActual = stateAtApogee.getPVCoordinates().getPosition().getNorm();
      double bias = r2 - rApoActual;
      r2Aim += bias;
      if (FastMath.abs(bias) < 100.0) {
        break;
      }
    }
    if (stateAtApogee == null) {
      if (!mayInsert) {
        throw new IllegalStateException("No apogee found within one transfer half-period.");
      }
      return insertThenTransfer(state, vehicle, context);
    }

    Vector3D burn1DirectionInertial = deltaV1.normalize();
    double massAfterBurn1 = state.getMass() * FastMath.exp(-dv1 / g0Ve);

    Vector3D rApo = stateAtApogee.getPVCoordinates().getPosition();
    Vector3D vCurrentApo = stateAtApogee.getPVCoordinates().getVelocity();

    // ── Target velocity at apogee: elliptical (rp, r2_target) in target plane ──
    // After the Newton iteration r2Aim is such that simulated |rApo| equals r2_target to within
    // ~100 m, so targeting the elliptical apogee velocity at r2_target (rather than |rApo|) is
    // essentially equivalent and removes the residual O(100 m) misalignment. When the target
    // orbit is circular (rp == r2_target) this collapses to the circularization velocity.
    double rApoMag = rApo.getNorm();
    Vector3D vTargetApo =
        computeTargetVelocityAtApogee(rApo, vCurrentApo, mu, rPerigeeTarget, r2, targetInclination);

    Vector3D deltaV2 = vTargetApo.subtract(vCurrentApo);
    double dv2 = deltaV2.getNorm();

    // Both burns are flown by the SAME stage — this phase contains no jettison, and only a
    // jettison can change the active one (invariant on VehicleStack#resolveActiveStage). Only the
    // mass moves between them.
    double dt2 =
        Physics.computeBurnDurationCapped(
            dv2,
            massAfterBurn1,
            propulsion1.isp(),
            propulsion1.thrust(),
            stage1.remainingFuel(massAfterBurn1));
    Vector3D burn2DirectionInertial = deltaV2.normalize();

    // Center burn 2 on the actual apogee. The simulation starts burn 1 at state.date (matching
    // the mission's epoch+1ms start) and propagates through the finite burn to the detected
    // apogee at state.date + dtToApogee. In the mission timeline burn 2 starts at state.date +
    // dt1 + dtCoast; centring on apogee gives dt1 + dtCoast + dt2/2 = dtToApogee, hence
    // dtCoast = dtToApogee − dt1 − dt2/2.
    double dtToApogee = stateAtApogee.getDate().durationFrom(state.getDate());
    double dtCoast = FastMath.max(0.0, dtToApogee - dt1 - dt2 / 2.0);
    double totalDuration = dt1 + dtCoast + dt2;

    logger.info(
        "Analytic Hohmann plan: dv1={} m/s, dt1={}s, dtCoast={}s, dv2={} m/s, dt2={}s,"
            + " apogee alt={} km, r2Aim={} km",
        dv1,
        dt1,
        dtCoast,
        dv2,
        dt2,
        (rApoMag - EARTH_RADIUS) / 1000.0,
        (r2Aim - EARTH_RADIUS) / 1000.0);
    logger.info(
        "Transfer entry: r1={} km (alt={} km), vRadial1={} m/s",
        r1Mag / 1000.0,
        (r1Mag - EARTH_RADIUS) / 1000.0,
        vRadial1);

    return new AnalyticBurnPlan(
        0.0,
        null,
        dt1,
        burn1DirectionInertial,
        dtCoast,
        dt2,
        burn2DirectionInertial,
        totalDuration,
        dv1,
        dv2);
  }

  /**
   * <b>PHY-8 / L4 — the branch taken when there is no apogee to aim at</b> (spec {@code
   * docs/etagement/06-conception-L4.md} §3.3).
   *
   * <p>An ascent can hand over on an arc so deep that raising its apogee does not produce one ahead
   * — the vehicle re-enters first. A split Ariane 64 does exactly that: apogee 372 km, perigee −3
   * 536 km, and 3 247 m/s still aboard. Before this branch the stage threw, which is why {@code
   * CentralBodyBaselineTest}'s polar profile has been frozen on an ascent-only chain since BUG-6.
   *
   * <p>The answer is one prograde burn that lifts the perigee to where the vehicle already is, then
   * the ordinary plan from the resulting orbit — which now has an apogee, by construction.
   *
   * <p><b>Why this lives on the throw path and nowhere else.</b> Measured: applying the same
   * insertion to a profile that did <em>not</em> need it costs 11 452 kg of final mass on the
   * Falcon Heavy LEO-400 baseline, 31 %. That ascent hands over at 77 km but with its apogee
   * already at 420 km, and circularizing at 77 km throws that apogee away — {@code dv1} goes from
   * 199 to 638 m/s. Reaching an apogee the ascent already paid for is what this stage is good at;
   * the branch must not take that away (spec §3.4).
   */
  private AnalyticBurnPlan insertThenTransfer(
      SpacecraftState state, Vehicle vehicle, FlightContext context) {
    Vector3D direction = insertionDirection(state);
    double dtInsertion = resolveInsertion(state, vehicle, context, direction);
    SpacecraftState inserted = flyInsertion(state, dtInsertion, direction, vehicle, context);
    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(state.getMass());
    logger.info(
        "Orbit insertion before transfer: burn {}s ({} s of propellant aboard), mass {} -> {} kg,"
            + " orbit {} x {} km -> {} x {} km",
        (float) dtInsertion,
        (float)
            (stageInfo.remainingFuel(state.getMass())
                / (stageInfo.propulsion().thrust()
                    / (stageInfo.propulsion().isp() * Constants.G0_STANDARD_GRAVITY))),
        (float) state.getMass(),
        (float) inserted.getMass(),
        (float) perigeeKm(state),
        (float) apogeeKm(state),
        (float) perigeeKm(inserted),
        (float) apogeeKm(inserted));
    AnalyticBurnPlan onward = computeBurnPlan(inserted, vehicle, context, false);

    return new AnalyticBurnPlan(
        dtInsertion,
        direction,
        onward.dt1(),
        onward.burn1DirectionInertial(),
        onward.dtCoast(),
        onward.dt2(),
        onward.burn2DirectionInertial(),
        dtInsertion + onward.totalDuration(),
        onward.dv1(),
        onward.dv2());
  }

  /**
   * Burn duration lifting the perigee to the radius the vehicle is at, resolved by flying
   * candidates rather than by the impulsive formula. Measured at design time: the impulsive figure
   * asks 1 723 m/s, which is 435 s of Vinci, and an impulse spread over seven minutes of arc lands
   * the perigee hundreds of kilometres short. A loop must evaluate its candidates on what will
   * actually be flown (MIS-4 / L6).
   */
  /**
   * <b>PHY-8 / L4 — the transfer that lowers</b> (spec {@code docs/etagement/06-conception-L4.md}
   * §3.5).
   *
   * <p>A Hohmann only ever climbed here: burn now, coast to apogee, circularize. That covers every
   * ascent that hands over <em>below</em> its target, which until L4 was all of them — a Falcon
   * Heavy hands over at 77 km for a 400 km orbit. A split Ariane 64 does the opposite: it is a far
   * bigger launcher for the same target, and after the insertion it sits on 400 x 467 km with the
   * perigee already right and 67 km too much apogee.
   *
   * <p>No single burn removes that. A burn produces an orbit through the point it fires at, and 467
   * km is not on a 400 km circle — the best one burn can do from there is exactly the 400 x 467 it
   * is already on. Lowering an apogee takes a burn at the <em>perigee</em>, retrograde, which is
   * the mirror of what this stage already does at apogee.
   *
   * @return the plan, or {@code null} when the orbit is not above its target and the ordinary
   *     ascending Hohmann applies
   */
  private AnalyticBurnPlan computeDescendingPlan(
      SpacecraftState state, Vehicle vehicle, FlightContext context) {
    KeplerianOrbit orbit = new KeplerianOrbit(state.getOrbit());
    double apogee = orbit.getA() * (1 + orbit.getE());
    double perigee = orbit.getA() * (1 - orbit.getE());
    double targetApogee = EARTH_RADIUS + targetApogeeAltitude;
    double targetPerigee = EARTH_RADIUS + targetPerigeeAltitude;
    if (apogee <= targetApogee + DESCENT_THRESHOLD_M
        || perigee < targetPerigee - DESCENT_THRESHOLD_M) {
      return null;
    }

    SpacecraftState atPerigee = detectStateAtPerigee(state, context);
    if (atPerigee == null) {
      return null;
    }

    double mu = state.getOrbit().getMu();
    double rp = atPerigee.getPosition().getNorm();
    Vector3D vAtPerigee = atPerigee.getPVCoordinates().getVelocity();
    // Magnitude only, along the flown velocity: the plane is not this burn's business — a commanded
    // inclination is cleaned at a node by AnalyticPlaneTrimAtNodeStage, and a due-east target has
    // nothing to clean.
    double vTarget = FastMath.sqrt(mu * (2.0 / rp - 2.0 / (rp + targetApogee)));
    double dv = vTarget - vAtPerigee.getNorm();
    if (dv >= 0) {
      return null;
    }

    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(atPerigee.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    double dt =
        Physics.computeBurnDurationCapped(
            -dv,
            atPerigee.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            stageInfo.remainingFuel(atPerigee.getMass()));
    Vector3D retrograde = vAtPerigee.normalize().negate();

    double dtToPerigee = atPerigee.getDate().durationFrom(state.getDate());
    double dtCoast = FastMath.max(0.0, dtToPerigee - dt / 2.0);

    logger.info(
        "Analytic Hohmann, descending: apogee {} km -> {} km, retrograde dv={} m/s over {}s at a"
            + " perigee reached in {}s",
        (float) ((apogee - EARTH_RADIUS) / 1000.0),
        (float) ((targetApogee - EARTH_RADIUS) / 1000.0),
        (float) -dv,
        (float) dt,
        (float) dtToPerigee);

    return new AnalyticBurnPlan(
        0.0, null, 0.0, retrograde, dtCoast, dt, retrograde, dtCoast + dt, 0.0, -dv);
  }

  /**
   * The next perigee after {@code state}, or {@code null} when none is reached within one period.
   * Mirror of {@link AnalyticTrimBurnStage#detectStateAtApogee}, which records the decreasing
   * apsis; this one records the increasing side.
   */
  private static SpacecraftState detectStateAtPerigee(
      SpacecraftState state, FlightContext context) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(state);
    ReentryGuard.armQuiet(propagator, context.gravity());

    RecordAndContinue recorder = new RecordAndContinue();
    propagator.addEventDetector(new ApsideDetector(state.getOrbit()).withHandler(recorder));
    propagator.propagate(state.getDate().shiftedBy(state.getOrbit().getKeplerianPeriod() * 1.1));

    for (RecordAndContinue.Event event : recorder.getEvents()) {
      if (event.isIncreasing()) {
        double dt = event.getState().getDate().durationFrom(state.getDate());
        if (dt > 1.0) {
          return event.getState();
        }
      }
    }
    return null;
  }

  /**
   * Direction of the insertion burn: towards the circular velocity at the current radius, in the
   * plane the vehicle already flies.
   *
   * <p><b>Not prograde, and that is the whole point.</b> A hand-over past apogee is descending, and
   * prograde thrust there raises the <em>far</em> apsis — measured: a full tank spent prograde took
   * a −200 x 568 km arc to 472 x 5 515 km, lifting the apogee by five thousand kilometres while the
   * perigee came up only as a side effect. What has to be cancelled is the radial descent, so the
   * direction is {@code v_circular − v}, the same construction burn 2 of this stage and the trim
   * stage already use.
   *
   * @param state the hand-over state
   * @return the unit direction of the insertion burn
   */
  private static Vector3D insertionDirection(SpacecraftState state) {
    Vector3D position = state.getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    Vector3D momentum = Vector3D.crossProduct(position, velocity);
    Vector3D horizontal = Vector3D.crossProduct(momentum, position).normalize();
    double vCircular = FastMath.sqrt(state.getOrbit().getMu() / position.getNorm());
    return horizontal.scalarMultiply(vCircular).subtract(velocity).normalize();
  }

  /**
   * Burn duration lifting the perigee to the radius the vehicle is at, resolved by <b>flying</b>
   * candidates rather than by the impulsive formula.
   *
   * <p>Measured at design time: the impulsive figure asks 935 m/s here, which is 213 s of Vinci,
   * and a burn that long is not an impulse — the vehicle travels most of an arc under a thrust
   * direction frozen at ignition, and the perigee comes up 2 000 km short. The relation between
   * duration and achieved perigee is monotone, so this bisects on it. A secant was tried first and
   * converged on the wrong root: the response is far too non-linear for two points to describe it.
   *
   * @return the burn duration, or the longest the stage can afford when even that falls short
   */
  private double resolveInsertion(
      SpacecraftState state, Vehicle vehicle, FlightContext context, Vector3D direction) {
    // The mission's own perigee when the vehicle is above it, the current radius when it is not: a
    // single burn cannot raise a perigee above the radius it fires at. Aiming there rather than at
    // a full circularization leaves the apogee for the transfer to finish, which is what the
    // transfer is for.
    double targetPerigeeRadius =
        FastMath.min(EARTH_RADIUS + targetPerigeeAltitude, state.getPosition().getNorm());
    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    double massFlow = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
    double maxBurn = stageInfo.remainingFuel(state.getMass()) / massFlow;

    // The impulsive circularization is the scale of the problem, not the answer: the search runs
    // over [0, 2.5x] of it, capped by the tank.
    double dvCircular =
        FastMath.sqrt(state.getOrbit().getMu() / state.getPosition().getNorm())
            * FastMath.hypot(1.0, 0.0);
    double dtCircular =
        Physics.computeBurnDurationCapped(
            insertionDeltaV(state),
            state.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            stageInfo.remainingFuel(state.getMass()));
    double span = FastMath.min(maxBurn, INSERTION_SPAN_FACTOR * dtCircular);
    if (!(span > 0) || !(dvCircular > 0)) {
      return 0.0;
    }

    // Scanned, not bisected. The achieved perigee is NOT monotone in burn duration: it rises to a
    // maximum near the impulsive circularization and falls again beyond it, because a burn this
    // long is flown in a frozen inertial direction and starts re-eccentricizing the orbit.
    // Measured: the whole tank took the perigee back down to 283 km where a third of it reaches
    // 400 (spec docs/etagement/06-conception-L4.md §3.4).
    double bestDt = 0.0;
    double bestPerigee = -Double.MAX_VALUE;
    double previousDt = 0.0;
    double previousError = -Double.MAX_VALUE;
    for (int i = 1; i <= INSERTION_SCAN_POINTS; i++) {
      double dt = span * i / INSERTION_SCAN_POINTS;
      double error = perigeeError(state, dt, direction, vehicle, context, targetPerigeeRadius);
      if (error + targetPerigeeRadius > bestPerigee) {
        bestPerigee = error + targetPerigeeRadius;
        bestDt = dt;
      }
      if (error >= 0) {
        // Crossed the target on the rising branch: refine between the last two points.
        return refine(state, direction, vehicle, context, targetPerigeeRadius, previousDt, dt);
      }
      previousDt = dt;
      previousError = error;
    }
    logger.info(
        "Orbit insertion: the best reachable perigee is {} km against {} km wanted; flying it and"
            + " letting the transfer judge.",
        (float) ((bestPerigee - EARTH_RADIUS) / 1000.0),
        (float) ((targetPerigeeRadius - EARTH_RADIUS) / 1000.0));
    return previousError > -Double.MAX_VALUE ? bestDt : 0.0;
  }

  /** Bisects the rising branch between a duration that undershoots and one that overshoots. */
  private double refine(
      SpacecraftState state,
      Vector3D direction,
      Vehicle vehicle,
      FlightContext context,
      double targetPerigeeRadius,
      double undershoot,
      double overshoot) {
    double low = undershoot;
    double high = overshoot;
    for (int i = 0; i < INSERTION_REFINE_STEPS; i++) {
      double mid = 0.5 * (low + high);
      double error = perigeeError(state, mid, direction, vehicle, context, targetPerigeeRadius);
      if (FastMath.abs(error) < INSERTION_PERIGEE_TOLERANCE_M) {
        return mid;
      }
      if (error < 0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return 0.5 * (low + high);
  }

  /** Magnitude of the impulsive velocity change that would circularize where the vehicle is. */
  private static double insertionDeltaV(SpacecraftState state) {
    Vector3D position = state.getPosition();
    Vector3D velocity = state.getPVCoordinates().getVelocity();
    Vector3D momentum = Vector3D.crossProduct(position, velocity);
    Vector3D horizontal = Vector3D.crossProduct(momentum, position).normalize();
    double vCircular = FastMath.sqrt(state.getOrbit().getMu() / position.getNorm());
    return horizontal.scalarMultiply(vCircular).subtract(velocity).getNorm();
  }

  private static double perigeeKm(SpacecraftState state) {
    KeplerianOrbit orbit = new KeplerianOrbit(state.getOrbit());
    return (orbit.getA() * (1 - orbit.getE()) - EARTH_RADIUS) / 1000.0;
  }

  private static double apogeeKm(SpacecraftState state) {
    KeplerianOrbit orbit = new KeplerianOrbit(state.getOrbit());
    return (orbit.getA() * (1 + orbit.getE()) - EARTH_RADIUS) / 1000.0;
  }

  private double perigeeError(
      SpacecraftState state,
      double dt,
      Vector3D direction,
      Vehicle vehicle,
      FlightContext context,
      double targetPerigeeRadius) {
    KeplerianOrbit orbit =
        new KeplerianOrbit(flyInsertion(state, dt, direction, vehicle, context).getOrbit());
    return orbit.getA() * (1 - orbit.getE()) - targetPerigeeRadius;
  }

  private SpacecraftState flyInsertion(
      SpacecraftState state,
      double dt,
      Vector3D direction,
      Vehicle vehicle,
      FlightContext context) {
    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(context, burnLimitedMaxStep(state, vehicle));
    propagator.setInitialState(state);
    ReentryGuard.armQuiet(propagator, context.gravity());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            state.getDate(),
            dt,
            propulsion.thrust(),
            propulsion.isp(),
            inertialFrameAttitude(direction, state),
            Vector3D.PLUS_I));
    return propagator.propagate(state.getDate().shiftedBy(dt));
  }

  /**
   * Mirrors the burn 1 force model the mission will use (constant inertial thrust over {@code
   * dt1}), then coasts under the full gravity model to the next apogee. Returns the spacecraft
   * state at that apogee.
   *
   * <p>Package-private so {@link AnalyticGtoInjectionStage} can reuse the same Newton-iteration
   * building block for its perigee-injection plan. Both callers size {@code maxStep} from their
   * active stage via {@link #burnLimitedMaxStep} so this burn-hosting plan propagator honours the
   * late-ignition invariant on a light I7 load (spec 09 §4).
   */
  static SpacecraftState simulateBurn1AndFindApogee(
      FlightContext context,
      SpacecraftState state,
      Vector3D burn1DirectionInertial,
      double dt1,
      double thrust,
      double isp,
      double transferHalfPeriod,
      double maxStep) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(context, maxStep);
    propagator.setInitialState(state);
    // A re-entering aim would otherwise grind here; on a stop no apogee is recorded and the throw
    // below reports it as a plan failure, which the optimizer reads as infeasible.
    ReentryGuard.armQuiet(propagator, context.gravity());

    AbsoluteDate burnStart = state.getDate().shiftedBy(1.0e-3);
    Rotation inertialToBody = new Rotation(burn1DirectionInertial, Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, state.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(burnStart, dt1, thrust, isp, attitude, Vector3D.PLUS_I));

    RecordAndContinue recorder = new RecordAndContinue();
    propagator.addEventDetector(new ApsideDetector(state.getOrbit()).withHandler(recorder));

    // Propagate up to slightly past one transfer half-period; the transfer apogee occurs at
    // roughly dt1/2 + transferHalfPeriod after burn start. 1.2× transferHalfPeriod is enough
    // headroom for J2 timing drift.
    propagator.propagate(state.getDate().shiftedBy(transferHalfPeriod * 1.2));

    // Skip any apsis caught during the burn (the pre-burn parking orbit was near-circular so
    // ApsideDetector can fire spuriously on it). minDt requires the event well after burn end,
    // and within the transfer half-period it should be the transfer apogee.
    double minDt = dt1 + 60.0;
    for (RecordAndContinue.Event event : recorder.getEvents()) {
      if (!event.isIncreasing()) {
        double dt = event.getState().getDate().durationFrom(state.getDate());
        if (dt > minDt) {
          return event.getState();
        }
      }
    }
    return null;
  }

  /**
   * Computes the target velocity vector at apogee for an orbit of perigee radius {@code
   * targetPerigeeRadius}, apogee radius {@code targetApogeeRadius} and inclination {@code
   * targetInclination}, prograde, with a target plane normal chosen to minimize the wedge angle
   * from the current orbit plane (purely a sign convention; the Δv magnitude is unaffected by other
   * plane choices that satisfy the inclination constraint and prograde sense).
   *
   * <p>Magnitude from vis-viva: {@code sqrt(mu·(2/ra − 1/a))} with {@code a = (rp + ra)/2}.
   * Collapses to the circularization velocity {@code sqrt(mu/ra)} when {@code rp == ra}.
   *
   * <p>Package-private so it can be reused by trim-burn stages computing a target-shape + plane-
   * change burn against an in-flight state.
   */
  static Vector3D computeTargetVelocityAtApogee(
      Vector3D rApo,
      Vector3D vCurrentApo,
      double mu,
      double targetPerigeeRadius,
      double targetApogeeRadius,
      double targetInclination) {
    double a = 0.5 * (targetPerigeeRadius + targetApogeeRadius);
    double vMag = FastMath.sqrt(mu * (2.0 / targetApogeeRadius - 1.0 / a));

    Vector3D zHat = Vector3D.PLUS_K;
    Vector3D wCurrent = Vector3D.crossProduct(rApo, vCurrentApo).normalize();

    Vector3D nTarget;
    if (targetInclination < 1e-10) {
      nTarget = zHat;
    } else {
      Vector3D rotAxis = Vector3D.crossProduct(zHat, wCurrent);
      if (rotAxis.getNorm() < 1e-10) {
        rotAxis = rApo.normalize();
      } else {
        rotAxis = rotAxis.normalize();
      }
      Rotation rot = new Rotation(rotAxis, targetInclination, RotationConvention.VECTOR_OPERATOR);
      nTarget = rot.applyTo(zHat);
    }

    Vector3D vTargetDir = Vector3D.crossProduct(nTarget, rApo).normalize();
    return vTargetDir.scalarMultiply(vMag);
  }

  // ════════════════════════════════════════════════════════════════════════
  // Wiring
  // ════════════════════════════════════════════════════════════════════════

  private void addBurns(
      NumericalPropagator propagator,
      SpacecraftState state,
      AnalyticBurnPlan plan,
      Vehicle vehicle) {
    AbsoluteDate epoch = state.getDate();

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    DepletionGuard.armCappedBurn(propagator, stage1.depletionFloor(), getName());

    // The insertion burn, when the plan needed one: it fires first, from the hand-over state, and
    // everything below is shifted by its duration.
    if (plan.dtInsertion() > 0) {
      propagator.addForceModel(
          new ConstantThrustManeuver(
              epoch.shiftedBy(1.0e-3),
              plan.dtInsertion(),
              propulsion1.thrust(),
              propulsion1.isp(),
              inertialFrameAttitude(plan.insertionDirectionInertial(), state),
              Vector3D.PLUS_I));
    }
    AbsoluteDate burn1Start = epoch.shiftedBy(1.0e-3 + plan.dtInsertion());
    // A descending plan has no first burn: it coasts to the perigee and brakes there.
    if (plan.dt1() > 0) {
      // Burn 1 uses a frame-aligned inertial attitude: the ΔV₁ vector has a radial component
      // (cancelling the parking-orbit residual eccentricity at the node) plus the tangential
      // Hohmann boost, so a pure-prograde LOF-TNW attitude would not deliver the right vector.
      propagator.addForceModel(
          new ConstantThrustManeuver(
              burn1Start,
              plan.dt1(),
              propulsion1.thrust(),
              propulsion1.isp(),
              inertialFrameAttitude(plan.burn1DirectionInertial(), state),
              Vector3D.PLUS_I));
    }

    // Burn 2 fires the same stage as burn 1 (no jettison in this phase, see
    // VehicleStack#resolveActiveStage), so it reuses propulsion1.
    AbsoluteDate burn2Start = epoch.shiftedBy(plan.dtInsertion() + plan.dt1() + plan.dtCoast());
    // Burn 2 uses a frame-aligned attitude so the thrust direction stays constant in inertial
    // throughout the finite burn — this is what makes the combined circularization + plane change
    // converge to the impulsive target instead of losing authority to LOF rotation.
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn2Start,
            plan.dt2(),
            propulsion1.thrust(),
            propulsion1.isp(),
            inertialFrameAttitude(plan.burn2DirectionInertial(), state),
            Vector3D.PLUS_I));
  }

  /**
   * Builds a {@link FrameAlignedProvider} that points body {@code PLUS_I} along {@code direction}
   * in the spacecraft inertial frame. The provider holds the rotation constant for the duration of
   * any maneuver attached to it.
   */
  private static FrameAlignedProvider inertialFrameAttitude(
      Vector3D direction, SpacecraftState state) {
    Rotation inertialToBody = new Rotation(direction, Vector3D.PLUS_I);
    return new FrameAlignedProvider(inertialToBody, state.getFrame());
  }
}
