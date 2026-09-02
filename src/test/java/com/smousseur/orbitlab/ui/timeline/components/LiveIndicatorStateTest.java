package com.smousseur.orbitlab.ui.timeline.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * {@link LiveIndicator#isLive} is the whole of what the cluster reports: the dot lights when the
 * clock shows the real time now, and on nothing else. Dates are built from {@code ARBITRARY_EPOCH}
 * so the predicate can be exercised without loading the Orekit data.
 */
class LiveIndicatorStateTest {

  private static final AbsoluteDate WALL = AbsoluteDate.ARBITRARY_EPOCH;

  @Test
  void playingAtRealTimeSpeedOnTheWallClockIsLive() {
    assertTrue(LiveIndicator.isLive(true, 1.0, WALL, WALL));
  }

  @Test
  void aPausedClockIsNotLiveEvenOnTheWallClock() {
    assertFalse(LiveIndicator.isLive(false, 1.0, WALL, WALL));
  }

  @Test
  void replayingAScrubbedDateAtOneTimesIsNotLive() {
    assertFalse(LiveIndicator.isLive(true, 1.0, WALL.shiftedBy(-300.0), WALL));
  }

  @Test
  void aClockAheadOfTheWallClockIsNotLiveEither() {
    assertFalse(LiveIndicator.isLive(true, 1.0, WALL.shiftedBy(300.0), WALL));
  }

  @Test
  void fastForwardAndRewindAreNotLive() {
    assertFalse(LiveIndicator.isLive(true, 60.0, WALL, WALL));
    assertFalse(LiveIndicator.isLive(true, -1.0, WALL, WALL));
  }

  @Test
  void theFrameTimeDriftOfAnOrdinarySessionStaysLive() {
    assertTrue(LiveIndicator.isLive(true, 1.0, WALL.shiftedBy(-1.0), WALL));
  }

  @Test
  void aStallLongEnoughToLoseRealTimeGoesDark() {
    assertFalse(LiveIndicator.isLive(true, 1.0, WALL.shiftedBy(-5.0), WALL));
  }
}
