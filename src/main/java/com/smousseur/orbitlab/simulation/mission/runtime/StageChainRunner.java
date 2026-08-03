package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

/**
 * Flies a chain of {@link MissionStage}s end to end: for each stage, {@code enter()} → propagator
 * sized at the stage's own {@code maxStepSeconds} → {@code configure()} → propagation up to the
 * stage's configured cutoff, handing the resulting state to the next stage.
 *
 * <p><b>Why it is extracted.</b> This traversal was the body of {@link
 * com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator}. Splitting the
 * ascent into explicit phases (spec {@code specs/mission-stages/01-separations-implicites.md}
 * §5.4) requires the <b>optimize</b> pass to fly the same chain the <b>ephemeris</b> pass replays:
 * two implementations of "walk the stages" would let the two passes drift apart in how often the
 * integrator restarts, which is exactly the optimize-vs-ephemeris divergence closed by bilan 11
 * §3.9. One implementation, used by both, cannot drift.
 *
 * <p><b>Stateless by contract.</b> CMA-ES explores in parallel, so a runner instance carries no
 * per-run state: everything mutable lives in the caller's sampler and listener, and each {@code
 * run} threads its state through locals. A runner may be shared or created per run indifferently
 * — but a sampler or listener that accumulates must not be shared across concurrent runs.
 */
public final class StageChainRunner {
  private static final Logger logger = LogManager.getLogger(StageChainRunner.class);

  /**
   * Duration propagated for a stage that configured no cutoff of its own. Pure safety net: every
   * stage but the last sets {@link MissionStage#getConfiguredEndDate()}.
   */
  public static final double FALLBACK_DURATION_SECONDS = 7200.0;

  /** Receives every fixed-step sample of the flown trajectory, tagged with its stage. */
  @FunctionalInterface
  public interface StepSampler {
    void sample(MissionStage stage, SpacecraftState state);
  }

  /** Observes the chain stage by stage, without taking part in flying it. */
  public interface StageListener {

    /** Called before a stage is entered. */
    default void onStageStart(MissionStage stage) {}

    /** Called once a stage has been propagated, whether it succeeded or not. */
    void onStageEnd(StageRun run);
  }

  /**
   * Outcome of one stage of the chain.
   *
   * @param stage the stage that was flown
   * @param entryState the state the stage started from, after {@code enter()}
   * @param finalState the state it ended on, or the entry state if propagation threw
   * @param endDate the date propagation was asked to reach
   * @param endDateIsStageCutoff whether {@code endDate} is the stage's own configured cutoff, as
   *     opposed to an open-ended coast or the fallback duration — only then does stopping early
   *     mean something
   * @param propagationFailed whether the propagation threw
   */
  public record StageRun(
      MissionStage stage,
      SpacecraftState entryState,
      SpacecraftState finalState,
      AbsoluteDate endDate,
      boolean endDateIsStageCutoff,
      boolean propagationFailed) {

    /**
     * Seconds by which the stage stopped short of its own scheduled cutoff — a positive value
     * means another STOP event fired first (in practice a depletion guard). Zero when the end date
     * is not the stage's own cutoff.
     */
    public double shortfallSeconds() {
      return endDateIsStageCutoff ? endDate.durationFrom(finalState.getDate()) : 0.0;
    }
  }

  private final double sampleStepSeconds;
  private final StepSampler sampler;
  private final double lastStageCoastSeconds;
  private final StageListener listener;

  private StageChainRunner(
      double sampleStepSeconds,
      StepSampler sampler,
      double lastStageCoastSeconds,
      StageListener listener) {
    this.sampleStepSeconds = sampleStepSeconds;
    this.sampler = sampler;
    this.lastStageCoastSeconds = lastStageCoastSeconds;
    this.listener = listener;
  }

  /**
   * A runner that only flies the chain: no sampling, no observation, and every stage — including
   * the last — bounded by its own configured cutoff.
   *
   * @return the runner
   */
  public static StageChainRunner plain() {
    return new StageChainRunner(0.0, null, 0.0, null);
  }

  /**
   * A runner that samples the flown trajectory at a fixed step and reports each stage.
   *
   * @param sampleStepSeconds the sampling step (s)
   * @param sampler receives every sample
   * @param lastStageCoastSeconds how long to propagate the last stage of the chain, which has no
   *     cutoff of its own; pass {@code 0} to bound it by its configured cutoff like any other
   * @param listener observes each stage, or {@code null}
   * @return the runner
   */
  public static StageChainRunner sampling(
      double sampleStepSeconds,
      StepSampler sampler,
      double lastStageCoastSeconds,
      StageListener listener) {
    return new StageChainRunner(sampleStepSeconds, sampler, lastStageCoastSeconds, listener);
  }

  /**
   * Flies the chain from {@code entryState}, advancing {@link Mission#setCurrentState} at each
   * boundary as the stages' event handlers expect.
   *
   * <p>A stage whose propagation throws does not abort the chain: it is reported to the listener
   * as failed and the following stages fly from the mission's current state, matching the
   * ephemeris generator's long-standing behaviour of producing a partial (and flagged) trajectory
   * rather than none at all.
   *
   * @param chain the stages to fly, in order
   * @param entryState the state the first stage starts from
   * @param mission the parent mission
   * @return the state at the end of the last stage
   */
  public SpacecraftState run(
      List<MissionStage> chain, SpacecraftState entryState, Mission mission) {
    SpacecraftState currentState = entryState;

    for (int stageIdx = 0; stageIdx < chain.size(); stageIdx++) {
      MissionStage stage = chain.get(stageIdx);
      boolean isLastStage = (stageIdx == chain.size() - 1);

      if (listener != null) {
        listener.onStageStart(stage);
      }

      currentState = stage.enter(currentState, mission);

      // If optimizable with saved entry state, use it for reproducibility
      if (stage instanceof OptimizableMissionStage<?> opt && opt.getEntryState() != null) {
        currentState = opt.getEntryState();
      }

      mission.setCurrentState(currentState);
      SpacecraftState stageEntry = currentState;

      // Create and configure propagator. The stage sizes its own max step (bilan 08 §3.1): burn-free
      // stages coast at the large cap (a big saving on the multi-hour final coast), burn stages keep
      // the late-ignition invariant against their own upper-stage burns for a light I7 load.
      double maxStep = stage.maxStepSeconds(stageEntry, mission);
      NumericalPropagator propagator = OrekitService.get().createOptimizationPropagator(maxStep);
      propagator.setInitialState(stageEntry);
      stage.configure(propagator, mission);

      if (sampler != null && sampleStepSeconds > 0.0) {
        propagator
            .getMultiplexer()
            .add(sampleStepSeconds, state -> sampler.sample(stage, state));
      }

      // Propagate to the exact end date configured by the stage. Using the precise end date
      // avoids numerical issues where adaptive-step integrators might miss ConstantThrustManeuver
      // boundary events when propagating far past the actual stage duration.
      //
      // Only a stage whose own cutoff defines endDate can be judged "reached its end": an
      // open-ended final coast, and a stage falling back to the safety net, have no real cutoff to
      // compare against.
      AbsoluteDate endDate;
      boolean endDateIsStageCutoff = false;
      if (isLastStage && lastStageCoastSeconds > 0.0) {
        endDate = stageEntry.getDate().shiftedBy(lastStageCoastSeconds);
      } else if (stage.getConfiguredEndDate() != null) {
        endDate = stage.getConfiguredEndDate();
        endDateIsStageCutoff = true;
      } else {
        endDate = stageEntry.getDate().shiftedBy(FALLBACK_DURATION_SECONDS);
      }

      SpacecraftState finalState;
      boolean failed = false;
      try {
        finalState = propagator.propagate(endDate);
      } catch (Exception e) {
        logger.warn("Propagation failed for stage '{}': {}", stage.getName(), e.getMessage());
        finalState = mission.getCurrentState();
        failed = true;
      }

      if (listener != null) {
        listener.onStageEnd(
            new StageRun(stage, stageEntry, finalState, endDate, endDateIsStageCutoff, failed));
      }

      currentState = finalState;
      mission.setCurrentState(currentState);
    }

    return currentState;
  }
}
