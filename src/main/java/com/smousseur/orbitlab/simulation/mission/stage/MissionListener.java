package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import org.orekit.propagation.SpacecraftState;

public interface MissionListener {
  void onStageTransition(Mission mission, SpacecraftState stateAtEvent);
}
