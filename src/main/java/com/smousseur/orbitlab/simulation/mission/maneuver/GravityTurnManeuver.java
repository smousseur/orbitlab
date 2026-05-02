package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
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
  private final double pitchKickAngleRad;
  private final double launchAzimuth;
  private final double usedAscensionPropellant;
  private final ActiveStageInfo activeStage;
  private final ActiveStageInfo nextStage;
  private MinAltitudeTracker lastAltitudeTracker;

  /**
   * Creates a gravity turn maneuver for the given vehicle and launch parameters.
   *
   * @param vehicle the vehicle performing the maneuver (must have at least two stages)
   * @param ascensionDuration the duration of the vertical ascent phase before the gravity turn
   *     (seconds)
   * @param pitchKickAngleRad the initial pitch kick angle in radians
   * @param launchAzimuth the launch azimuth angle in radians (measured from north)
   */
  public GravityTurnManeuver(
      Vehicle vehicle, double ascensionDuration, double pitchKickAngleRad, double launchAzimuth) {
    this.vehicle = vehicle;
    this.pitchKickAngleRad = pitchKickAngleRad;
    this.launchAzimuth = launchAzimuth;
    this.activeStage = vehicle.resolveActiveStage(vehicle.getMass());
    this.nextStage = vehicle.resolveActiveStage(activeStage.massAfterJettison());
    this.usedAscensionPropellant = activeStage.propulsion().massBurnt(ascensionDuration);
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
   * durations are derived from the vehicle's propellant capacity and propulsion characteristics.
   *
   * @param variables the raw optimization variable array (transitionTime, exponent)
   * @return the decoded physical parameters
   */
  public GravityTurnParams decode(double[] variables) {
    double transitionTime = variables[0];
    double exponent = variables[1];

    // Burn1 duration until propellant exhaustion
    PropulsionSystem prop1 = activeStage.propulsion();
    double massFlowRate1 = prop1.thrust() / (prop1.isp() * Constants.G0_STANDARD_GRAVITY);
    double burn1Duration =
        (activeStage.propellantCapacity() - usedAscensionPropellant) / massFlowRate1;

    // Burn2 duration after jettison until transitionTime
    double burn2Duration = transitionTime - burn1Duration;
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

    // Burn 1 — active stage propulsion
    PropulsionSystem propulsion1 = activeStage.propulsion();
    ConstantThrustManeuver burn1 =
        new ConstantThrustManeuver(
            kickDate.shiftedBy(1.0e-3),
            params.burn1Duration,
            propulsion1.thrust(),
            propulsion1.isp(),
            Vector3D.PLUS_I);
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
    // Burn 2 — next stage propulsion (after jettison)
    PropulsionSystem propulsion2 = nextStage.propulsion();
    ConstantThrustManeuver burn2 =
        new ConstantThrustManeuver(
            jettisonDate.shiftedBy(1.0e-3),
            params.burn2Duration,
            propulsion2.thrust(),
            propulsion2.isp(),
            Vector3D.PLUS_I);
    propagator.addForceModel(burn2);
  }

  /**
   * Propagates the trajectory for optimization purposes (creates its own propagator). Returns a
   * penalizing fallback state on error.
   */
  public SpacecraftState propagateForOptimization(
      SpacecraftState initialState, double[] variables) {
    GravityTurnParams params = decode(variables);
    SpacecraftState kickedState = applyKick(initialState);

    NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator();
    propagator.setInitialState(kickedState);
    configure(propagator, kickedState, params);
    MinAltitudeTracker tracker = new MinAltitudeTracker(0.0, Double.POSITIVE_INFINITY);
    propagator.addEventDetector(tracker);
    this.lastAltitudeTracker = tracker;
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
   * call, or {@code null} if no propagation has been performed yet.
   *
   * @return the last altitude tracker, or {@code null}
   */
  public MinAltitudeTracker getLastAltitudeTracker() {
    return lastAltitudeTracker;
  }

  /**
   * Returns the duration of burn 1, computed from the remaining first-stage propellant after
   * vertical ascent. The first stage fires until propellant exhaustion.
   *
   * @return the burn 1 duration in seconds
   */
  public double getBurn1Duration() {
    PropulsionSystem prop1 = activeStage.propulsion();
    double massFlowRate1 = prop1.thrust() / (prop1.isp() * Constants.G0_STANDARD_GRAVITY);
    return (activeStage.propellantCapacity() - usedAscensionPropellant) / massFlowRate1;
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
