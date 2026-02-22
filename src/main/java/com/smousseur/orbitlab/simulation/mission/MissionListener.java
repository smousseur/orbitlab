package com.smousseur.orbitlab.simulation.mission;

import org.orekit.propagation.SpacecraftState;

public interface MissionListener {
  void onStageTransition(Mission mission, SpacecraftState stateAtEvent);
}
