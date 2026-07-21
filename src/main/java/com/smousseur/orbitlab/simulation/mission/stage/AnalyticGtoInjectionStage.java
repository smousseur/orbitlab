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
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Finite-burn perigee injection raising the apogee to a target altitude (parking orbit → GTO).
 * The plan reuses the Hohmann stage's Newton iteration on the aimed apogee radius so the
 * finite-burn apogee lands on target despite steering and gravity losses. The stage ends at burn
 * cutoff: the spent upper stage separates right after ({@link StageSeparationStage}) and the
 * payload's kick motor performs the apogee circularization (spec 06 I5).
 */
public class AnalyticGtoInjectionStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticGtoInjectionStage.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

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

    AbsoluteDate endDate = state.getDate().shiftedBy(1.0e-3 + plan.dt1());
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

    NumericalPropagator propagator =
        OrekitService.get().createSimplePropagator(burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    addBurn(propagator, currentState, plan, mission.getVehicle());

    return propagator.propagate(currentState.getDate().shiftedBy(1.0e-3 + plan.dt1()));
  }

  private record InjectionPlan(double dt1, Vector3D burnDirectionInertial, double dv1) {}

  private InjectionPlan computePlan(SpacecraftState state, Vehicle vehicle) {
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
    Vector3D deltaV1 = Vector3D.ZERO;
    for (int iter = 0; iter < 4; iter++) {
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
      SpacecraftState stateAtApogee =
          AnalyticHohmannTransferStage.simulateBurn1AndFindApogee(
              state,
              deltaV1.normalize(),
              dt1,
              propulsion1.thrust(),
              propulsion1.isp(),
              transferHalfPeriod,
              maxStep);
      double bias = r2 - stateAtApogee.getPVCoordinates().getPosition().getNorm();
      r2Aim += bias;
      if (FastMath.abs(bias) < 100.0) {
        break;
      }
    }

    logger.info(
        "GTO injection plan: dv1={} m/s, dt1={}s, aimed apogee alt={} km",
        dv1,
        dt1,
        (r2Aim - EARTH_RADIUS) / 1000.0);

    return new InjectionPlan(dt1, deltaV1.normalize(), dv1);
  }

  private void addBurn(
      NumericalPropagator propagator, SpacecraftState state, InjectionPlan plan, Vehicle vehicle) {
    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    DepletionGuard.arm(propagator, stage1.depletionFloor(), getName());

    // Frame-aligned inertial attitude: the ΔV vector carries a radial component cancelling the
    // parking-orbit residual eccentricity, so a pure-prograde TNW attitude would miss it.
    Rotation inertialToBody = new Rotation(plan.burnDirectionInertial(), Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, state.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            state.getDate().shiftedBy(1.0e-3),
            plan.dt1(),
            propulsion1.thrust(),
            propulsion1.isp(),
            attitude,
            Vector3D.PLUS_I));
  }
}
