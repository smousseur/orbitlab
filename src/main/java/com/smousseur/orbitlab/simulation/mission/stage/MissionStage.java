package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;

public abstract class MissionStage {
  protected final String name;

  public MissionStage(String name) {
    this.name = name;
  }

  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    return previousState;
  }

  public abstract void configure(NumericalPropagator propagator, Mission mission);

  public final String getName() {
    return name;
  }
}
