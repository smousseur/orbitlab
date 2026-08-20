package com.smousseur.orbitlab.simulation.mission.progress;

/** The two phases a single CMA-ES attempt goes through, in order. */
public enum OptimizationStep {
  /** Independent runs sampling the whole bounded domain, executed in parallel. */
  EXPLORATION,
  /** Sequential passes narrowing sigma around the best point found by the exploration. */
  REFINEMENT
}
