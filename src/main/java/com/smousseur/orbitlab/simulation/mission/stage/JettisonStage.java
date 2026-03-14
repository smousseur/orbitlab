package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * A mission stage that jettisons a vehicle component (e.g., a spent booster or fairing) and
 * immediately transitions to the next stage. The spacecraft mass is updated to reflect the
 * jettisoned component's removal.
 */
public class JettisonStage extends MissionStage {
  private final int vehicleToJettisonIndex;

  /**
   * Creates a jettison stage that discards the first vehicle component (index 0).
   *
   * @param name the human-readable name of this stage
   */
  public JettisonStage(String name) {
    this(name, 0);
  }

  /**
   * Creates a jettison stage that discards the vehicle component at the specified index.
   *
   * @param name the human-readable name of this stage
   * @param vehicleToJettisonIndex the index of the vehicle component to jettison
   */
  public JettisonStage(String name, int vehicleToJettisonIndex) {
    super(name);
    this.vehicleToJettisonIndex = vehicleToJettisonIndex;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    Vehicle vehicle = mission.getVehicle();
    vehicle.jettison(vehicleToJettisonIndex);
    return previousState.withMass(vehicle.getMass());
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    mission.transitionToNextStage(mission.getCurrentState());
  }
}
