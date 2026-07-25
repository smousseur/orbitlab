package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MultiStageLoadOptimizer;

/**
 * Propellant-sizing metadata attached to a {@link MissionPlan} produced by {@link
 * MinimizedLoadPlanner}. A projection of {@link MultiStageLoadOptimizer.Result} <em>minus</em> its
 * embedded {@link MissionComputeResult}, which is hoisted to {@link MissionPlan#computation()} so
 * there is a single path to the computation ({@code plan.computation()}), never a second one through
 * the sizing.
 *
 * @param lambdas the resolved per-stage scale factors, {@code 1} on unscaled stages
 * @param passes the coordinate sweeps performed
 * @param evaluations the mission optimizations spent resolving the loads
 */
public record PropellantSizing(double[] lambdas, int passes, int evaluations) {

  /**
   * Projects a load-sweep result onto its sizing metadata.
   *
   * @param result the coordinate sweep outcome
   * @return the sizing projection
   */
  public static PropellantSizing from(MultiStageLoadOptimizer.Result result) {
    return new PropellantSizing(result.lambdas(), result.passes(), result.evaluations());
  }
}
