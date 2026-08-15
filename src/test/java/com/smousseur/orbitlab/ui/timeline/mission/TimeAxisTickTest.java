package com.smousseur.orbitlab.ui.timeline.mission;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/** Tick-step selection and label formatting for {@link TimeAxis}. */
class TimeAxisTickTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  private static TimeAxis axis(double durationSeconds) {
    return new TimeAxis(T0, T0.shiftedBy(durationSeconds), 14f, 572f);
  }

  @Test
  void aThreeDayMissionIsTickedEveryTwelveHours() {
    TimeAxis a = axis(3 * 86400.0);
    assertEquals(43200.0, a.tickStepSeconds(), 1e-9);
    assertEquals(7, a.majorTicks().size());
  }

  @Test
  void aShortAscentIsTickedEveryMinute() {
    TimeAxis a = axis(9 * 60.0);
    assertEquals(60.0, a.tickStepSeconds(), 1e-9);
    assertEquals(10, a.majorTicks().size());
  }

  @Test
  void aThreeHourWindowIsTickedEveryThirtyMinutes() {
    TimeAxis a = axis(3 * 3600.0);
    assertEquals(1800.0, a.tickStepSeconds(), 1e-9);
    assertEquals(7, a.majorTicks().size());
  }

  @Test
  void aThirtyDayWindowFallsInTheTableGapAndTakesTheSevenDayStep() {
    // 2 d would give 16 ticks, 7 d gives 5: the table has no value in between, so the count
    // lands under the 6..10 target. Documented, not accidental.
    TimeAxis a = axis(30 * 86400.0);
    assertEquals(7 * 86400.0, a.tickStepSeconds(), 1e-9);
    assertEquals(5, a.majorTicks().size());
  }

  @Test
  void noWindowEverExceedsTheTickBudget() {
    double[] durations = {30, 200, 600, 3600, 12 * 3600, 86400, 3 * 86400, 30 * 86400, 400 * 86400};
    for (double d : durations) {
      assertTrue(axis(d).majorTicks().size() <= TimeAxis.MAX_MAJOR_TICKS, "duration " + d);
    }
  }

  @Test
  void aFourHundredDayWindowExtendsTheLargestStepByWholeMultiples() {
    // 400 d overflows every fixed table entry (30 d still gives 14 ticks), so the fallback
    // doubles the 30 d step to 60 d, which is the first multiple that fits the budget.
    TimeAxis a = axis(400 * 86400.0);
    assertEquals(2 * 30 * 86400.0, a.tickStepSeconds(), 1e-6);
    assertEquals(7, a.majorTicks().size());
  }

  @Test
  void aDecadesLongWindowDoesNotOverflowTheTickCount() {
    // Regression test: at the smallest 1 s step, a multi-decade window's tick count exceeds
    // Integer.MAX_VALUE. An int-narrowed count used to wrap negative, which ArrayList's
    // constructor rejects with IllegalArgumentException instead of silently misbehaving.
    TimeAxis a = axis(70 * 365.25 * 86400.0);
    List<TimeAxis.Tick> ticks = assertDoesNotThrow(a::majorTicks);
    assertTrue(ticks.size() <= TimeAxis.MAX_MAJOR_TICKS, "tick count " + ticks.size());
  }

  @Test
  void ticksStartAtZeroAndAdvanceByTheStep() {
    TimeAxis a = axis(3 * 86400.0);
    List<TimeAxis.Tick> ticks = a.majorTicks();
    assertEquals(0.0, ticks.get(0).offsetSeconds(), 1e-9);
    assertEquals(43200.0, ticks.get(1).offsetSeconds(), 1e-9);
    assertEquals(14f, ticks.get(0).x(), 1e-4f);
  }

  @Test
  void majorTicksCarryLabelsMatchingTheirOwnOffset() {
    // formatTickLabel() is checked in isolation below; this pins the offset that majorTicks()
    // actually feeds it, so a wrong offset inside the loop would show up as a wrong label here.
    TimeAxis a = axis(3 * 86400.0);
    List<String> labels = a.majorTicks().stream().map(TimeAxis.Tick::label).toList();
    assertEquals(
        List.of("T+0", "T+12 h", "T+1 d", "T+1 d 12 h", "T+2 d", "T+2 d 12 h", "T+3 d"), labels);
  }

  @Test
  void tickLabelsUseAtMostTwoUnitsTruncatedDown() {
    assertEquals("T+0", TimeAxis.formatTickLabel(0));
    assertEquals("T+45 s", TimeAxis.formatTickLabel(45));
    assertEquals("T+12 min", TimeAxis.formatTickLabel(12 * 60));
    assertEquals("T+6 h", TimeAxis.formatTickLabel(6 * 3600));
    assertEquals("T+2 d 12 h", TimeAxis.formatTickLabel(2 * 86400 + 12 * 3600));
    assertEquals("T+1 min 30 s", TimeAxis.formatTickLabel(90));
    // Truncation is downward: 59.9 s is still 59 s, never 1 min.
    assertEquals("T+59 s", TimeAxis.formatTickLabel(59.9));
  }

  @Test
  void gapLabelsPadTheSecondUnitAndKeepOneDecimalUnderTenSeconds() {
    assertEquals("4.2 s", TimeAxis.formatGap(4.24));
    assertEquals("45 s", TimeAxis.formatGap(45.8));
    assertEquals("18 min 20 s", TimeAxis.formatGap(18 * 60 + 20));
    assertEquals("17 h 03 min", TimeAxis.formatGap(17 * 3600 + 3 * 60 + 40));
    assertEquals("3 d 04 h", TimeAxis.formatGap(3 * 86400 + 4 * 3600 + 59));
  }

  @Test
  void gapLabelsIgnoreSign() {
    assertEquals("45 s", TimeAxis.formatGap(-45));
  }
}
