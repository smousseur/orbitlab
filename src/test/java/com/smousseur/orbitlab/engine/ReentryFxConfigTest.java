package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The re-entry effect tuning refuses values that would make the intensity absurd. */
class ReentryFxConfigTest {

  private static final ReentryVolumeConfig VOLUME = ReentryVolumeConfig.defaults();

  @Test
  void defaultsCarryTheThresholdAndCeilingL0Measured() {
    ReentryFxConfig config = ReentryFxConfig.defaults();

    assertEquals(100_000.0, config.thresholdWm2());
    assertEquals(11_000_000.0, config.ceilingWm2());
  }

  @Test
  void refusesANonPositiveOrNonFiniteThreshold() {
    assertRefused(() -> new ReentryFxConfig(0.0, 1.1e7, VOLUME));
    assertRefused(() -> new ReentryFxConfig(-1.0, 1.1e7, VOLUME));
    assertRefused(() -> new ReentryFxConfig(Double.NaN, 1.1e7, VOLUME));
  }

  @Test
  void refusesACeilingNotAboveTheThreshold() {
    assertRefused(() -> new ReentryFxConfig(1e5, 1e5, VOLUME));
    assertRefused(() -> new ReentryFxConfig(1e5, 5e4, VOLUME));
    assertRefused(() -> new ReentryFxConfig(1e5, Double.POSITIVE_INFINITY, VOLUME));
  }

  @Test
  void defaultsCarryTheVolumeDefaults() {
    assertEquals(ReentryVolumeConfig.defaults(), ReentryFxConfig.defaults().volume());
  }

  @Test
  void refusesAMissingVolume() {
    assertThrows(NullPointerException.class, () -> new ReentryFxConfig(1e5, 1.1e7, null));
  }

  private static void assertRefused(Runnable construction) {
    assertThrows(IllegalArgumentException.class, construction::run);
  }
}
