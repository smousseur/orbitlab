package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
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
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Encapsulates the gravity turn maneuver configuration logic. Shared between {@link
 * GravityTurnStage} (execution) and {@link
 * com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem} (optimization).
 */
public class GravityTurnManeuver {

  private final Vehicle vehicle;
  private final double pitchKickAngleRad;
  private final double launchAzimuth;
  private final double usedAscensionPropellant;

  public GravityTurnManeuver(
      Vehicle vehicle, double ascensionDuration, double pitchKickAngleRad, double launchAzimuth) {
    this.vehicle = vehicle;
    this.pitchKickAngleRad = pitchKickAngleRad;
    this.launchAzimuth = launchAzimuth;
    this.usedAscensionPropellant =
        vehicle.getFirstStage().propulsion().massBurnt(ascensionDuration);
  }

  /** Decoded physical parameters from the raw optimization variables. */
  public record GravityTurnParams(
      double transitionTime, double exponent, double burn1Duration, double burn2Duration) {}

  /** Decodes raw CMA-ES variables into physical parameters. */
  public GravityTurnParams decode(double[] variables) {
    double transitionTime = variables[0];
    double exponent = variables[1];

    // Burn1 duration until propellant exhaustion
    PropulsionSystem prop1 = vehicle.getFirstStage().propulsion();
    double massFlowRate1 = prop1.thrust() / (prop1.isp() * Constants.G0_STANDARD_GRAVITY);
    double burn1Duration =
        (vehicle.getFirstStage().propellantCapacity() - usedAscensionPropellant) / massFlowRate1;

    // Burn2 duration after jettison until transitionTime
    double burn2Duration = transitionTime - burn1Duration;
    burn2Duration = FastMath.max(0.0, burn2Duration);

    return new GravityTurnParams(transitionTime, exponent, burn1Duration, burn2Duration);
  }

  /** Applies the pitch kick to the state (entry into gravity turn). */
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
    PropulsionSystem propulsion = vehicle.propulsion();
    AbsoluteDate kickDate = kickedState.getDate();

    GravityTurnAttitudeProvider attitudeProvider =
        new GravityTurnAttitudeProvider(kickDate, params.transitionTime(), params.exponent());
    propagator.setAttitudeProvider(attitudeProvider);

    // Burn 1
    PropulsionSystem propulsion1 = vehicle.getFirstStage().propulsion();
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
    // Jettison
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
                    double newMass = vehicle.getMass() - vehicle.getFirstStage().getMass();
                    return oldState.withMass(newMass);
                  }
                });
    propagator.addEventDetector(jettisonDetector);
    // Burn 2
    PropulsionSystem propulsion2 = vehicle.getSecondStage().propulsion();
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
    AbsoluteDate endDate = kickedState.getDate().shiftedBy(params.transitionTime);

    try {
      return propagator.propagate(endDate);
    } catch (Exception e) {
      return kickedState; // penalty
    }
  }

  /** Returns the duration of burn 1 (stage 1 fires to propellant exhaustion). */
  public double getBurn1Duration() {
    PropulsionSystem prop1 = vehicle.getFirstStage().propulsion();
    double massFlowRate1 = prop1.thrust() / (prop1.isp() * Constants.G0_STANDARD_GRAVITY);
    return (vehicle.getFirstStage().propellantCapacity() - usedAscensionPropellant) / massFlowRate1;
  }

  public Vehicle getVehicle() {
    return vehicle;
  }
}
