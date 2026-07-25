package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;

import java.util.Objects;

/**
 * {@link MissionPlanner} that flies the mission at its given propellant loads: it runs a single
 * {@link MissionOptimizer#optimize()} and wraps the result. Covers both the analytic profile and
 * the CMA-ES optimized transfer — those differ only in how the mission's stages are composed (an
 * upstream {@code MissionFactory} concern), not in how the mission is run.
 */
public final class FixedLoadPlanner implements MissionPlanner {
  private final Mission mission;
  private final int maxEvaluations;
  // null → MissionOptimizer's built-in deterministic default seed.
  private final Long seed;

  /**
   * Creates a planner with the deterministic default CMA-ES seed.
   *
   * @param mission the mission to optimize
   * @param maxEvaluations the per-stage CMA-ES evaluation budget
   */
  public FixedLoadPlanner(Mission mission, int maxEvaluations) {
    this(mission, maxEvaluations, null);
  }

  /**
   * Creates a planner with an explicit CMA-ES master seed.
   *
   * @param mission the mission to optimize
   * @param maxEvaluations the per-stage CMA-ES evaluation budget
   * @param seed the CMA-ES master seed, or {@code null} for {@link MissionOptimizer}'s default
   */
  public FixedLoadPlanner(Mission mission, int maxEvaluations, Long seed) {
    this.mission = Objects.requireNonNull(mission, "mission");
    this.maxEvaluations = maxEvaluations;
    this.seed = seed;
  }

  @Override
  public MissionPlan plan() {
    MissionOptimizer optimizer =
        seed == null
            ? new MissionOptimizer(mission, maxEvaluations)
            : new MissionOptimizer(mission, maxEvaluations, seed);
    return new MissionPlan(optimizer.optimize());
  }
}
