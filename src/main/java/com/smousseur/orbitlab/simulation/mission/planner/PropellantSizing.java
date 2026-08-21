package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MultiStageLoadOptimizer;
import java.util.Objects;

/**
 * Propellant-sizing metadata attached to a {@link MissionPlan} produced by {@link
 * MinimizedLoadPlanner}. A projection of {@link MultiStageLoadOptimizer.Result} <em>minus</em> its
 * embedded {@link MissionComputeResult}, which is hoisted to {@link MissionPlan#computation()} so
 * there is a single path to the computation ({@code plan.computation()}), never a second one
 * through the sizing.
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

  /**
   * Turns the resolved scale factors into the per-stage loads that were actually flown, in
   * kilograms.
   *
   * <p><b>This multiplication belongs at computation time, never at load time</b> (spec {@code
   * docs/scenario/01-persistance-missions.md} §2.3). A λ carries two dated dependencies its product
   * does not: the base it scales — whatever {@code PropellantBudget} produced that day — and the
   * mask deciding which stages carry a λ at all. Replaying {@code budgeted × λ} after either moved
   * would fly a third load set: neither the one that flew, nor the one today would compute. Here,
   * both factors are unambiguously in hand.
   *
   * <p>It lives on this record rather than inline in the caller for the same reason: the record
   * owns the λ, and a length mismatch between the two arrays is a real failure mode — an
   * inconsistency between the launcher the sweep sized and the one the spec carries — that deserves
   * to be caught somewhere a test can reach.
   *
   * @param budgetedLoads the per-stage loads the sweep started from (kg), launcher stage order
   * @return the per-stage loads actually flown (kg), a fresh array
   * @throws IllegalArgumentException if the two arrays do not describe the same launcher
   */
  public double[] applyTo(double[] budgetedLoads) {
    Objects.requireNonNull(budgetedLoads, "budgetedLoads");
    if (budgetedLoads.length != lambdas.length) {
      throw new IllegalArgumentException(
          "Propellant sizing describes "
              + lambdas.length
              + " stages, the loads describe "
              + budgetedLoads.length);
    }
    double[] flown = new double[budgetedLoads.length];
    for (int stage = 0; stage < flown.length; stage++) {
      flown[stage] = budgetedLoads[stage] * lambdas[stage];
    }
    return flown;
  }
}
