package com.smousseur.orbitlab.simulation.mission;

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

  /**
   * Propagates this stage in standalone mode (no side-effects on Mission's stage index). Used by
   * the optimizer to advance the mission state through non-optimizable stages.
   *
   * <p>Default implementation calls {@link #enter} then returns the updated state. Stages with a
   * duration (e.g. thrust stages) should override this to actually propagate.
   */
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    return enter(currentState, mission);
  }

  public final String getName() {
    return name;
  }
}
