package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
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
    return polylineOf(new int[] {n}, new boolean[] {false});
  }

  /**
   * A trail of consecutive runs: {@code lengths[k]} samples of stage {@code "S" + k}, whose
   * propulsive flag is {@code propulsive[k]}. Sample {@code i} sits at {@code (i, 0, 0)}.
   */
  private static TrajectoryPolyline polylineOf(int[] lengths, boolean[] propulsive) {
    int n = 0;
    for (int len : lengths) {
      n += len;
    }
    AbsoluteDate[] times = new AbsoluteDate[n];
    Vector3D[] positions = new Vector3D[n];
    String[] names = new String[n];
    boolean[] burns = new boolean[n];
    int i = 0;
    for (int k = 0; k < lengths.length; k++) {
      for (int j = 0; j < lengths[k]; j++, i++) {
        times[i] = T0.shiftedBy((double) i);
        positions[i] = new Vector3D(i, 0, 0);
        names[i] = "S" + k;
        burns[i] = propulsive[k];
      }
    }
    return TrajectoryPolyline.of(times, positions, names, burns);
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
                new MissionEphemerisPoint(T0, Vector3D.ZERO, Vector3D.PLUS_I, "S", false, 1.0, 0.0),
                new MissionEphemerisPoint(
                    T0.shiftedBy(1.0), Vector3D.PLUS_I, Vector3D.PLUS_I, "S", false, 1.0, 0.0)));

    assertSame(ephemeris.displayTrail(), ephemeris.displayTrail());
    assertEquals(2, ephemeris.displayTrail().size());
  }

  @Test
  void runsAreContiguousSegmentsOfTheSameStage() {
    TrajectoryPolyline trail = polylineOf(new int[] {10, 20, 5}, new boolean[] {true, false, true});

    List<PhaseRun> runs = trail.runs();
    assertEquals(3, runs.size());
    assertEquals("S0", runs.get(0).stageName());
    assertTrue(runs.get(0).propulsive());
    assertEquals(0, runs.get(0).firstVertex());
    assertEquals("S1", runs.get(1).stageName());
    assertFalse(runs.get(1).propulsive());
    assertEquals(10, runs.get(1).firstVertex());
    assertEquals(30, runs.get(2).firstVertex());
  }

  @Test
  void runOfIsParallelToPositions() {
    TrajectoryPolyline trail = polylineOf(new int[] {10, 20, 5}, new boolean[] {true, false, true});

    for (int i = 0; i < trail.size(); i++) {
      int expected = i < 10 ? 0 : i < 30 ? 1 : 2;
      int index = i;
      assertEquals(expected, trail.runOf(index), () -> "vertex " + index + " in the wrong run");
    }
  }

  /**
   * The regression this class exists to prevent, in its phase form. A vertical ascent is ~15 samples
   * at the 1 s burn step; on a long horizon the stride exceeds that, and a plain stride would delete
   * the whole phase from the drawn line, transition marker included.
   */
  @Test
  void aRunShorterThanTheStrideKeepsItsFirstVertex() {
    // One 15-sample burn, then a coast long enough to force a stride well above 15.
    TrajectoryPolyline trail = polylineOf(new int[] {15, 400_000}, new boolean[] {true, false});

    assertTrue(
        trail.size() <= TrajectoryPolyline.MAX_POINTS, () -> "budget exceeded: " + trail.size());

    List<PhaseRun> runs = trail.runs();
    assertEquals(2, runs.size(), "the short burn must not be swallowed by decimation");
    assertEquals(0, runs.get(0).firstVertex());
    assertEquals(
        new Vector3D(15, 0, 0),
        trail.positionAt(runs.get(1).firstVertex()),
        "run 1 must start on the raw sample where the stage actually changed");
  }

  @Test
  void everyRunStartIsAKeptVertexEvenWhenAllRunsAreShort() {
    int[] lengths = new int[40];
    boolean[] propulsive = new boolean[40];
    Arrays.fill(lengths, 5);
    lengths[39] = 300_000; // forces heavy decimation
    TrajectoryPolyline trail = polylineOf(lengths, propulsive);

    assertEquals(40, trail.runs().size());
    assertTrue(trail.size() <= TrajectoryPolyline.MAX_POINTS);
    for (PhaseRun run : trail.runs()) {
      assertEquals(
          run.stageName(),
          trail.runs().get(trail.runOf(run.firstVertex())).stageName(),
          "a run's first vertex must belong to that run");
    }
  }
}
