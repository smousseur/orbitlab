package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import java.util.Objects;

/**
 * Unified outcome of a {@link MissionPlanner}, whatever optimization mode produced it.
 *
 * <p>{@link #computation()} is the common denominator every mode yields and the runtime consumes
 * (optimization results, ephemeris, performance report). {@link #sizing()} is present only when the
 * plan came from {@link MinimizedLoadPlanner}
 *
 * @param computation the mission computation (never {@code null})
 * @param sizing the propellant-sizing metadata, or {@code null} when the loads were fixed
 */
public record MissionPlan(MissionComputeResult computation, PropellantSizing sizing) {
  public MissionPlan {
    Objects.requireNonNull(computation, "computation");
  }

  /**
   * Creates a plan with fixed loads (no sizing metadata).
   *
   * @param computation the mission computation
   */
  public MissionPlan(MissionComputeResult computation) {
    this(computation, null);
  }
}
