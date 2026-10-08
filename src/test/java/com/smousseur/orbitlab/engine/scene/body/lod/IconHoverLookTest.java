package com.smousseur.orbitlab.engine.scene.body.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.engine.HoverConfig;
import org.junit.jupiter.api.Test;

/** How a planet or mission icon looks at a given hover intensity and dimming. */
class IconHoverLookTest {

  private static final float TOLERANCE = 1e-5f;
  private static final ColorRGBA BASE = new ColorRGBA(0.2f, 0.4f, 0.6f, 0.8f);
  private static final HoverConfig CONFIG = HoverConfig.defaults();

  @Test
  void atRestTheIconKeepsItsSizeAndColour() {
    IconHoverLook look = IconHoverLook.of(0f, 1f, BASE, CONFIG);

    assertEquals(16f, look.iconPx(), TOLERANCE);
    assertColor(BASE, look.ring());
    assertColor(BASE, look.label());
  }

  @Test
  void fullyLitTheIconGrowsAndBlendsTowardWhite() {
    IconHoverLook look = IconHoverLook.of(1f, 1f, BASE, CONFIG);

    assertEquals(25f, look.iconPx(), TOLERANCE);
    assertColor(new ColorRGBA(0.56f, 0.67f, 0.78f, 0.8f), look.ring());
    assertColor(new ColorRGBA(0.88f, 0.91f, 0.94f, 0.8f), look.label());
  }

  @Test
  void followsACubicEaseOut() {
    assertEquals(0f, IconHoverLook.easeOut(0f), TOLERANCE);
    assertEquals(0.875f, IconHoverLook.easeOut(0.5f), TOLERANCE);
    assertEquals(1f, IconHoverLook.easeOut(1f), TOLERANCE);

    assertEquals(16f + 9f * 0.875f, IconHoverLook.of(0.5f, 1f, BASE, CONFIG).iconPx(), TOLERANCE);
  }

  @Test
  void dimmingFadesTheRingAndTheNameButKeepsTheirColour() {
    IconHoverLook look = IconHoverLook.of(0f, 0.5f, BASE, CONFIG);

    assertEquals(16f, look.iconPx(), TOLERANCE);
    assertColor(new ColorRGBA(0.2f, 0.4f, 0.6f, 0.4f), look.ring());
    assertColor(new ColorRGBA(0.2f, 0.4f, 0.6f, 0.4f), look.label());
  }

  /** Mid-crossing, an icon can still be lit while it is already dimmed: the two compose. */
  @Test
  void dimmingComposesWithTheLitLook() {
    IconHoverLook look = IconHoverLook.of(1f, 0.5f, BASE, CONFIG);

    assertEquals(25f, look.iconPx(), TOLERANCE);
    assertColor(new ColorRGBA(0.56f, 0.67f, 0.78f, 0.4f), look.ring());
    assertColor(new ColorRGBA(0.88f, 0.91f, 0.94f, 0.4f), look.label());
  }

  @Test
  void leavesTheBaseColourUntouched() {
    ColorRGBA base = BASE.clone();

    IconHoverLook.of(1f, 0.5f, base, CONFIG);

    assertColor(BASE, base);
  }

  private static void assertColor(ColorRGBA expected, ColorRGBA actual) {
    assertEquals(expected.r, actual.r, TOLERANCE, "red");
    assertEquals(expected.g, actual.g, TOLERANCE, "green");
    assertEquals(expected.b, actual.b, TOLERANCE, "blue");
    assertEquals(expected.a, actual.a, TOLERANCE, "alpha");
  }
}
