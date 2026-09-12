package com.smousseur.orbitlab.simulation.mission.planner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The bisection that narrows the bracket a flame-out opens (PHY-2, spec {@code
 * docs/atmosphere/13-cloture-PHY-2.md} §5.3).
 *
 * <p>The behaviour it serves needs flights and lives in {@code MeasuredLoadPlannerFlightTest}; the
 * arithmetic does not, and pinning it here is what makes the flight test's result readable — a
 * disappointing flight is then a fact about the trajectory, never about the midpoint.
 */
class MeasuredLoadPlannerBracketTest {

  /** The stack index of the sized stage in these fixtures. */
  private static final int TOP = 2;

  /**
   * The measured bracket of the Ariane 64 LEO-400 drag-on profile: the loop ends with 3 804 kg
   * known feasible and 246 kg known dry, and returned the rich end before this bisection existed.
   */
  @Test
  void bisectsTheMeasuredBracket_geometrically() {
    double[] feasible = {141_000.0, 152_000.0, 3_804.0};

    double[] next = MeasuredLoadPlanner.bisected(feasible, 246.0, TOP).orElseThrow();

    assertEquals(Math.sqrt(246.0 * 3_804.0), next[TOP], 1e-9);
    assertEquals(967.4, next[TOP], 0.1);
  }

  /**
   * The geometric midpoint takes the square root of the bracket's <em>ratio</em>, which is the
   * property the budget of {@link MeasuredLoadPlanner#MAX_BRACKET_PASSES} is sized against: three
   * probes take a ratio of 15.5 down to 1.4.
   */
  @Test
  void halvesTheBracketRatio_notItsWidth() {
    double dry = 246.0;
    double[] feasible = {141_000.0, 152_000.0, 3_804.0};
    double ratio = feasible[TOP] / dry;

    double[] next = MeasuredLoadPlanner.bisected(feasible, dry, TOP).orElseThrow();

    assertEquals(Math.sqrt(ratio), next[TOP] / dry, 1e-9);
    assertTrue(next[TOP] < (dry + feasible[TOP]) / 2.0, "the arithmetic midpoint would be richer");
  }

  /** Only the sized stage moves: the lower stages fly at capacity whatever the sizing decides. */
  @Test
  void movesTheSizedStageAlone() {
    double[] feasible = {141_000.0, 152_000.0, 3_804.0};

    double[] next = MeasuredLoadPlanner.bisected(feasible, 246.0, TOP).orElseThrow();

    assertEquals(141_000.0, next[0], 0.0);
    assertEquals(152_000.0, next[1], 0.0);
    assertNotSame(feasible, next, "the candidate must not write through to the flown loads");
    assertEquals(3_804.0, feasible[TOP], 0.0);
  }

  /**
   * A bracket tighter than the accepted residual band is not worth a flight: the two ends are
   * closer to each other than the width of the band that would have to tell them apart.
   */
  @Test
  void refusesABracketTooTightToLearnFrom() {
    double[] feasible = {141_000.0, 152_000.0, 300.0};

    assertEquals(Optional.empty(), MeasuredLoadPlanner.bisected(feasible, 250.0, TOP));
  }

  /** Exactly at the threshold counts as tight: the comparison is strict on the useful side. */
  @Test
  void refusesTheThresholdItself() {
    double dry = 1_000.0;
    double[] feasible = {141_000.0, 152_000.0, 1_250.0};

    assertEquals(Optional.empty(), MeasuredLoadPlanner.bisected(feasible, dry, TOP));
    assertTrue(
        MeasuredLoadPlanner.bisected(new double[] {141_000.0, 152_000.0, 1_260.0}, dry, TOP)
            .isPresent(),
        "just above the threshold there is still something to measure");
  }
}
