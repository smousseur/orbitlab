package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.Mission;
import java.util.Set;

/**
 * The ballistic coast of a translunar transfer: an ordinary {@link CoastingStage} that watches the
 * lunar sphere of influence and is allowed to change central body at it.
 *
 * <p><b>The class exists for one line</b>, and that is written rather than apologised for. L4 §3.1
 * deliberately refused to derive {@code soiTransitions} from the perturber set — doing so would arm a
 * detector on a GEO mission that only wanted the lunar perturbation, and make {@code
 * withPerturbers(MOON)} say two things at once. So the declaration cannot come from the mission, the
 * way the perturbers themselves do, and a stage has to carry it.
 *
 * <p><b>This is the first production stage in the repository to declare a transition</b>, which is
 * what makes L6 the first lot where any mission produces a second arc — and therefore the first
 * exercise of the arc machinery of L3, the switch of L4 and the two-scale rendering of L5 on a real
 * trajectory rather than on a fixture.
 */
public class TranslunarCoastStage extends CoastingStage {

  /**
   * Creates an open-ended translunar coast, whose duration comes from the mission's restitution
   * horizon because it is the last stage of the chain.
   *
   * @param name the human-readable name of this stage
   */
  public TranslunarCoastStage(String name) {
    super(name, (Double) null);
  }

  @Override
  public Set<SolarSystemBody> soiTransitions(Mission mission) {
    return Set.of(SolarSystemBody.MOON);
  }
}
