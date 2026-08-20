package com.smousseur.orbitlab.simulation.mission.progress;

import java.util.Objects;

/**
 * Where a running computation currently is, as a single immutable snapshot the render thread can
 * read without locking.
 *
 * <p>Two forms and no third, which is what encodes the display rule in the type rather than in a
 * convention: a fixed-load run reports its position in the stage sequence, a propellant-sizing run
 * reports its position in the load sweep and <em>nothing below it</em>. There is therefore no stage
 * field left empty in the sizing case — the case simply has no such field.
 */
public sealed interface ProgressPhase permits ProgressPhase.Trajectory, ProgressPhase.Sizing {

  /**
   * A fixed-load run: one CMA-ES optimization per optimizable stage, in sequence.
   *
   * @param stage the one-based index of the stage being optimized
   * @param stageCount how many optimizable stages the mission has
   * @param attempt the one-based CMA-ES attempt within that stage
   * @param attemptCount the configured ceiling of attempts
   * @param step the step the attempt is in
   */
  record Trajectory(int stage, int stageCount, int attempt, int attemptCount, OptimizationStep step)
      implements ProgressPhase {
    public Trajectory {
      Objects.requireNonNull(step, "step");
    }
  }

  /**
   * A propellant-sizing run: the coordinate-wise load sweep, each of whose evaluations wraps a full
   * mission optimization.
   *
   * @param pass the one-based coordinate-wise pass
   * @param passCount the configured ceiling of passes
   * @param load how many load evaluations have been spent
   * @param loadBudget the total evaluation budget of the sweep
   */
  record Sizing(int pass, int passCount, int load, int loadBudget) implements ProgressPhase {}
}
