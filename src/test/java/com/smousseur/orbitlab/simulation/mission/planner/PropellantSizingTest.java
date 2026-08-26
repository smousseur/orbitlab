package com.smousseur.orbitlab.simulation.mission.planner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The one multiplication the scenario format depends on: budgeted loads × resolved λ = the loads
 * that actually flew, in kilograms (spec {@code docs/scenario/01-persistance-missions.md} §2.3).
 */
class PropellantSizingTest {

  @Test
  void appliesTheScaleFactorsTermByTerm() {
    PropellantSizing sizing = new PropellantSizing(new double[] {0.9, 0.75, 1.0}, 2, 40);

    double[] flown = sizing.applyTo(new double[] {400_000.0, 100_000.0, 20_000.0});

    assertArrayEquals(new double[] {360_000.0, 75_000.0, 20_000.0}, flown, 1e-9);
  }

  /** A stage the sweep did not scale carries λ = 1, and must come out untouched to the kilogram. */
  @Test
  void unscaledStage_isLeftAlone() {
    PropellantSizing sizing = new PropellantSizing(new double[] {1.0}, 1, 1);

    assertArrayEquals(new double[] {123_456.789}, sizing.applyTo(new double[] {123_456.789}), 0.0);
  }

  @Test
  void doesNotWriteThroughToItsInput() {
    double[] budgeted = {400_000.0, 100_000.0};
    new PropellantSizing(new double[] {0.5, 0.5}, 1, 1).applyTo(budgeted);

    assertArrayEquals(new double[] {400_000.0, 100_000.0}, budgeted, 0.0);
  }

  /**
   * Two arrays of different lengths describe two different launchers. Multiplying what overlaps
   * would produce a plausible load array for a vehicle that does not exist.
   */
  @Test
  void mismatchedLengths_areRefused() {
    PropellantSizing sizing = new PropellantSizing(new double[] {0.9, 0.75}, 1, 1);

    assertThrows(IllegalArgumentException.class, () -> sizing.applyTo(new double[] {400_000.0}));
  }
}
