package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
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
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.handlers.RecordAndContinue;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Deterministic two-burn Hohmann transfer from a circular orbit to a target circular orbit at a
 * specified altitude and inclination. No optimization variables — all burn parameters are computed
 * in closed form from the entry state and the target.
 *
 * <p>Geometry assumption: the entry state lies at an equatorial node of the parking orbit. This is
 * normally guaranteed by chaining this stage after a {@link CoastingStage} configured with {@code
 * stopAtNode = true}. Under this assumption:
 *
 * <ul>
 *   <li>Burn 1 is a pure prograde Hohmann burn; the perigee of the transfer ellipse sits at the
 *       node.
 *   <li>The transfer ellipse retains the parking-orbit plane; its apogee sits at the antipodal
 *       node, also on the equator.
 *   <li>Burn 2 is a single vector burn at apogee that combines circularization at the target
 *       altitude and the plane change to the target inclination, in inertial-frame components
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

  private final double targetAltitude;
  private final double targetInclination;

  /**
   * @param name human-readable stage name
   * @param targetAltitude target circular orbit altitude (m, above the Earth surface)
   * @param targetInclination target orbital plane inclination (rad); 0 for an equatorial GEO
   */
  public AnalyticHohmannTransferStage(
      String name, double targetAltitude, double targetInclination) {
    super(name);
    this.targetAltitude = targetAltitude;
    this.targetInclination = targetInclination;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState state = mission.getCurrentState();
    AnalyticBurnPlan plan = computeBurnPlan(state, mission.getVehicle());

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
    AnalyticBurnPlan plan = computeBurnPlan(currentState, mission.getVehicle());

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(currentState);
    addBurns(propagator, currentState, plan, mission.getVehicle());

    return propagator.propagate(currentState.getDate().shiftedBy(plan.totalDuration()));
  }

  // ════════════════════════════════════════════════════════════════════════
  // Analytical computation
  // ════════════════════════════════════════════════════════════════════════

  private record AnalyticBurnPlan(
      double dt1,
      Vector3D burn1DirectionInertial,
      double dtCoast,
      double dt2,
      Vector3D burn2DirectionInertial,
      double totalDuration,
      double dv1,
      double dv2) {}

  private AnalyticBurnPlan computeBurnPlan(SpacecraftState state, Vehicle vehicle) {
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
    double r2 = EARTH_RADIUS + targetAltitude;
    Vector3D tHat = vTangential1.normalize();
    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    double g0Ve = propulsion1.isp() * Constants.G0_STANDARD_GRAVITY;

    double r2Aim = r2;
    double dv1 = 0.0;
    double dt1 = 0.0;
    Vector3D deltaV1 = Vector3D.ZERO;
    Vector3D vAfterBurn1 = Vector3D.ZERO;
    SpacecraftState stateAtApogee = null;
    for (int iter = 0; iter < 4; iter++) {
      double aTransfer = (r1Mag + r2Aim) / 2.0;
      double vTransferPerigee = FastMath.sqrt(mu * (2.0 / r1Mag - 1.0 / aTransfer));
      vAfterBurn1 = tHat.scalarMultiply(vTransferPerigee);
      deltaV1 = vAfterBurn1.subtract(v1);
      dv1 = deltaV1.getNorm();
      dt1 =
          Physics.computeBurnDuration(
              dv1, state.getMass(), propulsion1.isp(), propulsion1.thrust());
      double transferHalfPeriod =
          FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);
      stateAtApogee =
          simulateBurn1AndFindApogee(
              state,
              deltaV1.normalize(),
              dt1,
              propulsion1.thrust(),
              propulsion1.isp(),
              transferHalfPeriod);
      double rApoActual = stateAtApogee.getPVCoordinates().getPosition().getNorm();
      double bias = r2 - rApoActual;
      r2Aim += bias;
      if (FastMath.abs(bias) < 100.0) {
        break;
      }
    }
    Vector3D burn1DirectionInertial = deltaV1.normalize();
    double massAfterBurn1 = state.getMass() * FastMath.exp(-dv1 / g0Ve);

    Vector3D rApo = stateAtApogee.getPVCoordinates().getPosition();
    Vector3D vCurrentApo = stateAtApogee.getPVCoordinates().getVelocity();

    // ── Target velocity at apogee: circular at r2_target (not |rApo|) in target plane ──
    // After the Newton iteration r2Aim is such that simulated |rApo| equals r2_target to within
    // ~100 m, so targeting circular at r2_target (rather than |rApo|) is essentially equivalent
    // and removes the residual O(100 m) misalignment between the two radii.
    double rApoMag = rApo.getNorm();
    Vector3D vTargetApo =
        computeTargetVelocityAtApogee(rApo, vCurrentApo, mu, r2, targetInclination);

    Vector3D deltaV2 = vTargetApo.subtract(vCurrentApo);
    double dv2 = deltaV2.getNorm();

    ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
    PropulsionSystem propulsion2 = stage2.propulsion();
    double dt2 =
        Physics.computeBurnDuration(dv2, massAfterBurn1, propulsion2.isp(), propulsion2.thrust());
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
        dt1, burn1DirectionInertial, dtCoast, dt2, burn2DirectionInertial, totalDuration, dv1, dv2);
  }

  /**
   * Mirrors the burn 1 force model the mission will use (constant inertial thrust over {@code
   * dt1}), then coasts under the full gravity model to the next apogee. Returns the spacecraft
   * state at that apogee.
   */
  private static SpacecraftState simulateBurn1AndFindApogee(
      SpacecraftState state,
      Vector3D burn1DirectionInertial,
      double dt1,
      double thrust,
      double isp,
      double transferHalfPeriod) {
    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(state);

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
    throw new IllegalStateException("No apogee found within one transfer half-period.");
  }

  /**
   * Computes the target velocity vector at apogee for a circular orbit of radius {@code r2} and
   * inclination {@code targetInclination}, prograde, with a target plane normal chosen to minimize
   * the wedge angle from the current orbit plane (purely a sign convention; the Δv magnitude is
   * unaffected by other plane choices that satisfy the inclination constraint and prograde sense).
   *
   * <p>Package-private so it can be reused by trim-burn stages computing a circularization +
   * plane-change burn against an in-flight state.
   */
  static Vector3D computeTargetVelocityAtApogee(
      Vector3D rApo, Vector3D vCurrentApo, double mu, double r2, double targetInclination) {
    double vCirc = FastMath.sqrt(mu / r2);

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
    return vTargetDir.scalarMultiply(vCirc);
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
    AbsoluteDate burn1Start = epoch.shiftedBy(1.0e-3);
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

    double massAfterBurn1 =
        state.getMass()
            * FastMath.exp(-plan.dv1() / (propulsion1.isp() * Constants.G0_STANDARD_GRAVITY));
    ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
    PropulsionSystem propulsion2 = stage2.propulsion();
    AbsoluteDate burn2Start = epoch.shiftedBy(plan.dt1() + plan.dtCoast());
    // Burn 2 uses a frame-aligned attitude so the thrust direction stays constant in inertial
    // throughout the finite burn — this is what makes the combined circularization + plane change
    // converge to the impulsive target instead of losing authority to LOF rotation.
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn2Start,
            plan.dt2(),
            propulsion2.thrust(),
            propulsion2.isp(),
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
