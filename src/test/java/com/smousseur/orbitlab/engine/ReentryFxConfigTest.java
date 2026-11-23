package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.math.ColorRGBA;
import org.junit.jupiter.api.Test;

/** The re-entry effect tuning refuses values that would make the intensity or the shell absurd. */
class ReentryFxConfigTest {

  private static final ColorRGBA RIM = new ColorRGBA(1f, 0.45f, 0.1f, 1f);
  private static final ColorRGBA CORE = new ColorRGBA(1f, 0.97f, 0.9f, 1f);
  private static final ReentryWakeConfig WAKE = ReentryWakeConfig.defaults();

  @Test
  void defaultsCarryTheThresholdAndCeilingL0Measured() {
    ReentryFxConfig config = ReentryFxConfig.defaults();

    assertEquals(100_000.0, config.thresholdWm2());
    assertEquals(11_000_000.0, config.ceilingWm2());
  }

  @Test
  void acceptsAZeroStandoffAndTheEndsOfTheCoverageRange() {
    assertDoesNotThrow(() -> config(1e5, 1.1e7, 0f, 0f, 2.5f, 1.6f));
    assertDoesNotThrow(() -> config(1e5, 1.1e7, 1f, 0.5f, 2.5f, 1.6f));
  }

  @Test
  void refusesANonPositiveOrNonFiniteThreshold() {
    assertRefused(() -> config(0.0, 1.1e7, 0.35f, 0.5f, 2.5f, 1.6f));
    assertRefused(() -> config(-1.0, 1.1e7, 0.35f, 0.5f, 2.5f, 1.6f));
    assertRefused(() -> config(Double.NaN, 1.1e7, 0.35f, 0.5f, 2.5f, 1.6f));
  }

  @Test
  void refusesACeilingNotAboveTheThreshold() {
    assertRefused(() -> config(1e5, 1e5, 0.35f, 0.5f, 2.5f, 1.6f));
    assertRefused(() -> config(1e5, 5e4, 0.35f, 0.5f, 2.5f, 1.6f));
    assertRefused(() -> config(1e5, Double.POSITIVE_INFINITY, 0.35f, 0.5f, 2.5f, 1.6f));
  }

  @Test
  void refusesACoverageOutsideTheUnitRange() {
    assertRefused(() -> config(1e5, 1.1e7, -0.01f, 0.5f, 2.5f, 1.6f));
    assertRefused(() -> config(1e5, 1.1e7, 1.01f, 0.5f, 2.5f, 1.6f));
    assertRefused(() -> config(1e5, 1.1e7, Float.NaN, 0.5f, 2.5f, 1.6f));
  }

  @Test
  void refusesANegativeStandoffAndANonPositiveLengthOrOpening() {
    assertRefused(() -> config(1e5, 1.1e7, 0.35f, -0.1f, 2.5f, 1.6f));
    assertRefused(() -> config(1e5, 1.1e7, 0.35f, 0.5f, 0f, 1.6f));
    assertRefused(() -> config(1e5, 1.1e7, 0.35f, 0.5f, 2.5f, 0f));
    assertRefused(() -> config(1e5, 1.1e7, 0.35f, Float.POSITIVE_INFINITY, 2.5f, 1.6f));
  }

  @Test
  void refusesAMissingColour() {
    assertThrows(
        NullPointerException.class,
        () -> new ReentryFxConfig(1e5, 1.1e7, null, CORE, 0.35f, 0.5f, 2.5f, 1.6f, WAKE));
    assertThrows(
        NullPointerException.class,
        () -> new ReentryFxConfig(1e5, 1.1e7, RIM, null, 0.35f, 0.5f, 2.5f, 1.6f, WAKE));
  }

  @Test
  void defaultsCarryTheWakeDefaults() {
    assertEquals(ReentryWakeConfig.defaults(), ReentryFxConfig.defaults().wake());
  }

  @Test
  void refusesAMissingWake() {
    assertThrows(
        NullPointerException.class,
        () -> new ReentryFxConfig(1e5, 1.1e7, RIM, CORE, 0.35f, 0.5f, 2.5f, 1.6f, null));
  }

  @Test
  void coloursCannotBeChangedFromOutside() {
    ColorRGBA rim = RIM.clone();
    ReentryFxConfig config =
        new ReentryFxConfig(1e5, 1.1e7, rim, CORE, 0.35f, 0.5f, 2.5f, 1.6f, WAKE);

    rim.set(0f, 0f, 0f, 0f);
    config.hotCoreColor().set(0f, 0f, 0f, 0f);

    assertEquals(new ColorRGBA(1f, 0.45f, 0.1f, 1f), config.rimColor());
    assertEquals(new ColorRGBA(1f, 0.97f, 0.9f, 1f), config.hotCoreColor());
  }

  private static ReentryFxConfig config(
      double threshold,
      double ceiling,
      float coverage,
      float standoff,
      float length,
      float opening) {
    return new ReentryFxConfig(
        threshold, ceiling, RIM, CORE, coverage, standoff, length, opening, WAKE);
  }

  private static void assertRefused(Runnable construction) {
    assertThrows(IllegalArgumentException.class, construction::run);
  }
}
