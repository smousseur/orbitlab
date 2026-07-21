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
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Deterministic single-burn trim stage at the next apogee. Used after {@link
 * AnalyticHohmannTransferStage} to absorb the residual shape and inclination error left by the
 * analytical Hohmann transfer — driven mainly by un-modelled J3+ zonal harmonics and finite-burn
 * losses.
 *
 * <p>The burn is computed from the spacecraft state at the next apogee (detected via {@link
 * ApsideDetector} using the same gravity model as the main propagation):
 *
 * <ul>
 *   <li>ΔV vector = (target-shape velocity at the achieved apogee radius, target inclination plane)
 *       − v(apogee). The target shape is the ellipse (target perigee, achieved apogee); when the
 *       LEO target is circular, this is exactly the circularization velocity.
 *   <li>If {@code ||ΔV|| < SKIP_DV_THRESHOLD} the burn is skipped and the stage transitions
 *       immediately.
 *   <li>Otherwise the finite burn is scheduled centered on the impulsive apogee using a {@link
 *       FrameAlignedProvider} so the thrust direction stays constant in inertial throughout the
 *       burn (same rationale as burn 2 of the Hohmann transfer).
 * </ul>
 *
 * <p>The resulting orbit has the requested perigee and the achieved apogee (not necessarily the
 * requested apogee) in the target-inclination plane. With a J2-aware Hohmann upstream the apogee
 * radius is already close to the target.
 */
public class AnalyticTrimBurnStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(AnalyticTrimBurnStage.class);
  private static final double SKIP_DV_THRESHOLD = 1.0; // m/s
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  private final double targetPerigeeAltitude;
  private final double targetInclination;

  /**
   * @param name human-readable stage name
   * @param targetPerigeeAltitude target perigee altitude (m, above the Earth surface); equals the
   *     target apogee altitude for a circular orbit
   * @param targetInclination target orbital plane inclination (rad); 0 for an equatorial GEO
   */
  public AnalyticTrimBurnStage(
      String name, double targetPerigeeAltitude, double targetInclination) {
    super(name);
    this.targetPerigeeAltitude = targetPerigeeAltitude;
    this.targetInclination = targetInclination;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    TrimBurn plan = computeTrimBurn(state, mission.getVehicle());

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
    TrimBurn plan = computeTrimBurn(currentState, mission.getVehicle());
    if (plan == null) {
      return currentState;
    }

    NumericalPropagator propagator =
        OrekitService.get().createSimplePropagator(burnLimitedMaxStep(currentState, mission.getVehicle()));
    propagator.setInitialState(currentState);
    addBurn(propagator, currentState, plan, mission.getVehicle());
    return propagator.propagate(plan.burnStart().shiftedBy(plan.dt()));
  }

  private record TrimBurn(
      AbsoluteDate burnStart, double dt, Vector3D directionInertial, double dv) {}

  private TrimBurn computeTrimBurn(SpacecraftState state, Vehicle vehicle) {
    SpacecraftState stateAtApogee = detectStateAtApogee(state);
    if (stateAtApogee == null) {
      logger.info("Trim burn: no apogee detected within one period, skipping.");
      return null;
    }

    double mu = stateAtApogee.getOrbit().getMu();
    Vector3D rApo = stateAtApogee.getPVCoordinates().getPosition();
    Vector3D vCurrentApo = stateAtApogee.getPVCoordinates().getVelocity();
    double r2 = rApo.getNorm();
    double rPerigeeTarget = EARTH_RADIUS + targetPerigeeAltitude;

    Vector3D vTarget =
        AnalyticHohmannTransferStage.computeTargetVelocityAtApogee(
            rApo, vCurrentApo, mu, rPerigeeTarget, r2, targetInclination);
    Vector3D deltaV = vTarget.subtract(vCurrentApo);
    double dv = deltaV.getNorm();

    if (dv < SKIP_DV_THRESHOLD) {
      logger.info("Trim burn: residual ΔV={} m/s below threshold, skipping.", dv);
      return null;
    }

    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(stateAtApogee.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    double dt =
        Physics.computeBurnDurationCapped(
            dv,
            stateAtApogee.getMass(),
            propulsion.isp(),
            propulsion.thrust(),
            stageInfo.remainingFuel(stateAtApogee.getMass()));

    AbsoluteDate burnStart = stateAtApogee.getDate().shiftedBy(-dt / 2.0);
    if (burnStart.isBefore(state.getDate())) {
      burnStart = state.getDate().shiftedBy(1.0e-3);
    }

    logger.info(
        "Trim burn plan: dv={} m/s, dt={}s, apogee altitude {} km",
        dv,
        dt,
        (r2 - Constants.WGS84_EARTH_EQUATORIAL_RADIUS) / 1000.0);

    return new TrimBurn(burnStart, dt, deltaV.normalize(), dv);
  }

  /**
   * Detects the next apogee after {@code state} using a propagator with J2+ (same gravity model as
   * the optimization path). Returns the spacecraft state recorded at the apogee event, or null on
   * failure. Pattern matches {@code CircularizationBurnResolver#detectTimeToApoapsis}.
   *
   * <p>Package-private so {@link AnalyticApogeeCircularizationStage} reuses the same detection.
   */
  static SpacecraftState detectStateAtApogee(SpacecraftState state) {
    // Burn-free coast: nothing ignites, so step at the large coast cap (the apogee found is set by
    // the detector's root-finder + dense output, not by the integration step). See bilan 08 §3.1.
    NumericalPropagator coastPropagator =
        OrekitService.get().createOptimizationPropagator(OrekitService.COAST_MAX_STEP);
    coastPropagator.setInitialState(state);

    RecordAndContinue recorder = new RecordAndContinue();
    ApsideDetector apsideDetector = new ApsideDetector(state.getOrbit()).withHandler(recorder);
    coastPropagator.addEventDetector(apsideDetector);

    double period = state.getOrbit().getKeplerianPeriod();
    coastPropagator.propagate(state.getDate().shiftedBy(period * 1.1));

    double minDt = 1.0; // skip the immediate apsis if we're already at one
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

  private static void addBurn(
      NumericalPropagator propagator, SpacecraftState state, TrimBurn plan, Vehicle vehicle) {
    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();
    DepletionGuard.arm(propagator, stageInfo.depletionFloor(), "Trim burn");

    // FrameAlignedProvider maps inertial → body. We want body PLUS_I to point along the inertial
    // burn direction, i.e. r(directionInertial) = PLUS_I.
    Rotation inertialToBody = new Rotation(plan.directionInertial(), Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, state.getFrame());
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
