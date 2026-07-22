package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
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
 *
 * <p><b>Which stage gets dropped.</b> The stage jettisoned is whichever one the mass accounting
 * says is active — an assumption that only holds while the flight profile consumes the stages
 * below it exactly as calibrated. Pass an {@code expectedStageIndex} to make that assumption
 * explicit: the separation then refuses to drop the wrong stage and fails fast instead of silently
 * degrading the rest of the profile (bilan 10 §6 follow-up — on the GEO profile a lighter upper
 * stage makes the gravity turn stop before S1 is dry, leaving S1 active, so an unchecked "S2
 * separation" jettisoned S1 and let S2 masquerade as the payload's kick motor).
 */
public class StageSeparationStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(StageSeparationStage.class);

  /** {@link #expectedStageIndex} value disabling the check. */
  public static final int ANY_STAGE = -1;

  private final double separationCoastDuration;
  private final int expectedStageIndex;

  /**
   * Creates a separation stage that drops whichever stage is active, without checking which.
   *
   * @param name the human-readable name of this stage
   * @param separationCoastDuration the settling coast after separation (s), typically the
   *     launcher's interstage coast
   */
  public StageSeparationStage(String name, double separationCoastDuration) {
    this(name, separationCoastDuration, ANY_STAGE);
  }

  /**
   * Creates a separation stage that only drops the stage it is meant to drop.
   *
   * @param name the human-readable name of this stage
   * @param separationCoastDuration the settling coast after separation (s), typically the
   *     launcher's interstage coast
   * @param expectedStageIndex the stack index of the stage this separation is designed to
   *     jettison, or {@link #ANY_STAGE} to accept whichever stage is active
   */
  public StageSeparationStage(
      String name, double separationCoastDuration, int expectedStageIndex) {
    super(name);
    if (!(separationCoastDuration >= 0)) {
      throw new IllegalArgumentException("separationCoastDuration cannot be negative");
    }
    this.separationCoastDuration = separationCoastDuration;
    this.expectedStageIndex = expectedStageIndex;
  }

  @Override
  public boolean isPropulsive() {
    return false;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    ActiveStageInfo info = mission.getVehicle().resolveActiveStage(previousState.getMass());
    if (expectedStageIndex != ANY_STAGE && info.stageIndex() != expectedStageIndex) {
      throw new OrbitlabException(
          String.format(
              "[%s] is designed to jettison stack stage %d but stage %d is active at %.0f kg "
                  + "(%.0f kg of propellant still aboard it): the profile did not consume the "
                  + "stages below as expected, jettisoning here would drop the wrong stage",
              getName(),
              expectedStageIndex,
              info.stageIndex(),
              previousState.getMass(),
              FastMath.max(0.0, info.remainingFuel(previousState.getMass()))));
    }
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
