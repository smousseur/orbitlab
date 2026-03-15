package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * A mission stage representing an unpowered coasting phase with an optional maximum duration.
 * If a maximum time is specified, the stage transitions to the next stage when that time elapses.
 * If no maximum time is set, the stage coasts indefinitely until an external event triggers
 * a transition.
 */
public class CoastingStage extends MissionStage {
  private final Double maxTime;

  /**
   * Creates a coasting stage with an optional maximum duration.
   *
   * @param name the human-readable name of this stage
   * @param maxTime the maximum coasting duration in seconds, or {@code null} for unlimited coasting
   */
  public CoastingStage(String name, Double maxTime) {
    super(name);
    this.maxTime = maxTime;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (maxTime != null) {
      AbsoluteDate t = mission.getCurrentState().getDate().shiftedBy(maxTime);
      propagator.addEventDetector(
          new DateDetector(t)
              .withHandler(
                  (s, detector, increasing) -> {
                    mission.transitionToNextStage(s);
                    return Action.STOP;
                  }));
    }
  }
}
