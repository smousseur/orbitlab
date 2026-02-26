package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

public class CoastingStage extends MissionStage {
  private final Double maxTime;

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
