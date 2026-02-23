package com.smousseur.orbitlab.simulation.mission.maneuver;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.propagation.SpacecraftState;
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
  private final double verticalBurnDuration;

  public GravityTurnManeuver(
      Vehicle vehicle,
      double pitchKickAngleRad,
      double launchAzimuth,
      double verticalBurnDuration) {
    this.vehicle = vehicle;
    this.pitchKickAngleRad = pitchKickAngleRad;
    this.launchAzimuth = launchAzimuth;
    this.verticalBurnDuration = verticalBurnDuration;
  }

  /** Decoded physical parameters from the raw optimization variables. */
  public record GravityTurnParams(
      double transitionTime, double exponent, double remainingBurnTime) {}

  /** Decodes raw CMA-ES variables into physical parameters. */
  public GravityTurnParams decode(double[] variables) {
    double transitionTime = variables[0];
    double exponent = variables[1];
    double propFraction = variables[2];

    PropulsionSystem propulsion = vehicle.getPropulsion();
    double massFlowRate = propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
    double propToUse = propFraction * vehicle.getCurrentStagePropellantMass();
    double propUsedVertical = massFlowRate * verticalBurnDuration;
    double remainingBurnTime = (propToUse - propUsedVertical) / massFlowRate;
    remainingBurnTime = FastMath.max(10.0, remainingBurnTime);

    return new GravityTurnParams(transitionTime, exponent, remainingBurnTime);
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
    PropulsionSystem propulsion = vehicle.getPropulsion();
    AbsoluteDate kickDate = kickedState.getDate();

    GravityTurnAttitudeProvider attitudeProvider =
        new GravityTurnAttitudeProvider(kickDate, params.transitionTime(), params.exponent());

    ConstantThrustManeuver burn =
        new ConstantThrustManeuver(
            kickDate.shiftedBy(1.0e-3),
            params.remainingBurnTime(),
            propulsion.thrust(),
            propulsion.isp(),
            attitudeProvider,
            Vector3D.PLUS_I);

    propagator.addForceModel(burn);
  }

  /**
   * Propagates the trajectory for optimization purposes (creates its own propagator). Returns a
   * penalizing fallback state on error.
   */
  public SpacecraftState propagateForOptimization(
      SpacecraftState initialState, double minAllowableMass, double[] variables) {
    GravityTurnParams params = decode(variables);
    SpacecraftState kickedState = applyKick(initialState);

    NumericalPropagator propagator = OrekitService.get().createSimplePropagator();
    propagator.setInitialState(kickedState);
    configure(propagator, kickedState, params);
    propagator.addEventDetector(new MassDepletionDetector(minAllowableMass));
    AbsoluteDate endDate = kickedState.getDate().shiftedBy(params.remainingBurnTime() + 1.0);

    try {
      SpacecraftState finalState = propagator.propagate(endDate);
      // If propagation was cut short by MassDepletionDetector, return penalty state
      double timeDiff = FastMath.abs(finalState.getDate().durationFrom(endDate));
      if (timeDiff > 1.0) {
        return kickedState; // penalty: propellant exhausted before end of burn
      }

      double stage2Mass = finalState.getMass() - vehicle.getDryMass();
      if (stage2Mass <= 0) {
        return kickedState; // penalty
      }
      return finalState.withMass(stage2Mass);
    } catch (Exception e) {
      return kickedState; // penalty
    }
  }

  public Vehicle getVehicle() {
    return vehicle;
  }
}
