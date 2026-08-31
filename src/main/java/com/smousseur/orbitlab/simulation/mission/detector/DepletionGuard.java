package com.smousseur.orbitlab.simulation.mission.detector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.numerical.NumericalPropagator;

/**
 * Fail-fast propellant guard (spec 06 I4a). Arms a {@link MassDepletionDetector} that stops the
 * propagation when the spacecraft mass crosses the depletion floor of the burning stage.
 *
 * <p><b>Two ways to reach the floor, and they do not mean the same thing</b> ({@code docs/bugs.md}
 * BUG-15). Which one applies is a property of how the burn's duration was scheduled, so the arming
 * site knows it statically and picks the method accordingly:
 *
 * <ul>
 *   <li>{@link #arm} — the burn window is a duration nothing clamps: a fixed ascent phase, an
 *       optimizer variable, a Tsiolkovsky duration from {@code Physics.computeBurnDuration}. The
 *       floor is then unreachable unless the mass accounting upstream is wrong, so firing is an
 *       error and is logged as one.
 *   <li>{@link #armCappedBurn} — the burn window came out of {@code
 *       Physics.computeBurnDurationCapped}, which clamps the duration to {@code remainingFuel /
 *       massFlow}. That clamp lands the mass <em>exactly</em> on the floor, so a capped burn always
 *       trips this detector, at its own scheduled cutoff. Nothing is inconsistent: the stage is
 *       sized short of the ΔV its plan asked for, which is a verdict the propellant-load search and
 *       the mission optimizer read as such. Logged at WARN, saying that.
 * </ul>
 *
 * <p>Before BUG-15 both cases went through {@link #arm} and accused the mass accounting. The second
 * one is the common one — it is the routine outcome of every rejected candidate load — so the
 * registry's loudest message described the situation that almost never holds.
 *
 * <p>Depletion-driven burn termination (real MECO by flame-out) is a later increment (I4b).
 */
public final class DepletionGuard {
  private static final Logger logger = LogManager.getLogger(DepletionGuard.class);

  private DepletionGuard() {}

  /**
   * Arms the guard on a propagator whose burn windows are not clamped to the propellant left, so
   * reaching the floor is an accounting bug. Logs an error when it fires.
   *
   * @param propagator the propagator to guard
   * @param depletionFloor the mass floor (kg) below which the burning stage is out of propellant
   * @param context short label for the log (stage or maneuver name)
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
   * Arms the guard on a propagator flying a burn whose duration was capped at the propellant floor.
   * Reaching the floor is then that burn's own scheduled cutoff, not an inconsistency, and it is
   * logged as the capability verdict it is — see the class javadoc.
   *
   * @param propagator the propagator to guard
   * @param depletionFloor the mass floor (kg) below which the burning stage is out of propellant
   * @param context short label for the log (stage or maneuver name)
   */
  public static void armCappedBurn(
      NumericalPropagator propagator, double depletionFloor, String context) {
    propagator.addEventDetector(
        new MassDepletionDetector(depletionFloor)
            .withHandler(
                (state, detector, increasing) -> {
                  logger.warn(
                      "[{}] Burn ended on propellant depletion at {} (floor {} kg): its duration "
                          + "was capped at the floor, so the stage is short of the ΔV planned "
                          + "— stopping propagation",
                      context,
                      state.getDate(),
                      depletionFloor);
                  return Action.STOP;
                }));
  }

  /**
   * Arms the guard without any log. Use on optimization propagations, where infeasible candidates
   * legitimately cross the floor: the truncation itself penalizes them through the cost function,
   * and one line per candidate would flood the logs.
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
