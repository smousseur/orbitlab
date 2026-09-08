package com.smousseur.orbitlab.simulation.mission.stage;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
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
 * stage separates) without any ε-boundary ambiguity. A short settling coast follows before the next
 * stage configures its burn.
 *
 * <p><b>Which stage gets dropped.</b> The stage jettisoned is whichever one the mass accounting
 * says is active — an assumption that only holds while the flight profile consumes the stages below
 * it exactly as calibrated. Pass an {@code expectedRole} to make that assumption explicit: the
 * separation then refuses to drop the wrong stage and fails fast instead of silently degrading the
 * rest of the profile (bilan 10 §6 follow-up — on the GEO profile a lighter upper stage makes the
 * gravity turn stop before S1 is dry, leaving S1 active, so an unchecked "S2 separation" jettisoned
 * S1 and let S2 masquerade as the payload's kick motor).
 *
 * <p><b>A role rather than a stack index</b> (spec {@code docs/etagement/03-conception-L1.md}
 * §3.7). Splitting the boosters out of the core moves every index above them by one, so an index
 * written by hand would have had to move with it — the very class of bug the guard exists to close.
 * A stack that declares no role at all refuses the guard rather than letting it pass silently:
 * asking for a role on a stack that has none is a wiring error, not a permission.
 */
public class StageSeparationStage extends MissionStage {
  private static final Logger logger = LogManager.getLogger(StageSeparationStage.class);

  private final double separationCoastDuration;
  private final StageRole expectedRole;
  private final boolean logJettison;

  /**
   * Creates a separation stage that drops whichever stage is active, without checking which.
   *
   * @param name the human-readable name of this stage
   * @param separationCoastDuration the settling coast after separation (s), typically the
   *     launcher's interstage coast
   */
  public StageSeparationStage(String name, double separationCoastDuration) {
    this(name, separationCoastDuration, null);
  }

  /**
   * Creates a separation stage that only drops the stage it is meant to drop.
   *
   * @param name the human-readable name of this stage
   * @param separationCoastDuration the settling coast after separation (s), typically the
   *     launcher's interstage coast
   * @param expectedRole the role of the stage this separation is designed to jettison, or {@code
   *     null} to accept whichever stage is active
   */
  public StageSeparationStage(String name, double separationCoastDuration, StageRole expectedRole) {
    this(name, separationCoastDuration, expectedRole, true);
  }

  /**
   * Creates a separation stage that can fire silently.
   *
   * <p>The jettison line is per-mission telemetry, and that is the only rate at which it says
   * anything: the same phase is <em>also</em> flown once per CMA-ES candidate by the ascent's
   * optimization chain ({@code AscentChainPropagation}), tens of thousands of times per mission
   * optimization and from every exploration thread at once, where it reports a mass the optimizer
   * is deliberately varying and queues all those threads on one log appender. This is the same
   * split the burn phases around it already make between {@code AscentInstrumentation#REPLAY} and
   * {@code AscentInstrumentation#optimizing} — the separation was simply left out of it when the
   * ascent became three explicit phases.
   *
   * <p>Only the log is silenced: the stack-index check still fires and still throws.
   *
   * @param name the human-readable name of this stage
   * @param separationCoastDuration the settling coast after separation (s), typically the
   *     launcher's interstage coast
   * @param expectedRole the role of the stage this separation is designed to jettison, or {@code
   *     null} to accept whichever stage is active
   * @param logJettison whether firing logs the jettison; {@code false} on an optimization chain
   */
  public StageSeparationStage(
      String name, double separationCoastDuration, StageRole expectedRole, boolean logJettison) {
    super(name);
    if (!(separationCoastDuration >= 0)) {
      throw new IllegalArgumentException("separationCoastDuration cannot be negative");
    }
    this.separationCoastDuration = separationCoastDuration;
    this.expectedRole = expectedRole;
    this.logJettison = logJettison;
  }

  @Override
  public boolean isPropulsive() {
    return false;
  }

  @Override
  public SpacecraftState enter(SpacecraftState previousState, Mission mission) {
    ActiveStageInfo info = mission.getVehicle().resolveActiveStage(previousState.getMass());
    if (expectedRole != null && info.role() == null) {
      throw new OrbitlabException(
          String.format(
              "[%s] guards on the %s stage but the stack declares no staging plan: the guard "
                  + "cannot be honoured",
              getName(), expectedRole));
    }
    if (expectedRole != null && info.role() != expectedRole) {
      throw new OrbitlabException(
          String.format(
              "[%s] is designed to jettison the %s stage but the %s stage (stack index %d) is "
                  + "active at %.0f kg (%.0f kg of propellant still aboard it): the profile did "
                  + "not consume the stages below as expected, jettisoning here would drop the "
                  + "wrong stage",
              getName(),
              expectedRole,
              info.role(),
              info.stageIndex(),
              previousState.getMass(),
              FastMath.max(0.0, info.remainingFuel(previousState.getMass()))));
    }
    if (logJettison) {
      logger.info(
          "[{}] jettison: mass {} kg -> {} kg",
          getName(),
          FastMath.round(previousState.getMass()),
          FastMath.round(info.massAfterJettison()));
    }
    return previousState.withMass(info.massAfterJettison());
  }

  @Override
  public void configure(NumericalPropagator propagator, Mission mission) {
    // The coast is measured from the propagator's own entry state, not from
    // mission.getCurrentState():
    // the two are the same state (the runner sets both), but only the propagator's is private to
    // this run. Once the ascent is optimized as a chain, this method runs on the parallel CMA-ES
    // exploration threads, which share the mission (spec 01 §5.4).
    AbsoluteDate endDate =
        propagator
            .getInitialState()
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
