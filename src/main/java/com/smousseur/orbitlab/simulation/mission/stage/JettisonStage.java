package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

public class JettisonStage extends MissionStage {
  private final int vehicleToJettisonIndex;

  public JettisonStage(String name) {
    this(name, 0);
  }

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
    /*
       AbsoluteDate t = mission.getCurrentState().getDate().shiftedBy(3);
       propagator.addEventDetector(
           new DateDetector(t)
               .withHandler(
                   (s, detector, increasing) -> {
                     mission.transitionToNextStage(s);
                     return Action.STOP;
                   }));
    */
  }
}
