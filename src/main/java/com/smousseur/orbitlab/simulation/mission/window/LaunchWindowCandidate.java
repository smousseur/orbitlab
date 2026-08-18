package com.smousseur.orbitlab.simulation.mission.window;

import org.orekit.time.AbsoluteDate;

/**
 * One evaluation of one epoch: what it costs, or why it cannot be flown.
 *
 * <p>A refusal carries its reason as text because that reason is the only thing a user can act on —
 * "the lunar declination exceeds the parking inclination" and "the perilune floor is 135 km against
 * a 100 km target" are two different pieces of advice, and an enum would flatten both into {@code
 * INFEASIBLE}.
 *
 * @param epoch the evaluated date
 * @param deltaV the cost of leaving at {@code epoch} (m/s), {@link Double#POSITIVE_INFINITY} when
 *     refused
 * @param refusal why the epoch cannot be flown, or {@code null} when it can
 */
public record LaunchWindowCandidate(AbsoluteDate epoch, double deltaV, String refusal) {

  /**
   * @param epoch the evaluated date
   * @param deltaV the cost of leaving at that date (m/s)
   * @return a flyable candidate
   */
  public static LaunchWindowCandidate of(AbsoluteDate epoch, double deltaV) {
    return new LaunchWindowCandidate(epoch, deltaV, null);
  }

  /**
   * @param epoch the evaluated date
   * @param refusal why it cannot be flown
   * @return a refused candidate, at infinite cost
   */
  public static LaunchWindowCandidate refused(AbsoluteDate epoch, String refusal) {
    return new LaunchWindowCandidate(epoch, Double.POSITIVE_INFINITY, refusal);
  }

  /**
   * @return {@code true} when this epoch can be flown at a finite cost
   */
  public boolean feasible() {
    return refusal == null && Double.isFinite(deltaV);
  }
}
