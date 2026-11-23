package com.smousseur.orbitlab.states.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How large the target the plasma volumes are marched into is, against the view it covers. */
class ReentryVolumeFilterTest {

  /** The application's 1280x720 window, marched at half resolution: the 640x360 measured. */
  @Test
  void theTargetIsTheViewOverTheDivisor() {
    assertEquals(640, ReentryVolumeFilter.reducedSize(1280, 2));
    assertEquals(360, ReentryVolumeFilter.reducedSize(720, 2));
    assertEquals(1280, ReentryVolumeFilter.reducedSize(1280, 1));
  }

  /** An odd size rounds up, so the reduced target still covers the view's last row of pixels. */
  @Test
  void anOddSizeRoundsUp() {
    assertEquals(361, ReentryVolumeFilter.reducedSize(721, 2));
  }

  /**
   * The composite chooses, at a body's edge, the reduced texel whose depth matches the pixel's: it
   * needs the scene's depth, which the processor only renders for a filter that asks for it.
   */
  @Test
  void theCompositeReadsTheSceneDepth() {
    assertTrue(new ReentryVolumeFilter(2).isRequiresDepthTexture());
  }

  /** A view collapsed to a pixel, as during a minimised window, still gets a target. */
  @Test
  void aViewSmallerThanTheDivisorGetsOnePixel() {
    assertEquals(1, ReentryVolumeFilter.reducedSize(1, 4));
    assertEquals(1, ReentryVolumeFilter.reducedSize(0, 2));
  }
}
