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
    double bias = Double.NaN;
    boolean fuelCapped = false;
    Vector3D deltaV1 = Vector3D.ZERO;
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
      SpacecraftState stateAtApogee =
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
