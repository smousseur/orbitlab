package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.stage.ConstantThrustStage;
import com.smousseur.orbitlab.simulation.mission.attitude.ZenithThrustAttitudeProvider;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.propagation.SpacecraftState;

/**
 * A constant-thrust ascent stage that maintains a vertical (zenith-pointing) attitude throughout
 * the burn. This stage is typically used as the initial launch phase before a gravity turn is
 * initiated, thrusting straight up from the launch pad to gain altitude.
 */
public class VerticalAscentStage extends ConstantThrustStage {

  /**
   * Creates a vertical ascent stage with the given burn duration.
   *
   * @param name the human-readable name of this stage
   * @param duration the vertical ascent burn duration in seconds
   */
  public VerticalAscentStage(String name, double duration) {
    super(name, duration);
  }

  @Override
  protected AttitudeProvider getAttitudeProvider(SpacecraftState state) {
    return new ZenithThrustAttitudeProvider(state.getFrame());
  }
}
