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
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.frames.LOFType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

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
 * <p>Accepted approximations (v1):
 *
 * <ul>
 *   <li>Burn 1 starts at the node (not centered on it), so the resulting apogee slips a fraction of
 *       a degree from the antipodal node.
 *   <li>Burn 2 is centered on the apogee via {@code dtCoast = halfPeriod − dt2/2}.
 *   <li>J2 nodal regression during the half-period coast is not compensated (effect is a few
 *       arcminutes of plane drift on GTO-scale coasts).
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
      Vector3D burn1DirectionTNW,
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
    double v1Mag = v1.getNorm();

    // ── Burn 1: prograde Hohmann at the node ──
    double r2 = EARTH_RADIUS + targetAltitude;
    double aTransfer = (r1Mag + r2) / 2.0;
    double vTransferPerigee = FastMath.sqrt(mu * (2.0 / r1Mag - 1.0 / aTransfer));
    double dv1 = vTransferPerigee - v1Mag;

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    double dt1 =
        Physics.computeBurnDuration(dv1, state.getMass(), propulsion1.isp(), propulsion1.thrust());

    Vector3D burn1Direction = Physics.buildThrustDirectionTNW(0.0, 0.0);

    // ── Estimate the transfer ellipse from the impulsive Hohmann assumption ──
    Vector3D vAfterBurn1 = v1.normalize().scalarMultiply(vTransferPerigee);
    KeplerianOrbit transferOrbit =
        new KeplerianOrbit(
            new PVCoordinates(r1, vAfterBurn1), state.getFrame(), state.getDate(), mu);

    double transferPeriod =
        2.0 * FastMath.PI * FastMath.sqrt(aTransfer * aTransfer * aTransfer / mu);
    double dtCoastImpulsive = transferPeriod / 2.0;

    // ── Spacecraft state at apogee (analytical, ν = π) ──
    KeplerianOrbit orbitAtApogee =
        new KeplerianOrbit(
            transferOrbit.getA(),
            transferOrbit.getE(),
            transferOrbit.getI(),
            transferOrbit.getPerigeeArgument(),
            transferOrbit.getRightAscensionOfAscendingNode(),
            FastMath.PI,
            PositionAngleType.TRUE,
            transferOrbit.getFrame(),
            state.getDate().shiftedBy(dtCoastImpulsive),
            mu);
    Vector3D rApo = orbitAtApogee.getPVCoordinates().getPosition();
    Vector3D vCurrentApo = orbitAtApogee.getPVCoordinates().getVelocity();

    // ── Target velocity at apogee (target plane, circular speed, prograde) ──
    Vector3D vTargetApo = computeTargetVelocityAtApogee(rApo, vCurrentApo, mu, r2);

    Vector3D deltaV2 = vTargetApo.subtract(vCurrentApo);
    double dv2 = deltaV2.getNorm();

    double g0Ve = propulsion1.isp() * Constants.G0_STANDARD_GRAVITY;
    double massAfterBurn1 = state.getMass() * FastMath.exp(-dv1 / g0Ve);

    ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
    PropulsionSystem propulsion2 = stage2.propulsion();
    double dt2 =
        Physics.computeBurnDuration(dv2, massAfterBurn1, propulsion2.isp(), propulsion2.thrust());

    // ── Burn 2 direction in inertial frame ──
    // Applying the ΔV₂ as a constant LOF-TNW direction would let the local frame rotate with the
    // orbital plane *during* the finite burn — which is exactly the rotation the W component is
    // trying to induce. The result is that the plane change comes out short (observed: 1.88°
    // residual on a 5.3° target). Apply it as a constant inertial direction instead so the plane
    // change effectively accumulates against a fixed reference.
    Vector3D burn2DirectionInertial = deltaV2.normalize();

    // Center burn 2 on the apogee passage.
    double dtCoast = FastMath.max(0.0, dtCoastImpulsive - dt2 / 2.0);
    double totalDuration = dt1 + dtCoast + dt2;

    logger.info(
        "Analytic Hohmann plan: dv1={} m/s, dt1={}s, dtCoast={}s, dv2={} m/s, dt2={}s",
        dv1,
        dt1,
        dtCoast,
        dv2,
        dt2);

    return new AnalyticBurnPlan(
        dt1, burn1Direction, dtCoast, dt2, burn2DirectionInertial, totalDuration, dv1, dv2);
  }

  /**
   * Computes the target velocity vector at apogee for a circular orbit of radius {@code r2} and
   * inclination {@code targetInclination}, prograde, with a target plane normal chosen to minimize
   * the wedge angle from the current orbit plane (purely a sign convention; the Δv magnitude is
   * unaffected by other plane choices that satisfy the inclination constraint and prograde sense).
   */
  private Vector3D computeTargetVelocityAtApogee(
      Vector3D rApo, Vector3D vCurrentApo, double mu, double r2) {
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
      Rotation rot =
          new Rotation(rotAxis, targetInclination, RotationConvention.VECTOR_OPERATOR);
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
    LofOffset lofAttitude = new LofOffset(state.getFrame(), LOFType.TNW);

    ActiveStageInfo stage1 = vehicle.resolveActiveStage(state.getMass());
    PropulsionSystem propulsion1 = stage1.propulsion();
    AbsoluteDate burn1Start = epoch.shiftedBy(1.0e-3);
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn1Start,
            plan.dt1(),
            propulsion1.thrust(),
            propulsion1.isp(),
            lofAttitude,
            plan.burn1DirectionTNW()));

    double massAfterBurn1 =
        state.getMass()
            * FastMath.exp(-plan.dv1() / (propulsion1.isp() * Constants.G0_STANDARD_GRAVITY));
    ActiveStageInfo stage2 = vehicle.resolveActiveStage(massAfterBurn1);
    PropulsionSystem propulsion2 = stage2.propulsion();
    AbsoluteDate burn2Start = epoch.shiftedBy(plan.dt1() + plan.dtCoast());
    // Burn 2 uses a frame-aligned attitude so the thrust direction stays constant in inertial
    // throughout the finite burn — this is what makes the combined circularization + plane change
    // converge to the impulsive target instead of losing authority to LOF rotation.
    // FrameAlignedProvider's rotation maps inertial → body. We want body PLUS_I to point along
    // burn2DirectionInertial in inertial, i.e. r(burn2DirectionInertial) = PLUS_I.
    Rotation inertialToBody = new Rotation(plan.burn2DirectionInertial(), Vector3D.PLUS_I);
    FrameAlignedProvider inertialAttitude =
        new FrameAlignedProvider(inertialToBody, state.getFrame());
    propagator.addForceModel(
        new ConstantThrustManeuver(
            burn2Start,
            plan.dt2(),
            propulsion2.thrust(),
            propulsion2.isp(),
            inertialAttitude,
            Vector3D.PLUS_I));
  }
}
