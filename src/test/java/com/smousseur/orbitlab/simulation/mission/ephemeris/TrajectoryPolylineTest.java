package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.Arrays;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * The display product's two guarantees (spec {@code docs/mission-horizon/01-horizon-explicite.md}
 * §6): it never exceeds the vertex budget, and it always spans the whole flown trajectory — first
 * and last sample included.
 *
 * <p>The second one is the regression under test. The renderer used to walk the ephemeris backwards
 * from the end and stop after the budget, so on a long mission the ascent silently vanished from
 * the drawn line. Any decimation is acceptable; dropping the beginning is not.
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
    return polylineOf(lengths, propulsive, null);
  }

  /**
   * As above, with the arc column chosen by the caller. {@code arcBoundaries} lists the raw sample
   * indices at which a new arc opens; {@code null} means one arc for the whole trail, which is what
   * every mission produces until L4.
   */
  private static TrajectoryPolyline polylineOf(
      int[] lengths, boolean[] propulsive, int[] arcBoundaries) {
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
    return TrajectoryPolyline.of(times, positions, names, burns, arcsOf(n, arcBoundaries));
  }

  /**
   * An arc column: the Earth arc up to the first boundary, then an alternate arc after each one.
   * Two distinct arcs suffice — what the partition reads is {@link TrajectoryArc#equals}, not which
   * body it names.
   */
  private static TrajectoryArc[] arcsOf(int n, int[] boundaries) {
    TrajectoryArc[] arcs = new TrajectoryArc[n];
    Arrays.fill(arcs, TrajectoryArc.earth());
    if (boundaries == null) {
      return arcs;
    }
    TrajectoryArc other = new TrajectoryArc(SolarSystemBody.MOON, TrajectoryArc.earth().frame());
    for (int b = 0; b < boundaries.length; b++) {
      int end = b + 1 < boundaries.length ? boundaries[b + 1] : n;
      TrajectoryArc arc = b % 2 == 0 ? other : TrajectoryArc.earth();
      Arrays.fill(arcs, boundaries[b], end, arc);
    }
    return arcs;
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

  /**
   * The exact vertex selection, pinned — the net for the decimation budget formula (spec {@code
   * docs/multi-corps/05-conception-L3.md} §4.1).
   *
   * <p>The other over-budget tests here assert bounds and endpoints, which a stride that shifted by
   * one would still satisfy. This one pins the stride itself, and the fixture is sized to make a
   * single lost slot of budget impossible to miss: {@code n} is exactly twice the budget a
   * single-run trail gets, so the stride sits on the knife edge. Losing one slot takes {@code
   * ceil(n / budget)} from 2 to 3 and roughly a third of the vertices with it.
   *
   * <p>That matters because L3 adds a second set of forced vertices — the arc starts — and the
   * budget must reserve headroom for the <b>union</b> of run starts and arc starts, not for their
   * sum. With a single arc the union changes nothing, so this test must read identically before and
   * after the lot; written as a sum, it would not.
   */
  @Test
  void overTheBudget_theKeptVerticesArePinned() {
    // One run, so the budget is MAX_POINTS - 1 run start - 1 final sample.
    int budget = TrajectoryPolyline.MAX_POINTS - 2;
    TrajectoryPolyline trail = polylineOf(2 * budget);

    assertEquals(budget + 1, trail.size(), "the stride moved");
    assertEquals(new Vector3D(0, 0, 0), trail.positionAt(0));
    assertEquals(new Vector3D(2, 0, 0), trail.positionAt(1), "stride of 2");
    assertEquals(new Vector3D(4, 0, 0), trail.positionAt(2));
    assertEquals(
        new Vector3D(2 * budget - 2, 0, 0),
        trail.positionAt(trail.size() - 2),
        "the last strided vertex");
    assertEquals(
        new Vector3D(2 * budget - 1, 0, 0),
        trail.positionAt(trail.size() - 1),
        "the forced final sample");
  }

  /**
   * Decimation must stay monotonic in time, or {@code indexUpTo}'s binary search is meaningless.
   */
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

  /**
   * A renderer holds the trail across frames, so the ephemeris must hand out one shared instance.
   */
  @Test
  void missionEphemeris_returnsTheSameTrailEveryTime() {
    MissionEphemeris ephemeris =
        new MissionEphemeris(
            java.util.List.of(
                new MissionEphemerisPoint(
                    T0,
                    Vector3D.ZERO,
                    Vector3D.PLUS_I,
                    "S",
                    false,
                    1.0,
                    0.0,
                    TrajectoryArc.earth()),
                new MissionEphemerisPoint(
                    T0.shiftedBy(1.0),
                    Vector3D.PLUS_I,
                    Vector3D.PLUS_I,
                    "S",
                    false,
                    1.0,
                    0.0,
                    TrajectoryArc.earth())));

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
   * The regression this class exists to prevent, in its phase form. A vertical ascent is ~15
   * samples at the 1 s burn step; on a long horizon the stride exceeds that, and a plain stride
   * would delete the whole phase from the drawn line, transition marker included.
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

  // ════════════════════════════════════════════════════════════════════════
  // Arcs — the second partition (PHY-4 / L3, spec docs/multi-corps/05-conception-L3.md §4)
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The degenerate case, and the only one production reaches until L4: one arc for the whole line.
   */
  @Test
  void aSingleArcSpansTheWholeTrail() {
    TrajectoryPolyline trail = polylineOf(new int[] {10, 20, 5}, new boolean[] {true, false, true});

    assertEquals(1, trail.arcs().size());
    assertEquals(TrajectoryArc.earth(), trail.arcs().get(0).arc());
    assertEquals(0, trail.arcs().get(0).firstVertex());
    assertEquals(trail.size(), trail.arcs().get(0).vertexCount());
    for (int i = 0; i < trail.size(); i++) {
      int index = i;
      assertEquals(0, trail.arcOf(index), () -> "vertex " + index + " left the only arc");
    }
  }

  /**
   * An arc boundary is a change of frame: a stride that skipped it would join two vertices
   * expressed about different bodies with a straight segment. The boundary sits at an odd raw index
   * a stride of this size cannot land on by chance.
   */
  @Test
  void anArcBoundaryIsKeptEvenWhenTheStrideWouldSkipIt() {
    int n = 400_000;
    int boundary = 123_457;
    TrajectoryPolyline trail =
        polylineOf(new int[] {n}, new boolean[] {false}, new int[] {boundary});

    assertTrue(trail.size() <= TrajectoryPolyline.MAX_POINTS, "budget exceeded");
    assertEquals(2, trail.arcs().size(), "the crossing must not be decimated away");
    assertEquals(
        new Vector3D(boundary, 0, 0),
        trail.positionAt(trail.arcs().get(1).firstVertex()),
        "the second arc must open on the raw sample where the frame actually changed");
  }

  /**
   * The reason arcs are a partition of their own rather than a term added to the run criterion: a
   * sphere-of-influence crossing falls inside a phase, and folding it into the runs would split
   * that phase into two homonymous ones — two transition markers drawn, one phase too many
   * reported by the timeline.
   */
  @Test
  void arcsAndRunsArePartitionedIndependently() {
    // Runs open at 0, 10 and 30; the arc changes at 15, inside the second run.
    TrajectoryPolyline trail =
        polylineOf(new int[] {10, 20, 5}, new boolean[] {true, false, true}, new int[] {15});

    assertEquals(3, trail.runs().size(), "the arc boundary must not open a run");
    assertEquals(2, trail.arcs().size());
    assertEquals(15, trail.arcs().get(1).firstVertex());
    assertEquals(1, trail.runOf(15), "vertex 15 is still inside the second run");

    for (int i = 0; i < trail.size(); i++) {
      int index = i;
      assertEquals(i < 15 ? 0 : 1, trail.arcOf(index), () -> "vertex " + index + " in the wrong arc");
    }
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
