package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.events.NodeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * A mission stage representing an unpowered coasting phase with an optional maximum duration. If a
 * maximum time is specified, the stage transitions to the next stage when that time elapses. If no
 * maximum time is set, the stage coasts indefinitely until an external event triggers a transition.
 */
public class CoastingStage extends MissionStage {
  private final Double maxTime;
  private final boolean stopAtNode;

  /**
   * Creates a coasting stage with an optional maximum duration.
   *
   * @param name the human-readable name of this stage
   * @param maxTime the maximum coasting duration in seconds, or {@code null} for unlimited coasting
   */
  public CoastingStage(String name, Double maxTime) {
    super(name);
    this.maxTime = maxTime;
    this.stopAtNode = false;
  }

  public CoastingStage(String name, boolean stopAtNode) {
    super(name);
    this.maxTime = null;
    this.stopAtNode = stopAtNode;
  }

  @Override
  public boolean isPropulsive() {
    return false;
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    if (stopAtNode) {
      // The node is measured in the frame this stage flies in, not in GCRF. PHY-4 / L1 §3.4
      // announced three NodeDetector sites switched to the context and only two were; this is the
      // third, and it is the stage a sphere-of-influence crossing actually happens in. For an Earth
      // stage it is the very same frame instance, which is what lets the L1 gate prove the change
      // moved nothing (spec docs/multi-corps/06-conception-L4.md §3.7).
      propagator.addEventDetector(
          new NodeDetector(gravitationalContext(mission).inertialFrame())
              .withHandler(
                  (s, detector, increasing) -> {
                    mission.transitionToNextStage(s);
                    return Action.STOP;
                  }));
    } else if (maxTime != null) {
      AbsoluteDate t = mission.getCurrentState().getDate().shiftedBy(maxTime);
      this.configuredEndDate = t;
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
