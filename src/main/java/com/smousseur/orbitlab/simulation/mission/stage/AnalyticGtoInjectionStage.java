package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.NodeDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Finite-burn perigee injection raising the apogee to a target altitude (parking orbit → GTO).
 * The plan reuses the Hohmann stage's Newton iteration on the aimed apogee radius so the
 * finite-burn apogee lands on target despite steering and gravity losses. The stage ends at burn
 * cutoff: the spent upper stage separates right after ({@link StageSeparationStage}) and the
 * payload's kick motor performs the apogee circularization (spec 06 I5).
 *
 * <p><b>Node-aware injection (bilan 11 §3.10).</b> The downstream apogee circularization ({@link
 * AnalyticApogeeCircularizationStage}) removes the whole launch-site inclination (~5.2° from
 * Kourou) with its burn centred on apogee — but a plane change at apogee only has authority when
 * <em>apogee sits on an equatorial node</em>. Injecting at a parking-orbit node and trusting the
 * Keplerian antipode to put apogee on the opposite node is fragile: a finite burn plus J2
 * precession over the ~5 h transfer drift the flown apogee off-node, and the plane change then
 * starves. This stage therefore targets the node explicitly: it measures the flown transfer
 * apogee's latitude and, when the orbit is inclined and the apogee is off-node, nudges a lead-in
 * coast so the apogee lands on the equator. An equatorial parking orbit (i≈0) or an apogee already
 * on the node is left untouched, so the nominal flow and the unit tests keep their pre-node-aware
 * behaviour.
 */
public class AnalyticGtoInjectionStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticGtoInjectionStage.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /**
   * Newton iterations on the aimed apogee radius.
   *
   * <p>Four is enough because the iteration only ever has room to move while the burn is <em>not</em>
   * propellant-limited, and there it converges to under 100 m in two or three steps. Once {@link
   * Physics#computeBurnDurationCapped} clamps the duration to depletion, no iteration count helps —
   * the loop breaks out on that condition instead (bilan 11 §3.7).
   */
  private static final int AIM_ITERATIONS = 4;

  /**
   * Residual apogee defect accepted when the iteration runs out of steps, as a fraction of the
   * target radius. The iteration breaks out at 100 m when it converges, so this only has to
   * separate "a few iterations short" from "never got there" — 1 % of GEO is ~420 km, far above any
   * healthy residual (the reference mission lands within 100 m).
   *
   * <p><b>It is not the criterion that decides feasibility</b>, and reading it that way misleads:
   * on the I7 GEO run the tightest refusal missed by 472 km against this ~422 km bar, which looked
   * like the guard firing at its own resolution. It was not — that burn was propellant-capped, so
   * its defect was the honest shortfall of the largest burn the stage could perform, and no
   * threshold placement would have made it feasible. The capability check above owns that verdict;
   * this ratio only grades the solver's own convergence.
   */
  private static final double AIM_CONVERGENCE_TOLERANCE_RATIO = 0.01;

  /**
   * Secant refinements of the lead-in coast that places the transfer apogee on an equatorial node
   * (bilan 11 §3.10). Only reached for an inclined, off-node injection; each iteration costs one
   * apogee-radius aim (a burn + ~half-transfer propagation), so the budget is kept tight. After
   * coasting to a node the residual off-node is a few tenths of a degree and two or three secant
   * steps clear it.
   */
  private static final int NODE_AIM_ITERATIONS = 5;

  /**
   * Apogee latitude accepted as "on the node" (rad). At this proximity the apogee plane change
   * keeps essentially full authority and the node-targeted plane trim downstream mops up the
   * remainder; tightening it further only spins the secant. 0.05° places the apogee within ~0.5° of
   * the node in argument of latitude for a 5° orbit.
   */
  private static final double NODE_LATITUDE_TOLERANCE_RAD = FastMath.toRadians(0.05);

  /**
   * Below this |sin(i)| the parking orbit is treated as equatorial: the apogee is on the equator
   * whatever the injection phase, so there is nothing to target. Guards the near-equatorial unit
   * tests (i = 0) against a degenerate node search.
   */
  private static final double NEAR_EQUATORIAL_SIN_I = 1.0e-3;

  private final double targetApogeeAltitude;

  /**
   * Creates a GTO injection stage.
   *
   * @param name the human-readable name of this stage
   * @param targetApogeeAltitude the transfer-orbit apogee altitude to reach (m)
   */
  public AnalyticGtoInjectionStage(String name, double targetApogeeAltitude) {
    super(name);
    this.targetApogeeAltitude = targetApogeeAltitude;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    InjectionPlan plan = computePlan(state, mission.getVehicle());

    addBurn(propagator, state, plan, mission.getVehicle());

    AbsoluteDate endDate = state.getDate().shiftedBy(plan.leadCoast() + 1.0e-3 + plan.dt1());
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
    InjectionPlan plan = computePlan(currentState, mission.getVehicle());

    // 8×8 gravity, matching the ephemeris generator (bilan 11 §3.9): this standalone flight advances
    // the state the next stage plans from, so a Newtonian point-mass field here would diverge from
    // the flown 8×8 trajectory and break the apogee-node geometry the downstream plane change relies
    // on — the GTO injection is precisely where that geometry is set.
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    addBurn(propagator, currentState, plan, mission.getVehicle());

    return propagator.propagate(
        currentState.getDate().shiftedBy(plan.leadCoast() + 1.0e-3 + plan.dt1()));
  }

  /**
   * @param leadCoast unpowered coast from the entry state to the injection point, sized so the
   *     transfer apogee lands on an equatorial node (0 for the nominal node-start / equatorial case)
   * @param dt1 injection burn duration
   * @param burnDirectionInertial constant inertial thrust direction of the injection burn
   * @param dv1 injection ΔV magnitude
   */
  private record InjectionPlan(
      double leadCoast, double dt1, Vector3D burnDirectionInertial, double dv1) {}

  /** Result of aiming the injection burn at the apogee <em>radius</em> from a given injection state. */
  private record AimResult(
      double dt1, Vector3D burnDirectionInertial, double dv1, SpacecraftState apogeeState) {}

  /**
   * Plans the injection burn, targeting the equatorial node at apogee (bilan 11 §3.10).
   *
   * <p>The apogee <em>radius</em> aim and the node targeting are nearly orthogonal knobs — the
   * former is set by the burn ΔV magnitude, the latter by <em>when</em> the burn fires — so they are
   * solved in sequence: radius first at the current phase, then a lead-in coast to null the flown
   * apogee's latitude.
   */
  private InjectionPlan computePlan(SpacecraftState entryState, Vehicle vehicle) {
    double sinInclination = FastMath.sin(entryState.getOrbit().getI());

    // Aim at the current phase first. In the nominal GEO flow the preceding CoastingStage
    // (stopAtNode) already dropped us on an equatorial node, so the antipodal apogee is on the
    // equator and this returns immediately with no lead coast. An equatorial parking orbit (i≈0,
    // e.g. the unit tests) has its apogee on the equator whatever the phase and is likewise left as
    // is — keeping both on the exact pre-node-aware path.
    AimResult here = aimApogeeRadius(entryState, vehicle);
    double latHere = apogeeLatitudeRad(here.apogeeState());
    if (sinInclination < NEAR_EQUATORIAL_SIN_I
        || FastMath.abs(latHere) < NODE_LATITUDE_TOLERANCE_RAD) {
      return new InjectionPlan(0.0, here.dt1(), here.burnDirectionInertial(), here.dv1());
    }

    // Off-node: the transfer apogee would fall at latitude ≈ −i·sin(u_inj), where the AKM plane
    // change has no authority. Coast to the next equatorial node — where the antipodal apogee is
    // near the equator by construction, and where a small lead coast has room to move the apogee
    // either way (impossible from the entry epoch, which can only coast forward). Then secant-refine
    // to null the finite-burn/J2 residual off-node.
    double period = entryState.getOrbit().getKeplerianPeriod();
    double nodeLead = timeToNextNode(entryState);
    if (Double.isNaN(nodeLead)) {
      logger.warn(
          "[{}] node targeting: no equatorial node found within a period; flying the un-nudged "
              + "injection (apogee latitude {}°).",
          getName(),
          FastMath.toDegrees(latHere));
      return new InjectionPlan(0.0, here.dt1(), here.burnDirectionInertial(), here.dv1());
    }

    double leadCoast = nodeLead;
    double leadPrev = Double.NaN;
    double latPrev = Double.NaN;
    double bestLead = 0.0;
    double bestAbsLat = FastMath.abs(latHere);
    AimResult bestAim = here;

    for (int iter = 0; iter < NODE_AIM_ITERATIONS; iter++) {
      SpacecraftState injectionState = coastForward(entryState, leadCoast);
      AimResult aim = aimApogeeRadius(injectionState, vehicle);
      double latitude = apogeeLatitudeRad(aim.apogeeState());

      logger.info(
          "GTO node targeting iter {}: lead coast {}s, apogee latitude {}°",
          iter,
          FastMath.round(leadCoast),
          FastMath.toDegrees(latitude));

      if (FastMath.abs(latitude) < bestAbsLat) {
        bestAbsLat = FastMath.abs(latitude);
        bestLead = leadCoast;
        bestAim = aim;
      }
      if (FastMath.abs(latitude) < NODE_LATITUDE_TOLERANCE_RAD) {
        return new InjectionPlan(leadCoast, aim.dt1(), aim.burnDirectionInertial(), aim.dv1());
      }

      double slope;
      if (Double.isNaN(latPrev)) {
        // At the node, d(apogee latitude)/d(lead) = −i·n·cos(u_inj) = ∓i·n; the sign follows the
        // crossing sense (ascending, vz > 0 ⇒ u_inj ≈ 0 ⇒ slope −i·n).
        double vz = injectionState.getPVCoordinates().getVelocity().getZ();
        double n = 2.0 * FastMath.PI / period;
        slope = -sinInclination * n * FastMath.signum(vz == 0.0 ? 1.0 : vz);
      } else {
        slope = (latitude - latPrev) / (leadCoast - leadPrev);
      }
      double step = -latitude / slope;
      // Stay in the node's basin: the latitude is sinusoidal in the lead coast, so an unclamped
      // secant could leap into the far node's basin and stall. The residual is small, so is the step.
      step = FastMath.max(-period / 8.0, FastMath.min(period / 8.0, step));
      leadPrev = leadCoast;
      latPrev = latitude;
      leadCoast = FastMath.max(0.0, leadCoast + step);
    }

    // Best effort rather than a refusal: the injection radius did converge (aimApogeeRadius owns
    // that verdict and throws otherwise). A residual off-node apogee is caught downstream — the AKM
    // leaves the plane uncorrected, the node plane-trim starves, the DepletionGuard trips and
    // MissionEphemeris.isComplete() drops (bilan 11 §3.9) — so a warn keeps the signal visible
    // without pre-emptively rejecting a solution the feasibility machinery may still validate.
    logger.warn(
        "[{}] node targeting did not reach {}° in {} iterations; flying best apogee latitude {}° "
            + "(lead coast {}s) — the apogee plane change may be under-authoritative (bilan 11 §3.10).",
        getName(),
        FastMath.toDegrees(NODE_LATITUDE_TOLERANCE_RAD),
        NODE_AIM_ITERATIONS,
        FastMath.toDegrees(bestAbsLat),
        FastMath.round(bestLead));
    return new InjectionPlan(
        bestLead, bestAim.dt1(), bestAim.burnDirectionInertial(), bestAim.dv1());
  }

  /**
   * Aims the injection burn so the finite-burn transfer apogee reaches the target radius, from a
   * given injection state. Newton on the aimed apogee radius (same scheme as the Hohmann stage's
   * burn 1). Refuses the plan — distinguishing a propellant-capped capability limit from a solver
   * failure (bilan 11 §3.7) — when the apogee cannot be reached.
   */
  private AimResult aimApogeeRadius(SpacecraftState state, Vehicle vehicle) {
    double mu = state.getOrbit().getMu();
    Vector3D r1 = state.getPVCoordinates().getPosition();
    Vector3D v1 = state.getPVCoordinates().getVelocity();
    double r1Mag = r1.getNorm();
    Vector3D rHat = r1.normalize();
    double vRadial1 = v1.dotProduct(rHat);
    Vector3D vTangential1 = v1.subtract(rHat.scalarMultiply(vRadial1));
    Vector3D tHat = vTangential1.normalize();

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    double maxStep = burnLimitedMaxStep(state, vehicle);

    // Newton on the aimed apogee radius: the finite burn under-shoots the impulsive target, so
    // aim higher until the simulated post-burn apogee lands on r2 (same scheme as the Hohmann
    // stage's burn 1, which this stage replaces on the GEO profile).
    double r2 = EARTH_RADIUS + targetApogeeAltitude;
    double r2Aim = r2;
    double dv1 = 0.0;
    double dt1 = 0.0;
    double bias = Double.NaN;
    boolean fuelCapped = false;
    Vector3D deltaV1 = Vector3D.ZERO;
    SpacecraftState stateAtApogee = null;
    double remainingFuel = FastMath.max(0.0, stage1.remainingFuel(state.getMass()));
    double massFlow =
        propulsion1.thrust() / (propulsion1.isp() * Constants.G0_STANDARD_GRAVITY);
    double depletionDuration = remainingFuel / massFlow;
    for (int iter = 0; iter < AIM_ITERATIONS; iter++) {
      double aTransfer = (r1Mag + r2Aim) / 2.0;
      double vTransferPerigee = FastMath.sqrt(mu * (2.0 / r1Mag - 1.0 / aTransfer));
      Vector3D vAfterBurn1 = tHat.scalarMultiply(vTransferPerigee);
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
          AnalyticHohmannTransferStage.simulateBurn1AndFindApogee(
              state,
              deltaV1.normalize(),
              dt1,
              propulsion1.thrust(),
              propulsion1.isp(),
              transferHalfPeriod,
              maxStep);
      bias = r2 - stateAtApogee.getPVCoordinates().getPosition().getNorm();
      r2Aim += bias;
      if (FastMath.abs(bias) < 100.0) {
        break;
      }
      // Aiming higher only buys apogee while the burn can still get longer. Once the duration is
      // pinned to depletion, computeBurnDurationCapped clamps dt1 independently of dv1 — so dt1 no
      // longer depends on r2Aim, the next iteration simulates the very same burn and reads the very
      // same bias, while r2Aim accumulates the defect into a meaningless aim. The iteration has no
      // degree of freedom left: stop, and let the check below report a capability limit rather than
      // spending three more propagations re-deriving one number.
      if (dt1 >= depletionDuration - 1.0e-9) {
        fuelCapped = true;
        break;
      }
    }

    // The iteration only converges when the burn can actually reach the target. Refuse the plan
    // otherwise: the caller (mission optimizer, then the I7 outer loop) reads this as a clean
    // infeasibility. Without the refusal the unchecked loop accumulates the full defect into r2Aim
    // — the I7 GEO run produced an aim of 177 000 km for a 35 786 km target, whose multi-day
    // transfer orbit then made the downstream propagation grind for tens of minutes.
    //
    // Two failures reach this point and they are NOT the same thing (bilan 11 §3.7). Conflating
    // them under "did not converge" is what made the I7 GEO wall look like a solver artifact worth
    // re-running with a bigger iteration budget, when it was the vehicle's own limit.
    if (!(FastMath.abs(bias) <= AIM_CONVERGENCE_TOLERANCE_RATIO * r2)) {
      if (fuelCapped) {
        // Capability limit: the stage is burning everything it has and still falls short. More
        // iterations are provably useless here — dt1 is clamped, so the aim cannot move the apogee.
        throw new OrbitlabException(
            String.format(
                "[%s] injection out of reach: burning all %.0f kg left in the active stage "
                    + "(%.3f s at full thrust, Δv %.0f m/s) still leaves the apogee %.0f km short "
                    + "of the %.0f km target — the stage cannot perform this injection",
                getName(),
                remainingFuel,
                dt1,
                dv1,
                bias / 1000.0,
                targetApogeeAltitude / 1000.0));
      }
      // Solver failure: the burn still had propellant margin, so the aim had room to move and the
      // iteration simply ran out of steps. This one *would* warrant a larger AIM_ITERATIONS.
      throw new OrbitlabException(
          String.format(
              "[%s] injection plan did not converge: apogee still %.0f km short of the %.0f km "
                  + "target after %d iterations (Δv %.0f m/s over %.3f s, %.0f kg of propellant "
                  + "left, %.3f s short of depletion) — the burn was not propellant-limited, so "
                  + "this is a solver failure, not a capability limit",
              getName(),
              bias / 1000.0,
              targetApogeeAltitude / 1000.0,
              AIM_ITERATIONS,
              dv1,
              dt1,
              remainingFuel,
              depletionDuration - dt1));
    }

    logger.info(
        "GTO injection plan: dv1={} m/s, dt1={}s, aimed apogee alt={} km (residual bias {} km)",
        dv1,
        dt1,
        (r2Aim - EARTH_RADIUS) / 1000.0,
        bias / 1000.0);

    return new AimResult(dt1, deltaV1.normalize(), dv1, stateAtApogee);
  }

  /** Geocentric latitude (equatorial-plane offset) of the apogee in GCRF; 0 on a node. */
  private static double apogeeLatitudeRad(SpacecraftState apogeeState) {
    Vector3D r = apogeeState.getPVCoordinates().getPosition();
    return FastMath.asin(r.getZ() / r.getNorm());
  }

  /**
   * Burn-free coast from {@code state} by {@code dt} under the 8×8 model, matching the flown
   * lead-in coast the injection schedules before its burn. Steps at the large coast cap (nothing
   * ignites here — the injection burn is a separate force model scheduled after it).
   */
  private static SpacecraftState coastForward(SpacecraftState state, double dt) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(state);
    return propagator.propagate(state.getDate().shiftedBy(dt));
  }

  /**
   * Seconds from {@code state} to the next equatorial node crossing, or {@code NaN} if none is
   * found within a little over one period (an equatorial orbit has none). Burn-free, so it steps at
   * the large coast cap.
   */
  private static double timeToNextNode(SpacecraftState state) {
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
    propagator.setInitialState(state);
    RecordAndContinue recorder = new RecordAndContinue();
    propagator.addEventDetector(
        new NodeDetector(OrekitService.get().gcrf()).withHandler(recorder));
    double period = state.getOrbit().getKeplerianPeriod();
    propagator.propagate(state.getDate().shiftedBy(period * 1.1));
    for (RecordAndContinue.Event event : recorder.getEvents()) {
      double dt = event.getState().getDate().durationFrom(state.getDate());
      if (dt > 1.0) { // skip a node we may already be sitting on
        return dt;
      }
    }
    return Double.NaN;
  }

  private void addBurn(
      NumericalPropagator propagator, SpacecraftState state, InjectionPlan plan, Vehicle vehicle) {
    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    DepletionGuard.arm(propagator, stage1.depletionFloor(), getName());

    // Frame-aligned inertial attitude: the ΔV vector carries a radial component cancelling the
    // parking-orbit residual eccentricity, so a pure-prograde TNW attitude would miss it. The burn
    // fires after the node-targeting lead coast (0 in the nominal/equatorial case).
    Rotation inertialToBody = new Rotation(plan.burnDirectionInertial(), Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, state.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            state.getDate().shiftedBy(plan.leadCoast() + 1.0e-3),
            plan.dt1(),
            propulsion1.thrust(),
            propulsion1.isp(),
            attitude,
            Vector3D.PLUS_I));
  }
}
