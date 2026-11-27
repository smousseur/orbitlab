package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The tuning of the retrograde flip: its defaults are the values fixed by the measured baseline,
 * and it refuses a value the flip schedule could not use.
 */
class RetrogradeFlipConfigTest {

  @Test
  void defaultsFlipOverFiveSimulatedMinutesBelowAHalfCosine() {
    RetrogradeFlipConfig config = RetrogradeFlipConfig.defaults();
    assertEquals(300.0, config.flipSeconds());
    assertEquals(Easing.SMOOTHSTEP, config.easing());
    assertEquals(-0.5, config.retrogradeCosine());
  }

  @Test
  void theEngineConfigurationCarriesTheDefaults() {
    assertEquals(RetrogradeFlipConfig.defaults(), EngineConfig.defaultSolarSystem().flip());
  }

  @Test
  void refusesAFlipThatTakesNoTime() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RetrogradeFlipConfig(0.0, Easing.SMOOTHSTEP, -0.5));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RetrogradeFlipConfig(Double.NaN, Easing.SMOOTHSTEP, -0.5));
  }

  @Test
  void refusesAMissingCurve() {
    assertThrows(NullPointerException.class, () -> new RetrogradeFlipConfig(300.0, null, -0.5));
  }

  @Test
  void refusesAThresholdThatIsNotRetrograde() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RetrogradeFlipConfig(300.0, Easing.SMOOTHSTEP, 0.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RetrogradeFlipConfig(300.0, Easing.SMOOTHSTEP, -1.0));
  }
}
