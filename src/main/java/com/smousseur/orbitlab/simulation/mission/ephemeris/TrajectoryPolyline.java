package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * The drawable form of a mission trajectory: a bounded, time-indexed list of positions, built once
 * when the ephemeris is built.
 *
 * <p><b>Why this is a separate product.</b> One array used to serve two consumers with incompatible
 * needs (spec {@code docs/mission-horizon/01-horizon-explicite.md} §6). The flight recorder —
 * telemetry, the completeness verdict, post-flight analysis — wants fidelity wherever the dynamics
 * are fast, and that is {@link MissionEphemeris}. The display polyline wants at most a few thousand
 * points, because the screen is ~2000 px wide, and that is this class.
 *
 * <p>Keeping them merged had already produced a silent defect: the renderer walked the ephemeris
 * <em>backwards</em> from the end and stopped after {@code MAX_POINTS}, so on any mission longer
 * than the budget the ascent simply vanished from the drawn line. That was a truncation from the
 * start, not a decimation, and nobody chose it.
 *
 * <p>Immutable and safe to hand to the render thread: the arrays are never published and never
 * mutated after construction.
 */
public final class TrajectoryPolyline {

  /**
   * Vertex budget for a drawn trajectory, and the single source of truth for it — {@code
   * MissionTrajectoryRenderer} sizes its vertex buffer from this value rather than declaring its
   * own.
   *
   * <p>At the derived default horizon the raw ephemeris already fits (~5 200 points for a 3-day LEO
   * mission at the 1 s / 60 s sampling steps), so nothing is dropped. Decimation only engages on
   * the long horizons a user sets by hand.
   */
  public static final int MAX_POINTS = 8192;

  private final Vector3D[] positions;
  private final AbsoluteDate[] times;
  private final short[] runOf;
  private final List<PhaseRun> runs;
  private final short[] arcOf;
  private final List<ArcRun> arcs;

  private TrajectoryPolyline(
      Vector3D[] positions,
      AbsoluteDate[] times,
      short[] runOf,
      List<PhaseRun> runs,
      short[] arcOf,
      List<ArcRun> arcs) {
    this.positions = positions;
    this.times = times;
    this.runOf = runOf;
    this.runs = runs;
    this.arcOf = arcOf;
    this.arcs = arcs;
  }

  /**
   * Builds the drawable form of the given samples, decimating when they exceed {@link #MAX_POINTS}.
   * The first and last samples are always kept, so the drawn line spans the whole flown trajectory
   * whatever the horizon.
   *
   * <p><b>Every phase run also keeps its first sample</b>, whatever the stride. A short propulsive
   * phase — a vertical ascent is ~15 samples at the 1 s burn step — is shorter than the stride on a
   * long horizon, and a plain stride would delete it from the drawn line together with its
   * transition marker. That is the same class of silent truncation as the one this class was
   * extracted to fix. The stride is therefore computed against a budget reduced by the number of
   * runs, so the forced vertices cannot push the result over {@link #MAX_POINTS}.
   *
   * <p><b>Every arc start is forced too</b>, for a different reason: an arc boundary is a change of
   * frame, so a stride that skipped it would join two vertices expressed about different bodies with
   * a straight segment. The headroom reserved for the forced vertices is therefore computed on the
   * <b>union</b> of the run starts and the arc starts, never on their sum — with a single arc the
   * arc start is vertex 0, which is already a run start, so the union is the run starts and the
   * decimation is bit-for-bit what it was before PHY-4 / L3 (spec {@code
   * docs/multi-corps/05-conception-L3.md} §4.1). Written as a sum, the budget would lose a slot and
   * the stride could shift on any trajectory near a multiple of the budget.
   *
   * <p>The arrays are copied, not aliased: the caller keeps ownership of its own storage.
   *
   * @param times the sample times, sorted ascending
   * @param positions the sample positions, parallel to {@code times}
   * @param stageNames the stage name of each sample, parallel to {@code times}
   * @param propulsive whether each sample's stage burns propellant, parallel to {@code times}
   * @param arcs the frame each sample is expressed in, parallel to {@code times}
   * @return the decimated polyline
   */
  static TrajectoryPolyline of(
      AbsoluteDate[] times,
      Vector3D[] positions,
      String[] stageNames,
      boolean[] propulsive,
      TrajectoryArc[] arcs) {
    Objects.requireNonNull(times, "times");
    Objects.requireNonNull(positions, "positions");
    Objects.requireNonNull(stageNames, "stageNames");
    Objects.requireNonNull(propulsive, "propulsive");
    Objects.requireNonNull(arcs, "arcs");
    int n = times.length;

    int[] runStart = rawRunStarts(stageNames, propulsive);
    int[] arcStart = rawArcStarts(arcs);

    if (n <= MAX_POINTS) {
      return build(
          positions.clone(),
          times.clone(),
          stageNames,
          propulsive,
          arcs,
          runStart,
          arcStart,
          identity(n));
    }

    // Headroom for the forced vertices: at most one per distinct forced index plus the final
    // sample. Without it the forced adds could push the kept count past the budget the renderer
    // sizes its buffer from. The union, not the sum — see the method javadoc.
    int[] forced = union(runStart, arcStart);
    int budget = Math.max(1, MAX_POINTS - forced.length - 1);
    int stride = (n + budget - 1) / budget;

    boolean[] keep = new boolean[n];
    for (int i = 0; i < n; i += stride) {
      keep[i] = true;
    }
    for (int start : forced) {
      keep[start] = true;
    }
    // The trail must end on the real last sample: that is where the spacecraft is.
    keep[n - 1] = true;

    int kept = 0;
    for (boolean k : keep) {
      if (k) {
        kept++;
      }
    }
    int[] srcOf = new int[kept];
    Vector3D[] p = new Vector3D[kept];
    AbsoluteDate[] t = new AbsoluteDate[kept];
    for (int i = 0, j = 0; i < n; i++) {
      if (!keep[i]) {
        continue;
      }
      srcOf[j] = i;
      p[j] = positions[i];
      t[j] = times[i];
      j++;
    }
    return build(p, t, stageNames, propulsive, arcs, runStart, arcStart, srcOf);
  }

  /** Raw sample indices at which a new run begins; always starts with 0. */
  private static int[] rawRunStarts(String[] stageNames, boolean[] propulsive) {
    int n = stageNames.length;
    int[] starts = new int[n];
    int count = 0;
    starts[count++] = 0;
    for (int i = 1; i < n; i++) {
      if (!stageNames[i].equals(stageNames[i - 1]) || propulsive[i] != propulsive[i - 1]) {
        starts[count++] = i;
      }
    }
    return Arrays.copyOf(starts, count);
  }

  /**
   * Raw sample indices at which a new arc begins; always starts with 0.
   *
   * <p>Compared by {@code equals} and not by identity: that is what makes {@link TrajectoryArc}'s
   * equality the definition of an arc boundary, and why it carries only the body and the frame.
   */
  private static int[] rawArcStarts(TrajectoryArc[] arcs) {
    int n = arcs.length;
    int[] starts = new int[n];
    int count = 0;
    starts[count++] = 0;
    for (int i = 1; i < n; i++) {
      if (!arcs[i].equals(arcs[i - 1])) {
        starts[count++] = i;
      }
    }
    return Arrays.copyOf(starts, count);
  }

  /**
   * Merges two ascending index arrays, dropping duplicates. Both always begin with 0, so the result
   * of a single-arc trajectory is exactly {@code runStart}.
   */
  private static int[] union(int[] a, int[] b) {
    int[] merged = new int[a.length + b.length];
    int i = 0, j = 0, k = 0;
    while (i < a.length || j < b.length) {
      int next;
      if (j == b.length || (i < a.length && a[i] <= b[j])) {
        next = a[i++];
      } else {
        next = b[j++];
      }
      if (k == 0 || merged[k - 1] != next) {
        merged[k++] = next;
      }
    }
    return Arrays.copyOf(merged, k);
  }

  private static int[] identity(int n) {
    int[] src = new int[n];
    for (int i = 0; i < n; i++) {
      src[i] = i;
    }
    return src;
  }

  /**
   * Assembles the polyline once the kept vertices are chosen. {@code srcOf[j]} is the raw sample
   * index behind kept vertex {@code j}, which is what lets a start's raw index be remapped onto the
   * decimated indexing. Because every start is force-kept, each stretch opens on exactly the kept
   * vertex that <em>is</em> that sample, so {@link PhaseRun#firstVertex()} and {@link
   * ArcRun#firstVertex()} land on the transition itself rather than on the strided vertex before or
   * after it.
   *
   * <p>The two partitions are computed by the same {@link #partition} pass over the same {@code
   * srcOf}, which is what keeps them independent: neither is derived from the other.
   */
  private static TrajectoryPolyline build(
      Vector3D[] p,
      AbsoluteDate[] t,
      String[] stageNames,
      boolean[] propulsive,
      TrajectoryArc[] sampleArcs,
      int[] runStart,
      int[] arcStart,
      int[] srcOf) {
    Partition runPart = partition(runStart, srcOf, p.length);
    Partition arcPart = partition(arcStart, srcOf, p.length);

    // A span is only known once the next stretch has opened, so the records are emitted in a second
    // pass rather than as each one starts.
    List<PhaseRun> runs = new ArrayList<>(runPart.opened());
    for (int r = 0; r < runPart.opened(); r++) {
      int raw = runStart[r];
      runs.add(
          new PhaseRun(
              stageNames[raw],
              propulsive[raw],
              runPart.firstVertex()[r],
              runPart.spanOf(r, p.length)));
    }

    List<ArcRun> arcs = new ArrayList<>(arcPart.opened());
    for (int a = 0; a < arcPart.opened(); a++) {
      arcs.add(
          new ArcRun(
              sampleArcs[arcStart[a]], arcPart.firstVertex()[a], arcPart.spanOf(a, p.length)));
    }

    return new TrajectoryPolyline(
        p, t, runPart.indexOf(), List.copyOf(runs), arcPart.indexOf(), List.copyOf(arcs));
  }

  /**
   * One partition of the kept vertices: which stretch each belongs to, and where each stretch opens.
   *
   * @param indexOf the stretch index of every kept vertex
   * @param firstVertex the opening vertex of each stretch, valid up to {@code opened}
   * @param opened how many stretches actually opened
   */
  private record Partition(short[] indexOf, int[] firstVertex, int opened) {
    int spanOf(int stretch, int vertexCount) {
      int end = stretch + 1 < opened ? firstVertex[stretch + 1] : vertexCount;
      return end - firstVertex[stretch];
    }
  }

  /** Maps raw start indices onto the decimated indexing. */
  private static Partition partition(int[] rawStarts, int[] srcOf, int vertexCount) {
    short[] indexOf = new short[vertexCount];
    int[] firstVertex = new int[rawStarts.length];
    int opened = 0;
    int current = -1;
    int next = 0;
    for (int j = 0; j < vertexCount; j++) {
      while (next < rawStarts.length && srcOf[j] >= rawStarts[next]) {
        current = next;
        firstVertex[opened++] = j;
        next++;
      }
      indexOf[j] = (short) current;
    }
    return new Partition(indexOf, firstVertex, opened);
  }

  /**
   * Index of the last vertex at or before {@code date} — the end of the prefix to draw.
   *
   * <p>Allocation-free, which is the point: this is called once per frame per visible mission,
   * where the previous API allocated a fresh list of up to 86 400 positions each time.
   *
   * @param date the current simulation date
   * @return an index within {@code [0, size() - 1]}, clamped at both ends
   */
  public int indexUpTo(AbsoluteDate date) {
    int idx = Arrays.binarySearch(times, date);
    if (idx >= 0) {
      return idx;
    }
    // -idx - 1 is the first vertex strictly after the date; the prefix ends just before it.
    return Math.max(0, -idx - 2);
  }

  /**
   * @return the number of vertices, at least 1
   */
  public int size() {
    return positions.length;
  }

  /**
   * Returns the vertex at the given index, in meters, expressed in the frame of {@link
   * #arcOf(int)} — GCRF for as long as nothing produces a second arc.
   *
   * @param index the vertex index
   * @return the position
   */
  public Vector3D positionAt(int index) {
    return positions[index];
  }

  /**
   * Returns the time of the vertex at the given index.
   *
   * @param index the vertex index
   * @return the sample time
   */
  public AbsoluteDate timeAt(int index) {
    return times[index];
  }

  /**
   * The phase runs of this trajectory, in flight order. Never empty, and {@code
   * runs().get(0).firstVertex()} is always 0.
   *
   * @return the runs, unmodifiable
   */
  public List<PhaseRun> runs() {
    return runs;
  }

  /**
   * The run a vertex belongs to.
   *
   * @param index the vertex index
   * @return an index into {@link #runs()}
   */
  public int runOf(int index) {
    return runOf[index];
  }

  /**
   * The arcs of this trajectory, in flight order — one per change of central body. Never empty, and
   * {@code arcs().get(0).firstVertex()} is always 0.
   *
   * <p>Independent of {@link #runs()}: an arc boundary need not be a phase boundary, and is not one
   * in the case that motivates this partition (spec {@code docs/multi-corps/05-conception-L3.md}
   * §4).
   *
   * @return the arcs, unmodifiable
   */
  public List<ArcRun> arcs() {
    return arcs;
  }

  /**
   * The arc a vertex belongs to, and therefore the frame {@link #positionAt(int)} returns it in.
   *
   * @param index the vertex index
   * @return an index into {@link #arcs()}
   */
  public int arcOf(int index) {
    return arcOf[index];
  }
}
