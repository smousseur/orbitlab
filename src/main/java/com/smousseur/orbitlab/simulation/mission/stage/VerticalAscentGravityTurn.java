package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import org.orekit.propagation.numerical.NumericalPropagator;

public class VerticalAscentGravityTurn extends MissionStage {
  public VerticalAscentGravityTurn(String name) {
    super(name);
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {}
}
