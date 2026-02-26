package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.stage.ConstantThrustStage;
import com.smousseur.orbitlab.simulation.mission.attitude.ZenithThrustAttitudeProvider;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.propagation.SpacecraftState;

public class VerticalAscentStage extends ConstantThrustStage {
  public VerticalAscentStage(String name, double duration) {
    super(name, duration);
  }

  @Override
  protected AttitudeProvider getAttitudeProvider(SpacecraftState state) {
    return new ZenithThrustAttitudeProvider(state.getFrame());
  }
}
