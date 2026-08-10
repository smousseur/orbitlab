package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.attitude.GravityTurnAttitudeProvider;
import com.smousseur.orbitlab.simulation.mission.detector.DepletionGuard;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import java.util.Objects;
import org.hipparchus.ode.events.Action;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * One powered phase of the explicit three-phase ascent (spec {@code
 * docs/mission-stages/01-separations-implicites.md} §5.2): {@code Gravity turn (S1) → S1
 * separation → Gravity turn (S2)}. Both burns share everything but their propulsion and their
 * cutoff, which is what the two subclasses supply.
 *
 * <p><b>The pitch law is anchored on the plan, never on the phase.</b> {@link
 * GravityTurnAttitudeProvider} interpolates on {@code (date − kickDate) / transitionTime}, so a
 * second burn anchored on its <em>own</em> start date would restart at {@code alpha = 0} — thrust
 * straight up, just after staging. Anchoring both phases on {@link AscentPlan#kickDate()} and
 * {@link AscentPlan#transitionTime()} makes the attitude a pure function of date, identical to the
 * one the single-propagator ascent flew.
 */
public abstract class GravityTurnBurnStage extends MissionStage {

  /**
   * The detectors a burn phase arms besides its own cutoff — the one place the optimize and replay
   * passes legitimately differ, mirroring what the single-propagator ascent already did.
   */
  @FunctionalInterface
  public interface AscentInstrumentation {

    /**
     * Arms this instrumentation on a phase's propagator.
     *
     * @param propagator the phase propagator
     * @param plan the ascent plan being flown
     * @param stageName the phase name, for logs
     */
    void arm(NumericalPropagator propagator, AscentPlan plan, String stageName);

    /**
     * Replay instrumentation: a loud depletion guard. On this path the burn durations are supposed
     * to fit the loaded propellant, so crossing the floor is an accounting bug worth an error log.
     */
    AscentInstrumentation REPLAY =
        (propagator, plan, stageName) ->
            DepletionGuard.arm(propagator, plan.depletionFloor(), stageName);

    /**
     * Optimize instrumentation: a quiet depletion guard (infeasible candidates legitimately cross
     * the floor and are penalized by the truncation itself) plus the shared altitude tracker that
     * grades an underground trajectory.
     *
     * @param tracker the tracker shared by both burn phases of one evaluation
     * @return the instrumentation
     */
    static AscentInstrumentation optimizing(MinAltitudeTracker tracker) {
      return (propagator, plan, stageName) -> {
        DepletionGuard.armQuiet(propagator, plan.depletionFloor());
        propagator.addEventDetector(tracker);
      };
    }
  }

  /** The plan shared by the three ascent phases. */
  protected final AscentPlanRef planRef;

  private final AscentInstrumentation instrumentation;

  /**
   * Creates a burn phase reading its schedule from the shared plan.
   *
   * @param name the human-readable phase name
   * @param planRef the reference the three ascent phases share
   * @param instrumentation the guards this phase arms (replay or optimize)
   */
  protected GravityTurnBurnStage(
      String name, AscentPlanRef planRef, AscentInstrumentation instrumentation) {
    super(name);
    this.planRef = Objects.requireNonNull(planRef, "planRef");
    this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
  }

  @Override
  public double maxStepSeconds(SpacecraftState entryState, Mission mission) {
    // Every ascent phase steps at the plan's max step — the one sized on the late-igniting second
    // burn (spec §5.5). Falling back to MissionStage's default would RELAX the step on a light
    // load (burnLimitedMaxStep is capped at SAFE_MAX_STEP, so the default is >= this value) and
    // change the trajectory for no gain; refining the step per phase is a later concern.
    return planRef.require(getName()).maxStepSeconds();
  }

  @Override
  public final void configure(NumericalPropagator propagator, Mission mission) {
    prepare(propagator, mission);
    AscentPlan plan = planRef.require(getName());

    propagator.setAttitudeProvider(
        new GravityTurnAttitudeProvider(plan.kickDate(), plan.transitionTime(), plan.exponent()));
    configureBurn(propagator, plan);
    instrumentation.arm(propagator, plan, getName());

    AbsoluteDate endDate = endDate(plan, propagator.getInitialState());
    this.configuredEndDate = endDate;
    propagator.addEventDetector(
        new DateDetector(endDate)
            .withHandler(
                (state, detector, increasing) -> {
                  mission.transitionToNextStage(state);
                  return Action.STOP;
                }));
  }

  /**
   * Hook run before the plan is read. The first burn uses it to apply the pitch kick and to publish
   * the plan it owns; the second burn has nothing to do.
   *
   * @param propagator the phase propagator, already holding the entry state
   * @param mission the parent mission
   */
  protected void prepare(NumericalPropagator propagator, Mission mission) {
    // No-op by default.
  }

  /**
   * Adds this phase's thrust to the propagator.
   *
   * @param propagator the phase propagator
   * @param plan the ascent plan being flown
   */
  protected abstract void configureBurn(NumericalPropagator propagator, AscentPlan plan);

  /**
   * The date this phase hands over at.
   *
   * @param plan the ascent plan being flown
   * @param entryState the state this phase starts from
   * @return the cutoff date
   */
  protected abstract AbsoluteDate endDate(AscentPlan plan, SpacecraftState entryState);

  @Override
  public SpacecraftState propagateStandalone(SpacecraftState currentState, Mission mission) {
    // Used by the MissionOptimizer loop, which advances an advancesByReplay() ascent phase by
    // phase. Flies exactly what StageChainRunner would fly for this phase.
    SpacecraftState entryState = enter(currentState, mission);
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(maxStepSeconds(entryState, mission));
    propagator.setInitialState(entryState);
    ReentryGuard.armQuiet(propagator);
    configure(propagator, mission);
    return propagator.propagate(getConfiguredEndDate());
  }
}
