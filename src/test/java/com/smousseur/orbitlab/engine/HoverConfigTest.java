package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The hover tuning refuses values that would make the detection or the fade meaningless. */
class HoverConfigTest {

  @Test
  void defaultsCarryTheAgreedValues() {
    HoverConfig config = HoverConfig.defaults();

    assertEquals(8f, config.enterPx());
    assertEquals(12f, config.exitPx());
    assertEquals(4, config.detectionStride());
    assertEquals(0.05f, config.fadeInTauSec());
    assertEquals(0.085f, config.fadeOutTauSec());
    assertEquals(1e-3f, config.fadeSnap());
    assertEquals(16f, config.iconRestPx());
    assertEquals(25f, config.iconHoverPx());
    assertEquals(0.45f, config.ringWhiteBlend());
    assertEquals(0.85f, config.labelWhiteBlend());
    assertEquals(1f, config.orbitCoreGrowPx());
    assertEquals(0.35f, config.orbitCoreWhiteBlend());
    assertEquals(18f, config.haloPx());
    assertEquals(0.36f, config.haloPeakAlpha());
  }

  @Test
  void acceptsEqualEnterAndExitBands() {
    assertDoesNotThrow(() -> config(10f, 10f, 1, 0.05f, 0.085f, 0f, 1f));
  }

  @Test
  void refusesANonPositiveEnterBand() {
    assertThrows(IllegalArgumentException.class, () -> config(0f, 12f, 4, 0.05f, 0.085f, 0f, 1f));
  }

  @Test
  void refusesAnExitBandNarrowerThanTheEnterBand() {
    assertThrows(IllegalArgumentException.class, () -> config(8f, 7f, 4, 0.05f, 0.085f, 0f, 1f));
  }

  @Test
  void refusesAStrideBelowOne() {
    assertThrows(IllegalArgumentException.class, () -> config(8f, 12f, 0, 0.05f, 0.085f, 0f, 1f));
  }

  @Test
  void refusesANonPositiveTimeConstant() {
    assertThrows(IllegalArgumentException.class, () -> config(8f, 12f, 4, 0f, 0.085f, 0f, 1f));
    assertThrows(IllegalArgumentException.class, () -> config(8f, 12f, 4, 0.05f, -1f, 0f, 1f));
    assertThrows(
        IllegalArgumentException.class, () -> config(8f, 12f, 4, Float.NaN, 0.085f, 0f, 1f));
  }

  @Test
  void refusesABlendOutsideTheUnitInterval() {
    assertThrows(
        IllegalArgumentException.class, () -> config(8f, 12f, 4, 0.05f, 0.085f, -0.1f, 1f));
    assertThrows(IllegalArgumentException.class, () -> config(8f, 12f, 4, 0.05f, 0.085f, 0f, 1.1f));
  }

  @Test
  void acceptsAnOrbitCoreThatDoesNotGrow() {
    assertDoesNotThrow(() -> orbitConfig(0f, 0.35f, 18f, 0.36f));
  }

  @Test
  void refusesAnOrbitCoreThatShrinks() {
    assertThrows(IllegalArgumentException.class, () -> orbitConfig(-1f, 0.35f, 18f, 0.36f));
  }

  @Test
  void refusesANonPositiveHaloWidth() {
    assertThrows(IllegalArgumentException.class, () -> orbitConfig(1f, 0.35f, 0f, 0.36f));
    assertThrows(IllegalArgumentException.class, () -> orbitConfig(1f, 0.35f, Float.NaN, 0.36f));
  }

  @Test
  void refusesAnOrbitBlendOrHaloPeakOutsideTheUnitInterval() {
    assertThrows(IllegalArgumentException.class, () -> orbitConfig(1f, -0.1f, 18f, 0.36f));
    assertThrows(IllegalArgumentException.class, () -> orbitConfig(1f, 0.35f, 18f, 1.1f));
  }

  private static HoverConfig config(
      float enterPx,
      float exitPx,
      int stride,
      float fadeInTau,
      float fadeOutTau,
      float ringBlend,
      float labelBlend) {
    return new HoverConfig(
        enterPx,
        exitPx,
        stride,
        fadeInTau,
        fadeOutTau,
        1e-3f,
        16f,
        25f,
        ringBlend,
        labelBlend,
        1f,
        0.35f,
        18f,
        0.36f);
  }

  private static HoverConfig orbitConfig(
      float coreGrowPx, float coreWhiteBlend, float haloPx, float haloPeakAlpha) {
    return new HoverConfig(
        8f,
        12f,
        4,
        0.05f,
        0.085f,
        1e-3f,
        16f,
        25f,
        0.45f,
        0.85f,
        coreGrowPx,
        coreWhiteBlend,
        haloPx,
        haloPeakAlpha);
  }
}
