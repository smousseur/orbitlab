package com.smousseur.orbitlab.simulation.mission.stage;

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
 * <p>The stage does not implement {@link
 * com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage}; the mission optimizer
 * propagates it via {@link #propagateStandalone(SpacecraftState, Mission)} without running CMA-ES.
 *
 * <p>Accepted approximations (v1):
 *
 * <ul>
 *   <li>Burn 1 starts at {@code epoch + 1 ms}, not centered on periapsis — for a burn of a few
 *       seconds on an orbit with period ~2700 s the resulting phase slip is &lt;0.1%.
 *   <li>Burn 2 coast is half-period minus half of burn 2's duration to center burn 2 on the apoapsis
 *       passage.
 *   <li>J2 short-period altitude oscillation (~3 km) is not compensated.
 * </ul>
 */
public class AnalyticParkingInsertionStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticParkingInsertionStage.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

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
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    BurnPlan plan = computeBurnPlan(currentState, mission.getVehicle());

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(currentState);
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

    double dv1 = vTransferPerigee - v1;
    double dv2 = vCircTarget - vTransferApogee;

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    double dt1 =
        Physics.computeBurnDurationCapped(
            dv1,
            state.getMass(),
            propulsion1.isp(),
            propulsion1.thrust(),
            stage1.remainingFuel(state.getMass()));

    double g0Ve = propulsion1.isp() * Constants.G0_STANDARD_GRAVITY;
    double massAfterBurn1 = state.getMass() * FastMath.exp(-dv1 / g0Ve);

    ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
    PropulsionSystem propulsion2 = stage2.propulsion();
    double dt2 =
        Physics.computeBurnDurationCapped(
            dv2,
            massAfterBurn1,
            propulsion2.isp(),
            propulsion2.thrust(),
            stage2.remainingFuel(massAfterBurn1));

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

    double massAfterBurn1 =
        state.getMass()
            * FastMath.exp(-plan.dv1() / (propulsion1.isp() * Constants.G0_STANDARD_GRAVITY));
    ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
    PropulsionSystem propulsion2 = stage2.propulsion();
    AbsoluteDate burn2Start = epoch.shiftedBy(plan.dt1() + plan.dtCoast());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn2Start,
            plan.dt2(),
            propulsion2.thrust(),
            propulsion2.isp(),
            attitude,
            progradeTNW));
  }
}
