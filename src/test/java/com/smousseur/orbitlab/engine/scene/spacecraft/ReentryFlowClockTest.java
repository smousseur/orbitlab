package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How much of its turbulence the plasma keeps at a clock rate: all of it while the noise drifts
 * smoothly from frame to frame, none once it would jump by more than its coarsest structure.
 */
class ReentryFlowClockTest {

  @Test
  void pausedOrSlowTheTurbulenceIsWhole() {
    assertEquals(1f, ReentryFlowClock.turbulence(0.0), 0f);
    assertEquals(1f, ReentryFlowClock.turbulence(1.0), 0f);
    assertEquals(1f, ReentryFlowClock.turbulence(2.0), 0f);
  }

  /** 1 - smoothstep(2, 10, 5): t = 3/8, t^2 (3 - 2t) = 0.31640625. */
  @Test
  void atFiveTimesTheTurbulenceIsPartlySmoothedOut() {
    assertEquals(0.68359375f, ReentryFlowClock.turbulence(5.0), 1e-6f);
  }

  @Test
  void fromTenTimesTheTurbulenceIsGone() {
    assertEquals(0f, ReentryFlowClock.turbulence(10.0), 0f);
    assertEquals(0f, ReentryFlowClock.turbulence(300.0), 0f);
  }

  /** Rewinding drifts the noise as fast as playing forward. */
  @Test
  void rewindingSmoothsAsMuchAsPlaying() {
    assertEquals(0.68359375f, ReentryFlowClock.turbulence(-5.0), 1e-6f);
    assertEquals(0f, ReentryFlowClock.turbulence(-60.0), 0f);
  }
}
