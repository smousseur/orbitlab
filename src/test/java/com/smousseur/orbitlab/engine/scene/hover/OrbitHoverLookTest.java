package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.engine.HoverConfig;
import org.junit.jupiter.api.Test;

/** How a planet's orbit looks at a given hover intensity. */
class OrbitHoverLookTest {

  private static final float TOLERANCE = 1e-6f;
  private static final float REST_WIDTH_PX = 2.5f;
  private static final HoverConfig CONFIG = HoverConfig.defaults();

  @Test
  void atRestTheOrbitKeepsItsWidthAndShowsNoHalo() {
    OrbitHoverLook look = OrbitHoverLook.of(0f, REST_WIDTH_PX, CONFIG);

    assertEquals(2.5f, look.widthPx(), TOLERANCE);
    assertEquals(0f, look.coreWhite(), 0f);
    assertEquals(0f, look.haloAlpha(), 0f);
  }

  @Test
  void fullyLitTheCoreThickensAndWhitensUnderItsHalo() {
    OrbitHoverLook look = OrbitHoverLook.of(1f, REST_WIDTH_PX, CONFIG);

    assertEquals(3.5f, look.widthPx(), TOLERANCE);
    assertEquals(0.35f, look.coreWhite(), TOLERANCE);
    assertEquals(0.36f, look.haloAlpha(), TOLERANCE);
  }

  /** Unlike the icon, the orbit follows the intensity without an ease, as in the mockup. */
  @Test
  void followsTheIntensityLinearly() {
    OrbitHoverLook look = OrbitHoverLook.of(0.5f, REST_WIDTH_PX, CONFIG);

    assertEquals(3.0f, look.widthPx(), TOLERANCE);
    assertEquals(0.175f, look.coreWhite(), TOLERANCE);
    assertEquals(0.18f, look.haloAlpha(), TOLERANCE);
  }
}
