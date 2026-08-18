package com.smousseur.orbitlab.simulation.mission.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * MIS-2 — the encoding of one evaluation.
 *
 * <p>The property worth pinning is not the two factories but {@link
 * LaunchWindowCandidate#feasible()}: the solver reads it to decide whether an epoch is data it can
 * offer or data it walks past, and it is the <em>conjunction</em> of the two conditions that makes
 * a hand-built inconsistent candidate — a refusal at a finite cost — refuse rather than slip
 * through.
 *
 * <p>No Orekit data is touched: {@link AbsoluteDate#J2000_EPOCH} and {@code shiftedBy} are
 * arithmetic.
 */
class LaunchWindowCandidateTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  @Test
  @DisplayName("A flyable candidate keeps its cost and carries no refusal")
  void ofBuildsAFlyableCandidate() {
    LaunchWindowCandidate candidate = LaunchWindowCandidate.of(T0.shiftedBy(3600.0), 3181.0);

    assertEquals(T0.shiftedBy(3600.0), candidate.epoch());
    assertEquals(3181.0, candidate.deltaV());
    assertNull(candidate.refusal());
    assertTrue(candidate.feasible());
  }

  @Test
  @DisplayName("A refusal costs infinity, so the solver needs no special case for it")
  void refusedBuildsAnInfinitelyExpensiveCandidate() {
    LaunchWindowCandidate candidate =
        LaunchWindowCandidate.refused(T0, "lunar declination exceeds the parking inclination");

    assertEquals(Double.POSITIVE_INFINITY, candidate.deltaV());
    assertEquals("lunar declination exceeds the parking inclination", candidate.refusal());
    assertFalse(candidate.feasible());
  }

  @Test
  @DisplayName("An infinite or NaN cost is not feasible even without a stated refusal")
  void aNonFiniteCostIsNotFeasible() {
    // The guard that matters: a problem computing its way to NaN — a degenerate Lambert geometry,
    // an acos out of range — reports it as a number, not as an exception. Reading feasibility off
    // the refusal string alone would let that NaN through and rank it against real candidates.
    assertFalse(new LaunchWindowCandidate(T0, Double.NaN, null).feasible());
    assertFalse(new LaunchWindowCandidate(T0, Double.POSITIVE_INFINITY, null).feasible());
  }

  @Test
  @DisplayName("A stated refusal is not feasible even at a finite cost")
  void aRefusalAtAFiniteCostIsStillARefusal() {
    // The mirror case: a problem that computes a cost and then finds the epoch unflyable. The
    // refusal wins, so the two conditions are a conjunction and not two spellings of one.
    assertFalse(new LaunchWindowCandidate(T0, 3181.0, "perilune floor is 135 km").feasible());
  }

  @Test
  @DisplayName("Two candidates for the same epoch at the same cost are equal")
  void equalityIsByValue() {
    assertEquals(
        LaunchWindowCandidate.of(T0.shiftedBy(600.0), 3000.0),
        LaunchWindowCandidate.of(T0.shiftedBy(600.0), 3000.0));
  }
}
