package com.smousseur.orbitlab.ui.timeline.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link SpeedStepper#speedToIndex(double)} is the inverse the capsule needs (spec §12.1). */
class SpeedStepperIndexTest {

  @Test
  void everyIndexSurvivesARoundTripThroughTheSpeed() {
    for (int i = SpeedStepper.MIN_INDEX; i <= SpeedStepper.MAX_INDEX; i++) {
      assertEquals(i, SpeedStepper.speedToIndex(SpeedStepper.mapIndexToSpeed(i)), "index " + i);
    }
  }

  @Test
  void realTimeMapsToZeroWhateverItsSign() {
    assertEquals(0, SpeedStepper.speedToIndex(1.0));
    assertEquals(0, SpeedStepper.speedToIndex(-1.0));
  }

  @Test
  void aSpeedNotInTheTableSnapsToTheNearestStep() {
    assertEquals(3, SpeedStepper.speedToIndex(11.0));
    assertEquals(-3, SpeedStepper.speedToIndex(-11.0));
  }

  @Test
  void aSpeedBeyondTheTableSaturatesAtTheLastStep() {
    assertEquals(SpeedStepper.MAX_INDEX, SpeedStepper.speedToIndex(1e12));
    assertEquals(SpeedStepper.MIN_INDEX, SpeedStepper.speedToIndex(-1e12));
  }
}
