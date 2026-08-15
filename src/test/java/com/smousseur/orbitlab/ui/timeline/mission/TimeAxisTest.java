package com.smousseur.orbitlab.ui.timeline.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/** Projection tests for {@link TimeAxis}: they exercise no JME code and no Orekit data files. */
class TimeAxisTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  private static TimeAxis axis(double durationSeconds) {
    return new TimeAxis(T0, T0.shiftedBy(durationSeconds), 14f, 572f);
  }

  @Test
  void startMapsToLeftEdgeAndEndToRightEdge() {
    TimeAxis a = axis(3600);
    assertEquals(14f, a.timeToX(T0), 1e-4f);
    assertEquals(586f, a.timeToX(T0.shiftedBy(3600)), 1e-4f);
  }

  @Test
  void midpointMapsToTrackCentre() {
    TimeAxis a = axis(3600);
    assertEquals(300f, a.timeToX(T0.shiftedBy(1800)), 1e-3f);
  }

  @Test
  void positionsBeforeStartAndAfterEndAreClamped() {
    TimeAxis a = axis(3600);
    assertEquals(14f, a.timeToX(T0.shiftedBy(-99999)), 1e-4f);
    assertEquals(586f, a.timeToX(T0.shiftedBy(99999)), 1e-4f);
  }

  @Test
  void xToTimeIsTheInverseOfTimeToX() {
    TimeAxis a = axis(259200);
    for (double offset : new double[] {0, 1, 4242, 129600, 259199, 259200}) {
      AbsoluteDate t = T0.shiftedBy(offset);
      double roundTrip = a.xToTime(a.timeToX(t)).durationFrom(T0);
      // One pixel spans D/W seconds; the round trip cannot be finer than that.
      assertEquals(offset, roundTrip, a.durationSeconds() / 572.0 + 1e-6);
    }
  }

  @Test
  void xToTimeClampsOutsideTheTrack() {
    TimeAxis a = axis(3600);
    assertEquals(0.0, a.xToTime(-500f).durationFrom(T0), 1e-6);
    assertEquals(3600.0, a.xToTime(5000f).durationFrom(T0), 1e-6);
  }

  @Test
  void aVeryShortWindowStillProjectsWithoutDividingByZero() {
    TimeAxis a = axis(0.001);
    assertEquals(14f, a.timeToX(T0), 1e-4f);
    assertEquals(586f, a.timeToX(T0.shiftedBy(0.001)), 1e-4f);
  }

  @Test
  void aVeryLongWindowProjectsLinearly() {
    double year = 365 * 86400.0;
    TimeAxis a = axis(year);
    assertEquals(300f, a.timeToX(T0.shiftedBy(year / 2)), 1e-2f);
  }

  @Test
  void aNonPositiveDurationIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new TimeAxis(T0, T0, 14f, 572f));
    assertThrows(
        IllegalArgumentException.class, () -> new TimeAxis(T0, T0.shiftedBy(-1), 14f, 572f));
  }

  @Test
  void aNonPositiveWidthIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new TimeAxis(T0, T0.shiftedBy(60), 14f, 0f));
  }
}
