package com.smousseur.orbitlab.simulation.mission.objective.composite;

import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveStatus;
import com.smousseur.orbitlab.simulation.mission.objective.ObjectiveTarget;
import org.orekit.propagation.SpacecraftState;

import java.util.List;

public class CompositeObjective implements MissionObjective {
  private final List<MissionObjective> objectives;

  public CompositeObjective(List<MissionObjective> objectives) {
    this.objectives = objectives;
  }

  @Override
  public ObjectiveTarget getTarget() {
    return null;
  }

  @Override
  public void evaluate(ObjectiveStatus status, SpacecraftState state) {}
}
