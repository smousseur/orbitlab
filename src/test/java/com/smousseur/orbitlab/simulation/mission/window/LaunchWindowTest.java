package com.smousseur.orbitlab.simulation.mission.window;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * MIS-2 — the slot as the wizard will read it: an interval, plus the instant inside it that costs
 * least.
 */
class LaunchWindowTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  private static LaunchWindow window(double opening, double best, double closing, double deltaV) {
    return new LaunchWindow(
        T0.shiftedBy(opening),
        LaunchWindowCandidate.of(T0.shiftedBy(best), deltaV),
        T0.shiftedBy(closing));
  }

  @Test
  @DisplayName("The duration is the span between opening and closing")
  void durationSpansTheSlot() {
    assertEquals(Duration.ofSeconds(2400), window(600.0, 1800.0, 3000.0, 3100.0).duration());
  }

  @Test
  @DisplayName("The duration is rounded to the millisecond, not truncated to the second")
  void durationKeepsSubSecondResolution() {
    // A launch window can legitimately be seconds wide — an ISS plane alignment is minutes, a
    // refined edge lands wherever the bisection stopped. Truncating to the second would report a
    // one-second slot as zero-length and make it look shut.
    assertEquals(Duration.ofMillis(1700), window(0.0, 0.85, 1.7, 3100.0).duration());
  }

  @Test
  @DisplayName("A degenerate slot has zero duration rather than a negative one")
  void aPointSlotIsZeroLong() {
    assertEquals(Duration.ZERO, window(1200.0, 1200.0, 1200.0, 3100.0).duration());
  }

  @Test
  @DisplayName("The schedulable date is the cheapest instant, not the opening")
  void theDateIsTheBestEpochAndNotTheOpening() {
    // The distinction the record exists for: a countdown aims at the optimum, a timeline draws the
    // interval. Returning the opening here would schedule every mission at its worst affordable
    // instant.
    LaunchWindow w = window(600.0, 1800.0, 3000.0, 3100.0);

    assertEquals(T0.shiftedBy(1800.0), w.date());
    assertEquals(3100.0, w.best().deltaV());
  }
}
