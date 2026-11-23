package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The wake tuning refuses values that would make the wake vanish or blow up. */
class ReentryWakeConfigTest {

  @Test
  void defaultsAreTheStartingValuesOfTheDesign() {
    ReentryWakeConfig wake = ReentryWakeConfig.defaults();

    assertEquals(0.25f, wake.persistenceSeconds());
    assertEquals(3.0f, wake.minWidthPx());
    assertEquals(40f, wake.minLengthPx());
    assertEquals(7_000f, wake.referenceSpeedMs());
  }

  @Test
  void theReferenceLengthIsTheDistanceTheReferenceSpeedCoversInThePersistence() {
    ReentryWakeConfig wake = new ReentryWakeConfig(0.5f, 2f, 40f, 6_000f);

    assertEquals(3_000.0, wake.referenceLengthMeters(), 1e-9);
  }

  @Test
  void refusesANonPositivePersistence() {
    assertRefused(() -> new ReentryWakeConfig(0f, 2f, 40f, 7_000f));
    assertRefused(() -> new ReentryWakeConfig(-0.1f, 2f, 40f, 7_000f));
    assertRefused(() -> new ReentryWakeConfig(Float.NaN, 2f, 40f, 7_000f));
  }

  @Test
  void refusesNonPositivePixelFloors() {
    assertRefused(() -> new ReentryWakeConfig(0.25f, 0f, 40f, 7_000f));
    assertRefused(() -> new ReentryWakeConfig(0.25f, 2f, 0f, 7_000f));
    assertRefused(() -> new ReentryWakeConfig(0.25f, 2f, Float.NaN, 7_000f));
  }

  @Test
  void refusesANonPositiveReferenceSpeed() {
    assertRefused(() -> new ReentryWakeConfig(0.25f, 2f, 40f, 0f));
    assertRefused(() -> new ReentryWakeConfig(0.25f, 2f, 40f, Float.POSITIVE_INFINITY));
  }

  private static void assertRefused(Runnable construction) {
    assertThrows(IllegalArgumentException.class, construction::run);
  }
}
