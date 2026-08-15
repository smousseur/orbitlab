package com.smousseur.orbitlab.ui.timeline.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Gesture state machine of {@link ScrubGesture} (spec {@code NAV-3}). */
class ScrubGestureTest {

  @Test
  void pressThenReleaseWithNoMotionIsAClick() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertEquals(ScrubGesture.Release.CLICK, g.release());
  }

  @Test
  void pressMoveTwoPixelsReleaseIsAClick() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertEquals(ScrubGesture.Move.NONE, g.move(102f, 10L));
    assertEquals(ScrubGesture.Release.CLICK, g.release());
  }

  @Test
  void pressMoveExactlyThreePixelsReleaseIsStillAClick() {
    // Pins the strict comparison: exactly the threshold does not cross it.
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertEquals(ScrubGesture.Move.NONE, g.move(103f, 10L));
    assertEquals(ScrubGesture.Release.CLICK, g.release());
  }

  @Test
  void pressMoveFourPixelsBeginsDragAndReleaseCommits() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertEquals(ScrubGesture.Move.BEGIN_DRAG, g.move(104f, 10L));
    assertTrue(g.isDragging());
    assertEquals(ScrubGesture.Release.COMMIT, g.release());
  }

  @Test
  void onceDraggingAReturnToOriginNeverRevertsToAClick() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertEquals(ScrubGesture.Move.BEGIN_DRAG, g.move(104f, 10L));
    // Moving back to the exact origin: still a drag decision (EMIT or TRACK_ONLY), never NONE.
    ScrubGesture.Move back = g.move(100f, 20L);
    assertTrue(back == ScrubGesture.Move.EMIT || back == ScrubGesture.Move.TRACK_ONLY);
    assertTrue(g.isDragging());
    assertEquals(ScrubGesture.Release.COMMIT, g.release());
  }

  @Test
  void throttleGatesEmissionsWhileDragging() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 1000L);
    assertEquals(ScrubGesture.Move.BEGIN_DRAG, g.move(104f, 1000L));
    assertEquals(ScrubGesture.Move.TRACK_ONLY, g.move(110f, 1050L));
    assertEquals(ScrubGesture.Move.EMIT, g.move(112f, 1100L));
    assertEquals(ScrubGesture.Move.TRACK_ONLY, g.move(114f, 1120L));
  }

  @Test
  void moveBeforeAnyPressIsNone() {
    ScrubGesture g = new ScrubGesture();
    assertEquals(ScrubGesture.Move.NONE, g.move(100f, 0L));
  }

  @Test
  void releaseWithoutAPressIsNone() {
    ScrubGesture g = new ScrubGesture();
    assertEquals(ScrubGesture.Release.NONE, g.release());
  }

  @Test
  void cancelWhileDraggingReportsTrueAndSubsequentReleaseIsNone() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    g.move(104f, 10L);
    assertTrue(g.cancel());
    assertFalse(g.isDragging());
    assertEquals(ScrubGesture.Release.NONE, g.release());
  }

  @Test
  void cancelWhileMerelyPressedReportsFalse() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertFalse(g.cancel());
  }

  @Test
  void isDraggingReflectsStateAcrossTheLifecycle() {
    ScrubGesture g = new ScrubGesture();
    assertFalse(g.isDragging());
    g.press(100f, 0L);
    assertFalse(g.isDragging());
    g.move(104f, 10L);
    assertTrue(g.isDragging());
  }

  @Test
  void aDragToTheLeftAlsoCrossesTheThresholdOnAbsoluteValue() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    assertEquals(ScrubGesture.Move.BEGIN_DRAG, g.move(96f, 10L));
    assertTrue(g.isDragging());
  }

  @Test
  void pressWhileAlreadyPressedRestartsFromTheNewOrigin() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    g.press(200f, 5L);
    // Only 2 px from the new origin (200), well under threshold.
    assertEquals(ScrubGesture.Move.NONE, g.move(202f, 10L));
    assertEquals(ScrubGesture.Release.CLICK, g.release());
  }

  @Test
  void pressWhileDraggingRestartsFromTheNewOrigin() {
    ScrubGesture g = new ScrubGesture();
    g.press(100f, 0L);
    g.move(110f, 10L); // now dragging
    g.press(300f, 20L); // restarts the gesture entirely
    assertFalse(g.isDragging());
    assertEquals(ScrubGesture.Move.NONE, g.move(301f, 30L));
    assertEquals(ScrubGesture.Release.CLICK, g.release());
  }
}
