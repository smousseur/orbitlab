package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * The display product's two guarantees (spec {@code
 * specs/mission-horizon/01-horizon-explicite.md} §6): it never exceeds the vertex budget, and it
 * always spans the whole flown trajectory — first and last sample included.
 *
 * <p>The second one is the regression under test. The renderer used to walk the ephemeris backwards
 * from the end and stop after the budget, so on a long mission the ascent silently vanished from the
 * drawn line. Any decimation is acceptable; dropping the beginning is not.
 */
class TrajectoryPolylineTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;

  /** {@code n} samples one second apart, position {@code i} sitting at {@code (i, 0, 0)}. */
  private static TrajectoryPolyline polylineOf(int n) {
    AbsoluteDate[] times = new AbsoluteDate[n];
    Vector3D[] positions = new Vector3D[n];
    for (int i = 0; i < n; i++) {
      times[i] = T0.shiftedBy((double) i);
      positions[i] = new Vector3D(i, 0, 0);
    }
    return TrajectoryPolyline.of(times, positions);
  }

  @Test
  void underTheBudget_everySampleIsKept() {
    TrajectoryPolyline trail = polylineOf(1_000);

    assertEquals(1_000, trail.size());
    assertEquals(new Vector3D(0, 0, 0), trail.positionAt(0));
    assertEquals(new Vector3D(999, 0, 0), trail.positionAt(999));
  }

  @Test
  void atTheBudget_nothingIsDropped() {
    assertEquals(TrajectoryPolyline.MAX_POINTS, polylineOf(TrajectoryPolyline.MAX_POINTS).size());
  }

  @Test
  void overTheBudget_sizeIsBoundedAndTheEndsSurvive() {
    int n = 43_200; // a 30-day horizon at the 60 s coast sampling step
    TrajectoryPolyline trail = polylineOf(n);

    assertTrue(
        trail.size() <= TrajectoryPolyline.MAX_POINTS,
        () -> "budget exceeded: " + trail.size() + " > " + TrajectoryPolyline.MAX_POINTS);
    assertEquals(new Vector3D(0, 0, 0), trail.positionAt(0), "the ascent must not be dropped");
    assertEquals(
        new Vector3D(n - 1, 0, 0),
        trail.positionAt(trail.size() - 1),
        "the trail must end where the spacecraft is");
  }

  /** Decimation must stay monotonic in time, or {@code indexUpTo}'s binary search is meaningless. */
  @Test
  void overTheBudget_timesStayIncreasing() {
    TrajectoryPolyline trail = polylineOf(43_200);

    for (int i = 1; i < trail.size(); i++) {
      int index = i;
      assertTrue(
          trail.timeAt(index).compareTo(trail.timeAt(index - 1)) > 0,
          () -> "times must strictly increase, broke at " + index);
    }
  }

  @Test
  void indexUpTo_landsOnTheLastVertexAtOrBeforeTheDate() {
    TrajectoryPolyline trail = polylineOf(100);

    assertEquals(0, trail.indexUpTo(T0));
    assertEquals(42, trail.indexUpTo(T0.shiftedBy(42.0)));
    assertEquals(42, trail.indexUpTo(T0.shiftedBy(42.7)), "a date between samples takes the floor");
  }

  @Test
  void indexUpTo_clampsAtBothEnds() {
    TrajectoryPolyline trail = polylineOf(100);

    assertEquals(0, trail.indexUpTo(T0.shiftedBy(-5_000.0)));
    assertEquals(99, trail.indexUpTo(T0.shiftedBy(5_000.0)));
  }

  /** A renderer holds the trail across frames, so the ephemeris must hand out one shared instance. */
  @Test
  void missionEphemeris_returnsTheSameTrailEveryTime() {
    MissionEphemeris ephemeris =
        new MissionEphemeris(
            java.util.List.of(
                new MissionEphemerisPoint(T0, Vector3D.ZERO, Vector3D.PLUS_I, "S", 1.0, 0.0),
                new MissionEphemerisPoint(
                    T0.shiftedBy(1.0), Vector3D.PLUS_I, Vector3D.PLUS_I, "S", 1.0, 0.0)));

    assertSame(ephemeris.displayTrail(), ephemeris.displayTrail());
    assertEquals(2, ephemeris.displayTrail().size());
  }
}
