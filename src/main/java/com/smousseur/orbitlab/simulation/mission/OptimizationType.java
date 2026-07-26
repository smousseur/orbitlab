package com.smousseur.orbitlab.simulation.mission;

/**
 * How much effort a mission's trajectory computation invests. The mode drives two independent axes:
 * the stage composition (analytic vs. CMA-ES, resolved by {@code MissionComposer}) and the
 * propellant-load handling (fixed vs. minimized, resolved by {@code MissionPlanOptimizer}).
 *
 * <p>Kept at the mission root (rather than nested in {@code MissionEntry}) so both the {@code
 * context} and {@code operation} packages can depend on it without a package cycle.
 */
public enum OptimizationType {
  /** Closed-form analytic profile, fixed loads. Fastest, historical default. */
  FAST,
  /** CMA-ES optimized transfer at fixed loads. */
  BALANCED,
  /** CMA-ES optimized transfer with propellant-load minimization. Slowest, most accurate. */
  PRECISE
}
