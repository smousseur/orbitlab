package com.smousseur.orbitlab.simulation.mission.progress;

import java.util.Objects;

/**
 * A transition in the optimization of a mission, reported on the cold path of {@link
 * MissionProgressListener}.
 *
 * <p>Each producer in the optimization chain emits only what it knows — the mission optimizer knows
 * which stage it entered, the CMA-ES optimizer knows which attempt and step it started, the load
 * sweep knows where it is in its budget — and {@link MissionProgress} assembles them into the
 * {@link ProgressPhase} the UI reads. No producer has to recompose what the others know.
 *
 * <p>Evaluation counting is deliberately <em>not</em> an event: it happens tens of thousands of
 * times per stage, from the parallel exploration threads, and must not allocate.
 */
public sealed interface MissionProgressEvent
    permits MissionProgressEvent.StageEntered,
        MissionProgressEvent.AttemptStarted,
        MissionProgressEvent.StepStarted,
        MissionProgressEvent.SizingAdvanced {

  /**
   * The mission optimizer moved on to another optimizable stage.
   *
   * @param index the one-based index of the stage among the optimizable ones
   * @param count how many optimizable stages the mission has
   */
  record StageEntered(int index, int count) implements MissionProgressEvent {
    public StageEntered {
      if (index < 1 || count < 1 || index > count) {
        throw new IllegalArgumentException("stage " + index + " of " + count);
      }
    }
  }

  /**
   * A CMA-ES attempt started. The first attempt is the nominal one; the following ones are the
   * retries triggered when the previous cost stayed above the acceptable cost.
   *
   * @param attempt the one-based attempt index
   * @param count the configured ceiling of attempts, which the early exits rarely let it reach
   */
  record AttemptStarted(int attempt, int count) implements MissionProgressEvent {
    public AttemptStarted {
      if (attempt < 1 || count < 1 || attempt > count) {
        throw new IllegalArgumentException("attempt " + attempt + " of " + count);
      }
    }
  }

  /**
   * The current attempt entered one of its two steps.
   *
   * @param step the step being entered
   */
  record StepStarted(OptimizationStep step) implements MissionProgressEvent {
    public StepStarted {
      Objects.requireNonNull(step, "step");
    }
  }

  /**
   * The propellant load sweep spent one evaluation. Each of them runs a full mission optimization,
   * which is why this level replaces the trajectory levels rather than nesting under them: those
   * would recycle up to {@code passCount * loadBudget} times and flicker without teaching anything.
   *
   * @param pass the one-based coordinate-wise pass
   * @param passCount the configured ceiling of passes
   * @param load how many load evaluations have been spent, including the heuristic probe
   * @param loadBudget the total evaluation budget of the sweep
   */
  record SizingAdvanced(int pass, int passCount, int load, int loadBudget)
      implements MissionProgressEvent {
    public SizingAdvanced {
      if (pass < 1 || passCount < 1) {
        throw new IllegalArgumentException("pass " + pass + " of " + passCount);
      }
      if (load < 0 || loadBudget < 1) {
        throw new IllegalArgumentException("load " + load + " of " + loadBudget);
      }
    }
  }
}
