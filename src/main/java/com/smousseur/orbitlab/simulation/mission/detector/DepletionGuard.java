package com.smousseur.orbitlab.simulation.mission.detector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * Fail-fast propellant guard (spec 06 I4a). Arms a {@link MassDepletionDetector} that stops the
 * propagation with an error log when the spacecraft mass crosses the depletion floor of the
 * burning stage. Burn windows are still date-based at this increment, so crossing the floor means
 * the upstream mass accounting is wrong (a burn scheduled longer than the propellant allows) — no
 * nominal trajectory is expected to trigger it. Depletion-driven burn termination (real MECO by
 * flame-out) is the next increment (I4b).
 */
public final class DepletionGuard {
  private static final Logger logger = LogManager.getLogger(DepletionGuard.class);

  private DepletionGuard() {}

  /**
   * Arms the guard on the given propagator, logging an error when it fires. Use on replay and
   * standalone paths, where burn durations are supposed to be consistent with the loaded
   * propellant: firing there is an accounting bug.
   *
   * @param propagator the propagator to guard
   * @param depletionFloor the mass floor (kg) below which the burning stage is out of propellant
   * @param context short label for the error log (stage or maneuver name)
   */
  public static void arm(NumericalPropagator propagator, double depletionFloor, String context) {
    propagator.addEventDetector(
        new MassDepletionDetector(depletionFloor)
            .withHandler(
                (state, detector, increasing) -> {
                  logger.error(
                      "[{}] Propellant depleted before scheduled cutoff at {} (floor {} kg): "
                          + "stopping propagation, upstream mass accounting is wrong",
                      context,
                      state.getDate(),
                      depletionFloor);
                  return Action.STOP;
                }));
  }

  /**
   * Arms the guard without the error log. Use on optimization propagations, where infeasible
   * candidates legitimately cross the floor: the truncation itself penalizes them through the
   * cost function, and an error per candidate would flood the logs.
   *
   * @param propagator the propagator to guard
   * @param depletionFloor the mass floor (kg) below which the burning stage is out of propellant
   */
  public static void armQuiet(NumericalPropagator propagator, double depletionFloor) {
    propagator.addEventDetector(
        new MassDepletionDetector(depletionFloor)
            .withHandler((state, detector, increasing) -> Action.STOP));
  }
}
