package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How much a body is dimmed while another one is lit. */
class HoverDimTest {

  private static final float TOLERANCE = 1e-6f;
  private static final float ORBIT_FLOOR = 0.35f;
  private static final float ICON_FLOOR = 0.5f;

  @Test
  void atRestNothingIsDimmed() {
    assertEquals(1f, HoverDim.of(0f, 0f, ORBIT_FLOOR), 0f);
  }

  @Test
  void theMostLitBodyIsNeverDimmed() {
    assertEquals(1f, HoverDim.of(1f, 1f, ORBIT_FLOOR), 0f);
    assertEquals(1f, HoverDim.of(0.7f, 0.7f, ICON_FLOOR), 0f);
  }

  @Test
  void aFullyLitPlanetDimsTheOthersToTheirFloor() {
    assertEquals(0.35f, HoverDim.of(1f, 0f, ORBIT_FLOOR), TOLERANCE);
    assertEquals(0.5f, HoverDim.of(1f, 0f, ICON_FLOOR), TOLERANCE);
  }

  /**
   * The cursor has moved from A to B: A is fading out, at 0.4, while B fades in, at 0.7. A is
   * dimmed by the gap to B, and B, the most lit, not at all.
   */
  @Test
  void twoCrossingFadesDimTheFadingBodyByTheirGap() {
    float strongest = 0.7f;

    assertEquals(0.805f, HoverDim.of(strongest, 0.4f, ORBIT_FLOOR), TOLERANCE);
    assertEquals(0.85f, HoverDim.of(strongest, 0.4f, ICON_FLOOR), TOLERANCE);
    assertEquals(1f, HoverDim.of(strongest, 0.7f, ORBIT_FLOOR), 0f);
  }

  @Test
  void staysBetweenTheFloorAndOne() {
    assertEquals(1f, HoverDim.of(0.2f, 0.5f, ORBIT_FLOOR), 0f);
    assertEquals(0.35f, HoverDim.of(1.5f, 0f, ORBIT_FLOOR), TOLERANCE);
  }
}
