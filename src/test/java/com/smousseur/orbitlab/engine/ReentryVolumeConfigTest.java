package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The plasma volume tuning refuses values that would leave no volume to march or no light. */
class ReentryVolumeConfigTest {

  @Test
  void defaultsAreTheValuesOfTheDesign() {
    ReentryVolumeConfig volume = ReentryVolumeConfig.defaults();

    assertEquals(56, volume.steps());
    assertEquals(2, volume.resolutionDivisor());
    assertEquals(3f, volume.aheadRadii());
    assertEquals(20f, volume.behindRadii());
    assertEquals(11f, volume.halfWidthRadii());
    assertEquals(290f, volume.soundSpeedMs());
    assertEquals(6f, volume.emissionGain());
    assertEquals(0.7f, volume.exposure());
  }

  @Test
  void acceptsOneStepFullResolutionAndNothingAheadOfTheNose() {
    assertDoesNotThrow(() -> new ReentryVolumeConfig(1, 1, 0f, 20f, 11f, 290f, 6f, 0.7f));
  }

  @Test
  void refusesFewerThanOneStepOrADivisorUnderOne() {
    assertRefused(() -> new ReentryVolumeConfig(0, 2, 3f, 20f, 11f, 290f, 6f, 0.7f));
    assertRefused(() -> new ReentryVolumeConfig(56, 0, 3f, 20f, 11f, 290f, 6f, 0.7f));
  }

  @Test
  void refusesANegativeReachAheadAndANonPositiveReachBehind() {
    assertRefused(() -> new ReentryVolumeConfig(56, 2, -0.1f, 20f, 11f, 290f, 6f, 0.7f));
    assertRefused(() -> new ReentryVolumeConfig(56, 2, Float.NaN, 20f, 11f, 290f, 6f, 0.7f));
    assertRefused(() -> new ReentryVolumeConfig(56, 2, 3f, 0f, 11f, 290f, 6f, 0.7f));
  }

  /** The body is one radius wide: a box no wider would hold no plasma beside it. */
  @Test
  void refusesAHalfWidthThatDoesNotClearTheBody() {
    assertRefused(() -> new ReentryVolumeConfig(56, 2, 3f, 20f, 1f, 290f, 6f, 0.7f));
    assertRefused(
        () -> new ReentryVolumeConfig(56, 2, 3f, 20f, Float.POSITIVE_INFINITY, 290f, 6f, 0.7f));
  }

  @Test
  void refusesANonPositiveSpeedOfSoundGainOrExposure() {
    assertRefused(() -> new ReentryVolumeConfig(56, 2, 3f, 20f, 11f, 0f, 6f, 0.7f));
    assertRefused(() -> new ReentryVolumeConfig(56, 2, 3f, 20f, 11f, 290f, 0f, 0.7f));
    assertRefused(() -> new ReentryVolumeConfig(56, 2, 3f, 20f, 11f, 290f, 6f, -0.7f));
  }

  private static void assertRefused(Runnable construction) {
    assertThrows(IllegalArgumentException.class, construction::run);
  }
}
