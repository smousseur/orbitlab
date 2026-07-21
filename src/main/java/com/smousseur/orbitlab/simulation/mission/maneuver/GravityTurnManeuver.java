package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionStopTrigger;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.forces.maneuvers.Maneuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Encapsulates the gravity turn maneuver configuration logic. Shared between {@link
 * GravityTurnStage} (execution) and {@link
 * com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem} (optimization).
 *
 * <p>Stage resolution is automatic: the active stage and the next stage after jettison are
 * determined from the vehicle's reference mass via {@link Vehicle#resolveActiveStage(double)}.
 */
public class GravityTurnManeuver {

  private static final Logger logger = LogManager.getLogger(GravityTurnManeuver.class);

  private final Vehicle vehicle;
  private final double entryMass;
  private final double pitchKickAngleRad;
  private final double launchAzimuth;
  private final double interstageCoastDuration;
  private final ActiveStageInfo activeStage;
  private final ActiveStageInfo nextStage;
  // Stored per-thread so parallel CMA-ES exploration runs can call propagateForOptimization()
  // concurrently without overwriting each other's tracker (matches TransferProblem.lastResult).
  private final ThreadLocal<MinAltitudeTracker> lastAltitudeTracker = new ThreadLocal<>();

  /**
   * Creates a gravity turn maneuver for the given vehicle and launch parameters.
   *
   * @param vehicle the vehicle performing the maneuver (must have at least two stages)
   * @param entryMass the actual spacecraft mass at gravity turn entry (kg); already reflects any
   *     propellant burnt during the vertical ascent
   * @param pitchKickAngleRad the initial pitch kick angle in radians
   * @param launchAzimuth the launch azimuth angle in radians (measured from north)
   * @param interstageCoastDuration unpowered coast between jettison and next-stage ignition (s)
   */
  public GravityTurnManeuver(
      Vehicle vehicle,
      double entryMass,
      double pitchKickAngleRad,
      double launchAzimuth,
      double interstageCoastDuration) {
    this.vehicle = vehicle;
    this.entryMass = entryMass;
    this.pitchKickAngleRad = pitchKickAngleRad;
    this.launchAzimuth = launchAzimuth;
    this.interstageCoastDuration = interstageCoastDuration;
    this.activeStage = vehicle.resolveActiveStage(entryMass);
    this.nextStage = vehicle.resolveActiveStage(activeStage.massAfterJettison());
  }

  /**
   * Decoded physical parameters from the raw CMA-ES optimization variables.
   *
   * @param transitionTime total gravity turn transition duration (seconds)
   * @param exponent power-law exponent controlling pitch-over profile
   * @param burn1Duration duration of the first stage burn (seconds)
   * @param burn2Duration duration of the second stage burn after jettison (seconds)
   */
  public record GravityTurnParams(
      double transitionTime, double exponent, double burn1Duration, double burn2Duration) {}

  /**
   * Decodes raw CMA-ES optimization variables into physical gravity turn parameters. The burn
   * durations are derived from the propellant remaining at gravity turn entry.
   *
   * @param variables the raw optimization variable array (transitionTime, exponent)
   * @return the decoded physical parameters
   */
  public GravityTurnParams decode(double[] variables) {
    double transitionTime = variables[0];
    double exponent = variables[1];

    // Burn1 duration until propellant exhaustion
    double burn1Duration = getBurn1Duration();

    // Burn2 duration after jettison and interstage coast, until transitionTime
    double burn2Duration = transitionTime - burn1Duration - interstageCoastDuration;
    burn2Duration = FastMath.max(0.0, burn2Duration);

    return new GravityTurnParams(transitionTime, exponent, burn1Duration, burn2Duration);
  }

  /**
   * Applies the initial pitch kick to the spacecraft state, marking the entry into the gravity
   * turn. The kick rotates the velocity vector by the configured pitch angle along the launch
   * azimuth.
   *
   * @param state the spacecraft state before the pitch kick
   * @return the spacecraft state after the pitch kick has been applied
   */
  public SpacecraftState applyKick(SpacecraftState state) {
    return Physics.applyPitchKick(state, pitchKickAngleRad, launchAzimuth);
  }

  /**
   * Configures the given propagator with the gravity turn thrust maneuver and MECO event. This is
   * THE single source of truth for gravity turn configuration.
   *
   * @param propagator the propagator to configure
   * @param kickedState the state after pitch kick
   * @param params decoded physical parameters
   */
  public void configure(
      NumericalPropagator propagator, SpacecraftState kickedState, GravityTurnParams params) {
    AbsoluteDate kickDate = kickedState.getDate();

    GravityTurnAttitudeProvider attitudeProvider =
        new GravityTurnAttitudeProvider(kickDate, params.transitionTime(), params.exponent());
    propagator.setAttitudeProvider(attitudeProvider);

    // Burn 1 — active stage propulsion, flame-out semantics (spec 06 I4b): the engine thrusts
    // until stage 1's depletion floor instead of a date window, so the load can vary (outer
    // propellant-sizing loop) without recomputing the window. The analytic burn1Duration remains
    // the schedule prediction for the jettison and burn 2 dates below.
    PropulsionSystem propulsion1 = activeStage.propulsion();
    Maneuver burn1 =
        new Maneuver(
            null,
            new DepletionStopTrigger(kickDate.shiftedBy(1.0e-3), activeStage.depletionFloor()),
            new BasicConstantThrustPropulsionModel(
                propulsion1.thrust(), propulsion1.isp(), Vector3D.PLUS_I, "GT-burn1"));
    propagator.addForceModel(burn1);

    AbsoluteDate jettisonDate =
        kickDate.shiftedBy(1.0e-3).shiftedBy(params.burn1Duration).shiftedBy(1.0e-3);
    // Jettison — mass drops to the reference mass of all stages above
    double massAfterJettison = activeStage.massAfterJettison();
    DateDetector jettisonDetector =
        new DateDetector(jettisonDate)
            .withHandler(
                new EventHandler() {
                  @Override
                  public Action eventOccurred(
                      SpacecraftState s, EventDetector detector, boolean increasing) {
                    return Action.RESET_STATE;
                  }

                  @Override
                  public SpacecraftState resetState(
                      EventDetector detector, SpacecraftState oldState) {
                    return oldState.withMass(massAfterJettison);
                  }
                });
    propagator.addEventDetector(jettisonDetector);
    // Burn 2 — next stage propulsion (after jettison and interstage coast)
    PropulsionSystem propulsion2 = nextStage.propulsion();
    ConstantThrustManeuver burn2 =
        new ConstantThrustManeuver(
            jettisonDate.shiftedBy(interstageCoastDuration).shiftedBy(1.0e-3),
            params.burn2Duration,
            propulsion2.thrust(),
            propulsion2.isp(),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn2);
  }

  /**
   * Returns the depletion floor guarding this maneuver: the post-jettison stack floor. A single
   * detector at this floor covers both burns — during burn 1 the mass stays above stage 1's own
   * floor, which is above this one. Burn 2's window is transition-time-driven, not fuel-capped,
   * so this is where a wrong mass accounting would burn nonexistent propellant (spec 06 I4a).
   */
  public double getDepletionFloor() {
    return nextStage.depletionFloor();
  }

  /**
   * Integrator max step keeping the late-ignition invariant for this maneuver. Burn 2 (the next
   * stage) ignites after the interstage coast, so a coast-sized trial step at its ignition mass
   * ({@link ActiveStageInfo#massAfterJettison()}, the full post-jettison stack) must not drive the
   * mass negative. Burn 1 fires immediately (no preceding coast, so no late-ignition hazard) and is
   * excluded. See {@link OrekitService#burnLimitedMaxStep}.
   *
   * @return the integrator max step in seconds
   */
  public double maxStepSeconds() {
    PropulsionSystem propulsion2 = nextStage.propulsion();
    return OrekitService.burnLimitedMaxStep(
        new OrekitService.BurnSpec(
            propulsion2.thrust(), propulsion2.isp(), activeStage.massAfterJettison()));
  }

  /**
   * Propagates the trajectory for optimization purposes (creates its own propagator). Returns a
   * penalizing fallback state on error.
   */
  public SpacecraftState propagateForOptimization(
      SpacecraftState initialState, double[] variables) {
    GravityTurnParams params = decode(variables);
    SpacecraftState kickedState = applyKick(initialState);

    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(maxStepSeconds());
    propagator.setInitialState(kickedState);
    configure(propagator, kickedState, params);
    // Quiet guard: infeasible candidates crossing the floor are truncated (and thus penalized by
    // the cost function) instead of burning nonexistent propellant.
    DepletionGuard.armQuiet(propagator, getDepletionFloor());
    MinAltitudeTracker tracker = new MinAltitudeTracker(0.0, Double.POSITIVE_INFINITY);
    propagator.addEventDetector(tracker);
    this.lastAltitudeTracker.set(tracker);
    AbsoluteDate endDate = kickedState.getDate().shiftedBy(params.transitionTime);

    try {
      return propagator.propagate(endDate);
    } catch (Exception e) {
      logger.debug("Gravity turn propagation failed (penalty applied): {}", e.getMessage());
      return kickedState; // penalty
    }
  }

  /**
   * Returns the altitude tracker attached to the most recent {@link #propagateForOptimization}
   * call on the calling thread, or {@code null} if no propagation has been performed on this thread
   * yet. Stored per-thread so parallel CMA-ES exploration runs don't overwrite each other's tracker.
   *
   * @return the last altitude tracker for this thread, or {@code null}
   */
  public MinAltitudeTracker getLastAltitudeTracker() {
    return lastAltitudeTracker.get();
  }

  /**
   * Returns the duration of burn 1, computed from the propellant remaining in the active stage at
   * gravity turn entry. The first stage fires until propellant exhaustion.
   *
   * @return the burn 1 duration in seconds
   */
  public double getBurn1Duration() {
    PropulsionSystem prop1 = activeStage.propulsion();
    double massFlowRate1 = prop1.thrust() / (prop1.isp() * Constants.G0_STANDARD_GRAVITY);
    return activeStage.remainingFuel(entryMass) / massFlowRate1;
  }

  /**
   * Returns the vehicle performing this maneuver.
   *
   * @return the vehicle
   */
  public Vehicle getVehicle() {
    return vehicle;
  }
}
