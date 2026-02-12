package com.smousseur.orbitlab.simulation.mission.objective;

import org.orekit.propagation.SpacecraftState;

public interface MissionObjective {
  ObjectiveTarget getTarget();

  void evaluate(ObjectiveStatus status, SpacecraftState state);
}
