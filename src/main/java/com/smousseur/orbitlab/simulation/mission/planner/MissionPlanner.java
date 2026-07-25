package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MultiStageLoadOptimizer;

/**
 * A deferred mission computation: produces a {@link MissionPlan} regardless of the optimization
 * mode that backs it.
 *
 * <p>This is the single seam the runtime consumes. Callers pick the concrete planner that matches
 * the mode they want and call {@link #plan()} — no mode enum, no {@code switch}:
 *
 * <ul>
 *   <li>{@link FixedLoadPlanner} — flies the mission at its given propellant loads ({@code
 *       MissionOptimizer.optimize()}). Covers both the analytic profile and the CMA-ES optimized
 *       transfer: they differ only in how the mission's stages are composed, not in how it is run.
 *   <li>{@link MinimizedLoadPlanner} — sizes the propellant first, wrapping many mission
 *       optimizations in the coordinate-wise load sweep ({@link MultiStageLoadOptimizer}), and
 *       reports the resolved per-stage scaling as {@link PropellantSizing}.
 * </ul>
 *
 * <p>Each planner owns its own construction inputs, so the T3 plumbing — the mission builder, the
 * heuristic loads, the scaling mask, the launch epoch, the feasibility settings — stays enclosed in
 * {@link MinimizedLoadPlanner} and never reaches the caller.
 */
@FunctionalInterface
public interface MissionPlanner {
  /**
   * Runs the optimization and returns the computed plan.
   *
   * @return the mission plan, always carrying a {@link MissionComputeResult}
   */
  MissionPlan plan();
}
