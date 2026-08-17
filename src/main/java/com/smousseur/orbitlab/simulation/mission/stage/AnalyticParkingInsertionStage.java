package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Deterministic two-burn Hohmann insertion from a sub-orbital perigee state to a circular parking
 * orbit at a target altitude, preserving the current orbital plane.
 *
 * <p>Geometry assumption: the entry state lies at the periapsis of an elliptical orbit, i.e. {@code
 * vRad = 0} and {@code |v| > vCirc(r)} at the entry point. This is the typical end-state of a
 * gravity turn that successfully shapes a sub-orbital trajectory whose apoapsis is below the target
 * altitude. Under this assumption:
 *
 * <ul>
 *   <li>Burn 1 is a pure prograde tangential burn at periapsis; the transfer ellipse's apoapsis
 *       reaches the target altitude.
 *   <li>Burn 2 is a pure prograde tangential burn at the next apoapsis; the orbit circularizes at
 *       the target altitude in the same plane.
 * </ul>
 *
 * <p>The "apoapsis below the target" half of that assumption is <b>checked</b>: an entry state
 * carrying enough energy to overshoot the target would require a retrograde burn, which this stage
 * cannot express, so the plan is refused rather than flown (see {@code DV_SIGN_TOLERANCE}). The
 * "entry at periapsis" half is only approximate in practice — the gravity turn hands off at a
 * flight path angle of one to a few degrees — and is deliberately not checked.
 *
 * <p>Separately from the geometry, the stage checks it can actually <b>fly</b> the plan it just
 * computed: a burn whose duration is capped by the propellant left aboard is refused (see {@code
 * BURN_CAPACITY_TOLERANCE}), the same verdict {@link AnalyticGtoInjectionStage} returns when the
 * injection is out of reach.
 *
 * <p>The stage does not implement {@link
 * com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage}; the mission optimizer
 * propagates it via {@link #propagateStandalone(SpacecraftState, Mission)} without running CMA-ES.
 *
 * <p>Accepted approximations (v1):
 *
 * <ul>
 *   <li>Burn 1 starts at {@code epoch + 1 ms}, not centered on periapsis — for a burn of a few
 *       seconds on an orbit with period ~2700 s the resulting phase slip is &lt;0.1%.
 *   <li>Burn 2 coast is half-period minus half of burn 2's duration to center burn 2 on the
 *       apoapsis passage.
 *   <li>J2 short-period altitude oscillation (~3 km) is not compensated.
 * </ul>
 */
public class AnalyticParkingInsertionStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticParkingInsertionStage.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /**
   * Retrograde ΔV tolerated before a burn is rejected as an assumption violation (m/s). Sized to
   * separate "the entry apoapsis is numerically level with the target, so this burn is a no-op"
   * from a real geometry breakdown: on the reference GEO profile burn 1 runs at +20 to +59 m/s, and
   * the run that exposed the flaw sat at −57 m/s.
   */
  private static final double DV_SIGN_TOLERANCE = 1.0;

  /**
   * Fraction of a burn's required duration that may be lost to propellant capping before the plan
   * is refused. Covers rounding between the Tsiolkovsky duration and the depletion duration;
   * anything beyond it is a stage that cannot deliver the ΔV it just planned.
   *
   * <p><b>Why refusing rather than under-burning.</b> {@link Physics#computeBurnDurationCapped}
   * caps a burn at depletion on the premise that the shortfall shows up later as a missed
   * objective. That premise does not hold here. Observed on the I7 GEO multi-stage sweep at {@code
   * λ(S1) = 0.3}: the gravity turn tripped the {@code DepletionGuard} on its second burn and handed
   * this stage a stack sitting exactly on its dry mass, so both burns were capped to {@code 0 s}
   * while the plan still asked for 372 and 107 m/s. Nothing refused, and the phase went on to
   * propagate 2 666 s of pure ballistics from 36 km at 7 603 m/s — a re-entering trajectory, which
   * no detector on this chain stops: the integrator follows it below the surface, the step control
   * collapses as {@code r → 0} and the evaluation never returns. The outer loop would have read
   * this λ as infeasible in seconds; instead it hung for hours.
   *
   * <p>This is the capability sibling of {@code DV_SIGN_TOLERANCE}: that one guards the sign of the
   * ΔV, this one guards the ability to deliver it.
   */
  private static final double BURN_CAPACITY_TOLERANCE = 1.0e-3;

  private final double targetAltitude;

  /**
   * @param name human-readable stage name
   * @param targetAltitude target circular orbit altitude (m, above Earth surface)
   */
  public AnalyticParkingInsertionStage(String name, double targetAltitude) {
    super(name);
    this.targetAltitude = targetAltitude;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    BurnPlan plan = computeBurnPlan(state, mission.getVehicle());

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
    BurnPlan plan = computeBurnPlan(currentState, mission.getVehicle());

    // 8×8 gravity, matching the ephemeris generator (bilan 11 §3.9): this standalone flight
    // advances
    // the state the next stage plans from, so a Newtonian point-mass field here would diverge from
    // the flown 8×8 trajectory and break the apogee-node geometry the GEO plane change relies on.
    GravitationalContext body = gravitationalContext(mission);
    NumericalPropagator propagator =
        OrekitService.get()
            .createOptimizationPropagator(
                body, burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    ReentryGuard.armQuiet(propagator, body);
    addBurns(propagator, currentState, plan, mission.getVehicle());

    return propagator.propagate(currentState.getDate().shiftedBy(plan.totalDuration()));
  }

  private record BurnPlan(
      double dt1, double dtCoast, double dt2, double dv1, double dv2, double totalDuration) {}

  private BurnPlan computeBurnPlan(SpacecraftState state, Vehicle vehicle) {
    double mu = state.getOrbit().getMu();

    double r1 = state.getPVCoordinates().getPosition().getNorm();
    double v1 = state.getPVCoordinates().getVelocity().getNorm();

    double r2 = EARTH_RADIUS + targetAltitude;
    double aTransfer = (r1 + r2) / 2.0;
    double vTransferPerigee = FastMath.sqrt(mu * (2.0 / r1 - 1.0 / aTransfer));
    double vTransferApogee = FastMath.sqrt(mu * (2.0 / r2 - 1.0 / aTransfer));
    double vCircTarget = FastMath.sqrt(mu / r2);

    double dv1Raw = vTransferPerigee - v1;
    double dv2Raw = vCircTarget - vTransferApogee;

    // Both burns of a raising Hohmann transfer are prograde. A retrograde ΔV means the entry
    // apoapsis already sits above the target, i.e. the geometry documented on this class does not
    // hold — and it is not a benign inaccuracy: Physics.computeBurnDuration evaluates
    // (1 − exp(−Δv/ve)), which goes NEGATIVE for a negative Δv, so the plan hands the propagator a
    // maneuver whose end date precedes its start and predicts a mass GAIN across burn 1. Observed
    // on the I7 GEO loop at λ=0.3 (dv1 = −57 m/s, dt1 = −0.61 s): the burns silently did nothing
    // and the mission flew on with a corrupted plan. Refuse it instead — the caller (mission
    // optimizer, then the outer propellant loop) reads this as a clean infeasibility.
    if (dv1Raw < -DV_SIGN_TOLERANCE || dv2Raw < -DV_SIGN_TOLERANCE) {
      throw new OrbitlabException(
          String.format(
              "[%s] cannot plan the insertion to a %.0f km circular orbit: the entry state at "
                  + "%.0f km already carries too much energy, so the transfer would need a "
                  + "retrograde burn (ΔV1 %.1f m/s, ΔV2 %.1f m/s — both must be prograde). The "
                  + "entry apoapsis is above the target, which breaks the raising-Hohmann "
                  + "geometry this stage assumes",
              getName(), targetAltitude / 1000.0, (r1 - EARTH_RADIUS) / 1000.0, dv1Raw, dv2Raw));
    }
    // Within tolerance the entry apoapsis is level with the target: the correct response is no
    // burn at all, not a retrograde one.
    double dv1 = FastMath.max(0.0, dv1Raw);
    double dv2 = FastMath.max(0.0, dv2Raw);

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    double fuelAtBurn1 = stage1.remainingFuel(state.getMass());
    double dt1 =
        Physics.computeBurnDurationCapped(
            dv1, state.getMass(), propulsion1.isp(), propulsion1.thrust(), fuelAtBurn1);
    requireDeliverable("transfer injection", dv1, dt1, state.getMass(), propulsion1, fuelAtBurn1);

    double g0Ve = propulsion1.isp() * Constants.G0_STANDARD_GRAVITY;
    double massAfterBurn1 = state.getMass() * FastMath.exp(-dv1 / g0Ve);

    // Both burns are flown by the SAME stage — this phase contains no jettison, and only a
    // jettison can change the active one (invariant on VehicleStack#resolveActiveStage). Only the
    // mass moves between them.
    double fuelAtBurn2 = stage1.remainingFuel(massAfterBurn1);
    double dt2 =
        Physics.computeBurnDurationCapped(
            dv2, massAfterBurn1, propulsion1.isp(), propulsion1.thrust(), fuelAtBurn2);
    requireDeliverable("circularization", dv2, dt2, massAfterBurn1, propulsion1, fuelAtBurn2);

    double transferPeriod =
        2.0 * FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);
    double dtCoastImpulsive = transferPeriod / 2.0;
    double dtCoast = FastMath.max(0.0, dtCoastImpulsive - dt1 / 2.0 - dt2 / 2.0);

    double totalDuration = dt1 + dtCoast + dt2;

    logger.info(
        "Analytic parking plan: dv1={} m/s, dt1={}s, dtCoast={}s, dv2={} m/s, dt2={}s",
        dv1,
        dt1,
        dtCoast,
        dv2,
        dt2);

    return new BurnPlan(dt1, dtCoast, dt2, dv1, dv2, totalDuration);
  }

  /**
   * Refuses a burn the active stage cannot deliver, distinguishing a propellant-capped capability
   * limit from a planning failure — the verdict the mission optimizer and the outer propellant loop
   * read as a clean infeasibility. See {@link #BURN_CAPACITY_TOLERANCE} for why a capped burn is
   * refused here rather than flown short.
   *
   * @param burnLabel which burn of the transfer this is, for the message
   * @param dv the ΔV the plan asks this burn for (m/s)
   * @param cappedDuration the burn duration after propellant capping (s)
   * @param mass the spacecraft mass at ignition (kg)
   * @param propulsion the burning stage's propulsion
   * @param remainingFuel the propellant left in the burning stage at ignition (kg)
   */
  private void requireDeliverable(
      String burnLabel,
      double dv,
      double cappedDuration,
      double mass,
      PropulsionSystem propulsion,
      double remainingFuel) {
    if (!(dv > 0)) {
      return; // nothing to deliver — the burn is a deliberate no-op
    }
    double required = Physics.computeBurnDuration(dv, mass, propulsion.isp(), propulsion.thrust());
    if (cappedDuration >= required * (1.0 - BURN_CAPACITY_TOLERANCE)) {
      return;
    }
    double ve = propulsion.isp() * Constants.G0_STANDARD_GRAVITY;
    double burnt =
        FastMath.min(propulsion.thrust() / ve * cappedDuration, FastMath.max(0.0, remainingFuel));
    double delivered = burnt > 0 ? ve * FastMath.log(mass / (mass - burnt)) : 0.0;
    throw new OrbitlabException(
        String.format(
            "[%s] %s out of reach: burning all %.0f kg left in the active stage (%.2f s at full "
                + "thrust, Δv %.0f m/s) falls %.0f m/s short of the %.0f m/s this burn needs to "
                + "reach a %.0f km circular orbit — the stage cannot fly the plan it just computed",
            getName(),
            burnLabel,
            FastMath.max(0.0, remainingFuel),
            cappedDuration,
            delivered,
            dv - delivered,
            dv,
            targetAltitude / 1000.0));
  }

  private void addBurns(
      NumericalPropagator propagator, SpacecraftState state, BurnPlan plan, Vehicle vehicle) {
    AbsoluteDate epoch = state.getDate();
    LofOffset attitude = new LofOffset(state.getFrame(), LOFType.TNW);
    Vector3D progradeTNW = Physics.buildThrustDirectionTNW(0.0, 0.0);

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    DepletionGuard.arm(propagator, stage1.depletionFloor(), getName());
    AbsoluteDate burn1Start = epoch.shiftedBy(1.0e-3);
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn1Start,
            plan.dt1(),
            propulsion1.thrust(),
            propulsion1.isp(),
            attitude,
            progradeTNW));

    // Burn 2 fires the same stage as burn 1 (no jettison in this phase, see
    // VehicleStack#resolveActiveStage), so it reuses propulsion1.
    AbsoluteDate burn2Start = epoch.shiftedBy(plan.dt1() + plan.dtCoast());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn2Start,
            plan.dt2(),
            propulsion1.thrust(),
            propulsion1.isp(),
            attitude,
            progradeTNW));
  }
}
