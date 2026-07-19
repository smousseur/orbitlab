package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * Explicit separation of the spent active stage between two mission stages (spec 06 I5, decision
 * S4). On entry the state mass drops to the exact reference mass of the stack above, so {@code
 * resolveActiveStage} activates the next vehicle (e.g. the payload's kick motor once the upper
 * stage separates) without any ε-boundary ambiguity. A short settling coast follows before the
 * next stage configures its burn.
 */
public class StageSeparationStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(StageSeparationStage.class);

  private final double separationCoastDuration;

  /**
   * Creates a separation stage.
   *
   * @param name the human-readable name of this stage
   * @param separationCoastDuration the settling coast after separation (s), typically the
   *     launcher's interstage coast
   */
  public StageSeparationStage(String name, double separationCoastDuration) {
    super(name);
    if (!(separationCoastDuration >= 0)) {
      throw new IllegalArgumentException("separationCoastDuration cannot be negative");
    }
    this.separationCoastDuration = separationCoastDuration;
  }

  @Override
  public boolean isPropulsive() {
    return false;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    ActiveStageInfo info = mission.getVehicle().resolveActiveStage(previousState.getMass());
    logger.info(
        "[{}] jettison: mass {} kg -> {} kg",
        getName(),
        FastMath.round(previousState.getMass()),
        FastMath.round(info.massAfterJettison()));
    return previousState.withMass(info.massAfterJettison());
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    AbsoluteDate endDate =
        mission
            .getCurrentState()
            .getDate()
            .shiftedBy(FastMath.max(separationCoastDuration, 1.0e-3));
    this.configuredEndDate = endDate;
    propagator.addEventDetector(
        new DateDetector(endDate)
            .withHandler(
                (s, detector, increasing) -> {
                  mission.transitionToNextStage(s);
                  return Action.STOP;
                }));
  }
}
