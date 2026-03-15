package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ApsideDetector;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * A mission stage that coasts along a ballistic (unpowered) trajectory until a termination
 * condition is met. The stage can terminate either after a fixed duration or when the spacecraft
 * reaches an apside (apogee or perigee).
 */
public class BallisticCoastingStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(BallisticCoastingStage.class);
  private final boolean stopAtApside;
  private final Double duration;

  /**
   * Creates a ballistic coasting stage that terminates after a fixed duration.
   *
   * @param name the human-readable name of this stage
   * @param duration the coasting duration in seconds
   */
  public BallisticCoastingStage(String name, Double duration) {
    super(name);
    this.stopAtApside = false;
    this.duration = duration;
  }

  /**
   * Creates a ballistic coasting stage that terminates when an apside is reached.
   *
   * @param name the human-readable name of this stage
   * @param stopAtApside whether to stop at the next apside (apogee or perigee)
   */
  public BallisticCoastingStage(String name, boolean stopAtApside) {
    super(name);
    this.duration = -1.0;
    this.stopAtApside = stopAtApside;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    SpacecraftState currentState = mission.getCurrentState();
    ApsideDetector apogeeDetector =
        new ApsideDetector(currentState.getOrbit())
            .withHandler(
                (s, detector, increasing) -> {
                  logger.info("Apogee reached");
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });
    AbsoluteDate mecoDate = currentState.getDate().shiftedBy(this.duration);
    DateDetector mecoDetector =
        new DateDetector(mecoDate)
            .withHandler(
                (s, detector, increasing) -> {
                  logger.info("MECO reached");
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                });

    if (stopAtApside) {
      propagator.addEventDetector(apogeeDetector);
    } else if (this.duration >= 0) {
      propagator.addEventDetector(mecoDetector);
    }
  }
}
