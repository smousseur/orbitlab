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
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Apogee circularization + plane change executed by a low-thrust kick motor (spec 06 I5). The
 * burn is centered on the detected apogee, but an hours-long finite burn still inflates the
 * apogee while it executes — and a subsequent apogee trim can only set the opposite side of the
 * orbit, so that drift would be locked in. The plan therefore iterates (secant) on a <em>scale of
 * the target velocity</em>: simulate the centered finite burn, measure the post-burn osculating
 * apogee, and shave the target speed until it lands on the target radius — the energy is the only
 * knob with real authority over the far apside. A vector feedback on the aimed plane normal
 * simultaneously absorbs the plane smear of the long arc, whatever its direction. The residual
 * perigee deficit is left to the downstream trim stage, whose short burn drifts negligibly.
 */
public class AnalyticApogeeCircularizationStage extends MissionStage {
  private static final Logger logger =
      LogManager.getLogger(AnalyticApogeeCircularizationStage.class);
  private static final double EARTH_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  /**
   * Convergence threshold on the post-burn apogee radius (m). Loose on purpose: the empirical
   * apogee-vs-β slope is ~10× weaker than the impulsive estimate, so a tight threshold makes the
   * secant crawl and β drift far (each extra % of β digs the perigee ~1 000 km deeper for the
   * trim to refill). 5 km is well inside the mission tolerance.
   */
  private static final double APOGEE_BIAS_THRESHOLD = 5_000.0;

  private final double targetAltitude;
  private final double targetInclination;

  /**
   * Creates an apogee circularization stage.
   *
   * @param name the human-readable name of this stage
   * @param targetAltitude the target circular orbit altitude (m)
   * @param targetInclination the target orbital plane inclination (rad)
   */
  public AnalyticApogeeCircularizationStage(
      String name, double targetAltitude, double targetInclination) {
    super(name);
    this.targetAltitude = targetAltitude;
    this.targetInclination = targetInclination;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    CircularizationPlan plan = computePlan(state, mission.getVehicle());

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
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    CircularizationPlan plan = computePlan(currentState, mission.getVehicle());

    // Same gravity model as the plan simulation and the ephemeris generation: the hours-long
    // burn is planned against its own finite-burn drift, a Newtonian standalone flight would
    // diverge from that plan by tens of km.
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(currentState);
    addBurn(propagator, currentState, plan, mission.getVehicle());
    return propagator.propagate(plan.burnStart().shiftedBy(plan.dt()));
  }

  private record CircularizationPlan(
      AbsoluteDate burnStart, double dt, Vector3D directionInertial, double dv) {}

  private CircularizationPlan computePlan(SpacecraftState state, Vehicle vehicle) {
    SpacecraftState stateAtApogee = AnalyticTrimBurnStage.detectStateAtApogee(state);
    if (stateAtApogee == null) {
      throw new IllegalStateException("No apogee found for the circularization burn");
    }

    double mu = stateAtApogee.getOrbit().getMu();
    Vector3D rApo = stateAtApogee.getPVCoordinates().getPosition();
    Vector3D vCurrentApo = stateAtApogee.getPVCoordinates().getVelocity();
    double rApoMag = rApo.getNorm();
    double rTarget = EARTH_RADIUS + targetAltitude;

    ActiveStageInfo stageInfo = vehicle.resolveActiveStage(stateAtApogee.getMass());
    PropulsionSystem propulsion = stageInfo.propulsion();

    // The finite burn smears both targets while it executes: the apogee inflates (energy added
    // off-apogee) and the plane rotation delivered over a ~40° arc misses the aimed plane — as a
    // VECTOR, about an arbitrary axis (RAAN twist included), so no scalar inclination aim can
    // cancel it. Both are compensated by simulate-and-correct on the SAME simulation: a secant
    // on the target velocity scale (β) lands the post-burn apogee on target — the energy is the
    // only knob with real authority over the far apside — and a vector feedback on the aimed
    // plane normal (rotate the aim by the rotation taking the measured post-burn normal onto the
    // ideal one) absorbs the plane smear whatever its direction. The residual perigee deficit
    // goes to the trim stage, whose short burn does not drift.
    double vMagRef = FastMath.sqrt(mu / rTarget);
    double impulsiveSlope = -2.0 * rTarget * rTarget * vMagRef * vMagRef / mu;

    // Ideal target plane normal: zHat rotated toward the current plane by the target inclination
    // (same convention as AnalyticHohmannTransferStage.computeTargetVelocityAtApogee).
    Vector3D zHat = Vector3D.PLUS_K;
    Vector3D wCurrent = Vector3D.crossProduct(rApo, vCurrentApo).normalize();
    Vector3D nIdeal;
    if (targetInclination < 1e-10) {
      nIdeal = zHat;
    } else {
      Vector3D rotAxis = Vector3D.crossProduct(zHat, wCurrent);
      rotAxis = rotAxis.getNorm() < 1e-10 ? rApo.normalize() : rotAxis.normalize();
      nIdeal =
          new Rotation(rotAxis, targetInclination, RotationConvention.VECTOR_OPERATOR)
              .applyTo(zHat);
    }

    double aTransfer = 0.5 * (rTarget + rApoMag);
    double vMagPlan = FastMath.sqrt(mu * (2.0 / rApoMag - 1.0 / aTransfer));

    Vector3D deltaV = Vector3D.ZERO;
    double dv = 0.0;
    double dt = 0.0;
    AbsoluteDate burnStart = stateAtApogee.getDate();
    double beta = 0.0;
    double betaPrev = 0.0;
    double biasPrev = Double.NaN;
    double flownBeta = 0.0;
    Vector3D nAim = nIdeal;
    for (int iter = 0; iter < 6; iter++) {
      flownBeta = beta;
      Vector3D vTargetDir = Vector3D.crossProduct(nAim, rApo).normalize();
      if (vTargetDir.dotProduct(vCurrentApo) < 0) {
        vTargetDir = vTargetDir.negate();
      }
      Vector3D vTarget = vTargetDir.scalarMultiply(vMagPlan * (1.0 - beta));
      deltaV = vTarget.subtract(vCurrentApo);
      dv = deltaV.getNorm();
      dt =
          Physics.computeBurnDurationCapped(
              dv,
              stateAtApogee.getMass(),
              propulsion.isp(),
              propulsion.thrust(),
              stageInfo.remainingFuel(stateAtApogee.getMass()));
      burnStart = stateAtApogee.getDate().shiftedBy(-dt / 2.0);
      if (burnStart.isBefore(state.getDate())) {
        burnStart = state.getDate().shiftedBy(1.0e-3);
      }

      SpacecraftState endState =
          simulateCenteredBurn(
              state, deltaV.normalize(), burnStart, dt, propulsion.thrust(), propulsion.isp());
      KeplerianOrbit postBurn =
          new KeplerianOrbit(
              endState.getPVCoordinates(), endState.getFrame(), endState.getDate(), mu);
      double apogeePost = postBurn.getA() * (1.0 + postBurn.getE());
      double bias = rTarget - apogeePost;
      Vector3D wPost =
          Vector3D.crossProduct(
                  endState.getPVCoordinates().getPosition(),
                  endState.getPVCoordinates().getVelocity())
              .normalize();
      double planeError = Vector3D.angle(wPost, nIdeal);
      logger.info(
          "Circularization iter {}: beta={}, dv={} m/s, dt={}s, post-burn apogee alt={} km"
              + " (bias {} km), inclination {}°, plane error {}°",
          iter,
          beta,
          FastMath.round(dv),
          FastMath.round(dt),
          FastMath.round((apogeePost - EARTH_RADIUS) / 1000.0),
          FastMath.round(bias / 1000.0),
          FastMath.toDegrees(postBurn.getI()),
          FastMath.toDegrees(planeError));
      // Apogee-only stop criterion. The plane residual (~0.25°) is a physical floor of a single
      // hours-long burn: at the burn point the plane can only rotate about the radius vector, the
      // orthogonal smear component is only correctable at a NODE (future node-targeted plane
      // trim). The vector feedback below recovers what little is reachable (~0.03°); demanding
      // more would just spin the loop and drift β.
      if (FastMath.abs(bias) < APOGEE_BIAS_THRESHOLD) {
        break;
      }

      double nextBeta;
      if (Double.isNaN(biasPrev) || FastMath.abs(bias - biasPrev) < 1.0) {
        nextBeta = beta + bias / impulsiveSlope;
      } else {
        nextBeta = beta - bias * (beta - betaPrev) / (bias - biasPrev);
      }
      betaPrev = beta;
      biasPrev = bias;
      beta = FastMath.max(-0.05, FastMath.min(0.05, nextBeta));
      // Vector feedback: rotate the aimed normal by the rotation taking the measured post-burn
      // normal onto the ideal one (gain 1 — the smear is nearly constant between iterations).
      nAim = new Rotation(wPost, nIdeal).applyTo(nAim);
    }

    logger.info(
        "Circularization plan: dv={} m/s, dt={}s, velocity scale beta={}, aim tilt {}°",
        dv,
        dt,
        flownBeta,
        FastMath.toDegrees(Vector3D.angle(nAim, nIdeal)));

    return new CircularizationPlan(burnStart, dt, deltaV.normalize(), dv);
  }

  /** Mirrors the centered finite burn the mission will fly and returns the state at burn end. */
  private static SpacecraftState simulateCenteredBurn(
      SpacecraftState state,
      Vector3D directionInertial,
      AbsoluteDate burnStart,
      double dt,
      double thrust,
      double isp) {
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(state);
    Rotation inertialToBody = new Rotation(directionInertial, Vector3D.PLUS_I);
    FrameAlignedProvider attitude = new FrameAlignedProvider(inertialToBody, state.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(burnStart, dt, thrust, isp, attitude, Vector3D.PLUS_I));
    return propagator.propagate(burnStart.shiftedBy(dt));
  }

  private void addBurn(
      NumericalPropagator propagator,
      SpacecraftState state,
      CircularizationPlan plan,
      Vehicle vehicle) {
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
