package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.numerical.NumericalPropagator;

public class BallisticCoastingStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(BallisticCoastingStage.class);
  private final boolean stopAtApside;

  public BallisticCoastingStage(String name) {
    this(name, true);
  }

  public BallisticCoastingStage(String name, boolean stopAtApside) {
    super(name);
    this.stopAtApside = stopAtApside;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    ApsideDetector apogeeDetector =
        new ApsideDetector(mission.getCurrentState().getOrbit())
            .withHandler(
                (s, detector, increasing) -> {
                  logger.info("Apogee reached");
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });
    if (stopAtApside) {
      propagator.addEventDetector(apogeeDetector);
    }
  }
}
