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
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
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

/**
 * Node-targeted plane trim (bilan 08 §3.5). The apogee circularization ({@link
 * AnalyticApogeeCircularizationStage}) rotates the orbital plane with an hours-long finite burn
 * whose out-of-plane component smears over a wide arc, leaving a ~0.25° residual that is
 * geometrically uncorrectable away from a node. This stage cleans it up with a short out-of-plane
 * burn placed at a node, where a plane change is efficient and drift-free — bringing the residual
 * down toward ~0.10-0.15°.
 *
 * <p>The stage detects the next node itself (equatorial crossing, {@link NodeDetector}) and centers
 * the burn on it, so it does not depend on a preceding coast. The burn is a <em>pure</em> plane
 * rotation — it preserves the speed at the node, so it changes neither the orbit's energy nor its
 * shape, only the plane. For an equatorial target ({@code targetInclination = 0}) the equatorial
 * node is exactly the node of the plane change; a non-zero target inclination is handled
 * approximately (the equatorial node approximates the target-plane node for small tilts).
 */
public class AnalyticPlaneTrimAtNodeStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticPlaneTrimAtNodeStage.class);
  /** Skip the burn below this residual plane error (rad): already good enough. */
  private static final double SKIP_PLANE_ERROR_RAD = FastMath.toRadians(0.03);
  /** Skip the burn below this ΔV (m/s): not worth firing the motor. */
  private static final double SKIP_DV_THRESHOLD = 0.5;

  private final double targetInclination;

  /**
   * @param name human-readable stage name
   * @param targetInclination target orbital plane inclination (rad); 0 for an equatorial GEO
   */
  public AnalyticPlaneTrimAtNodeStage(String name, double targetInclination) {
    super(name);
    this.targetInclination = targetInclination;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    PlaneTrim plan = computePlaneTrim(state, mission.getVehicle());

    if (plan == null) {
      this.configuredEndDate = state.getDate();
      propagator.addEventDetector(
          new DateDetector(state.getDate().shiftedBy(1.0e-3))
              .withHandler(
                  (s, detector, increasing) -> {
                    mission.transitionToNextStage(s);
                    return Action.STOP;
                  }));
      return;
    }

    addBurn(propagator, state, plan, mission.getVehicle());

    AbsoluteDate endDate = plan.burnStart().shiftedBy(plan.dt());
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
    PlaneTrim plan = computePlaneTrim(currentState, mission.getVehicle());
    if (plan == null) {
      return currentState;
    }

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    addBurn(propagator, currentState, plan, mission.getVehicle());
    return propagator.propagate(plan.burnStart().shiftedBy(plan.dt()));
  }

  private record PlaneTrim(
      AbsoluteDate burnStart, double dt, Vector3D directionInertial, double dv) {}

  private PlaneTrim computePlaneTrim(SpacecraftState state, Vehicle vehicle) {
    SpacecraftState stateAtNode = detectStateAtNode(state);
    if (stateAtNode == null) {
      logger.info("Plane trim: no node found within one period, skipping.");
      return null;
    }

    Vector3D rNode = stateAtNode.getPVCoordinates().getPosition();
    Vector3D vNode = stateAtNode.getPVCoordinates().getVelocity();

    Vector3D wCurrent = Vector3D.crossProduct(rNode, vNode).normalize();
    Vector3D nIdeal = targetPlaneNormal(wCurrent, rNode);
    double planeError = Vector3D.angle(wCurrent, nIdeal);
    if (planeError < SKIP_PLANE_ERROR_RAD) {
      logger.info(
          "Plane trim: residual plane error {}° below threshold, skipping.",
          FastMath.toDegrees(planeError));
      return null;
    }

    // Pure plane change: keep the node speed, rotate the velocity into the target plane. The
    // target-plane prograde direction at the node is nIdeal × rNode (sign-matched to the current
    // velocity). Because the magnitude is unchanged, the burn touches neither energy nor shape.
    double vMag = vNode.getNorm();
    Vector3D vTargetDir = Vector3D.crossProduct(nIdeal, rNode).normalize();
    if (vTargetDir.dotProduct(vNode) < 0) {
      vTargetDir = vTargetDir.negate();
    }
    Vector3D vTarget = vTargetDir.scalarMultiply(vMag);
    Vector3D deltaV = vTarget.subtract(vNode);
    double dv = deltaV.getNorm();
    if (dv < SKIP_DV_THRESHOLD) {
      logger.info("Plane trim: ΔV {} m/s below threshold, skipping.", dv);
      return null;
    }

    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(stateAtNode.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    double dt =
        Physics.computeBurnDurationCapped(
            dv,
            stateAtNode.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            stageInfo.remainingFuel(stateAtNode.getMass()));

    // Center the short burn on the node passage (same rationale as the trim/circularization).
    AbsoluteDate burnStart = stateAtNode.getDate().shiftedBy(-dt / 2.0);
    if (burnStart.isBefore(state.getDate())) {
      burnStart = state.getDate().shiftedBy(1.0e-3);
    }

    logger.info(
        "Plane trim at node: plane error {}° -> ΔV {} m/s, dt {}s",
        FastMath.toDegrees(planeError),
        FastMath.round(dv),
        FastMath.round(dt));

    return new PlaneTrim(burnStart, dt, deltaV.normalize(), dv);
  }

  /**
   * Coasts to the next node (equatorial crossing) and returns the state there, or {@code null} if
   * none is found within a little over one period. Burn-free, so it steps at the large coast cap.
   */
  private static SpacecraftState detectStateAtNode(SpacecraftState state) {
    NumericalPropagator coastPropagator =
        OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
    coastPropagator.setInitialState(state);

    RecordAndContinue recorder = new RecordAndContinue();
    coastPropagator.addEventDetector(
        new NodeDetector(OrekitService.get().gcrf()).withHandler(recorder));

    double period = state.getOrbit().getKeplerianPeriod();
    coastPropagator.propagate(state.getDate().shiftedBy(period * 1.1));

    double minDt = 1.0; // skip a node we may already be sitting on
    for (RecordAndContinue.Event event : recorder.getEvents()) {
      double dt = event.getState().getDate().durationFrom(state.getDate());
      if (dt > minDt) {
        return event.getState();
      }
    }
    return null;
  }

  /**
   * Ideal target-plane normal: zHat rotated toward the current plane by the target inclination
   * (same convention as {@link AnalyticApogeeCircularizationStage} and {@link
   * AnalyticHohmannTransferStage#computeTargetVelocityAtApogee}).
   */
  private Vector3D targetPlaneNormal(Vector3D wCurrent, Vector3D rReference) {
    Vector3D zHat = Vector3D.PLUS_K;
    if (targetInclination < 1e-10) {
      return zHat;
    }
    Vector3D rotAxis = Vector3D.crossProduct(zHat, wCurrent);
    rotAxis = rotAxis.getNorm() < 1e-10 ? rReference.normalize() : rotAxis.normalize();
    return new Rotation(rotAxis, targetInclination, RotationConvention.VECTOR_OPERATOR)
        .applyTo(zHat);
  }

  private void addBurn(
      NumericalPropagator propagator, SpacecraftState state, PlaneTrim plan, Vehicle vehicle) {
    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(state.getMass());
    DepletionGuard.arm(propagator, stageInfo.depletionFloor(), getName());

    Rotation inertialToBody = new Rotation(plan.directionInertial(), Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, state.getFrame());
    PropulsionSystem propulsion = stageInfo.propulsion();
    propagator.addForceModel(
        new ConstantThrustManeuver(
            plan.burnStart(),
            plan.dt(),
            propulsion.thrust(),
            propulsion.isp(),
            attitude,
            Vector3D.PLUS_I));
  }
}
