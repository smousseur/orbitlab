package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  private final AbsoluteDate[] times;
  private final short[] runOf;
  private final List<PhaseRun> runs;
  private final short[] arcOf;
  private final List<ArcRun> arcs;

  /**
   * The vertices, one table per body they can be drawn about — every distinct arc body of this
   * trajectory, and no other (spec {@code docs/multi-corps/07-conception-L5.md} §3.4).
   *
   * <p>A trajectory of a single arc holds <b>one</b> table, and it is the sampled array itself, not
   * a copy: the identity is what makes L5 a structural non-regression rather than a measured one,
   * and it is also why a single-arc polyline never touches Orekit.
   */
  private final Map<SolarSystemBody, Vector3D[]> positionsByRenderBody;

  private TrajectoryPolyline(
      Map<SolarSystemBody, Vector3D[]> positionsByRenderBody,
      AbsoluteDate[] times,
      short[] runOf,
      List<PhaseRun> runs,
      short[] arcOf,
      List<ArcRun> arcs) {
    this.positionsByRenderBody = positionsByRenderBody;
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
   * <p><b>Both sides of every arc boundary are forced too</b>, so that each arc's vertex range
   * actually contains its own boundary rather than stopping up to a stride short of it (spec {@code
   * docs/multi-corps/07-conception-L5.md} §4.1). The headroom reserved for the forced vertices is
   * therefore computed on the <b>union</b> of the run starts and the arc boundaries, never on their
   * sum — with a single arc the arc start is vertex 0, which is already a run start, so the union
   * is the run starts and the decimation is bit-for-bit what it was before PHY-4 / L3 (spec {@code
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
    int[] forced = union(runStart, arcBoundaries(arcStart));
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
   * Both sides of every arc boundary: each arc's first sample, and the last sample of the arc
   * before it. Ascending and deduplicated; for a single arc it is {@code {0}}, which is already a
   * run start, so the union below is unchanged and so is the stride.
   *
   * <p><b>Why the outgoing side is forced too</b> (spec {@code
   * docs/multi-corps/07-conception-L5.md} §4.1). L4 §5 flagged this as a debt on the grounds that a
   * decimated trace would otherwise join two vertices expressed about different bodies with a
   * straight segment. That reason is now void: L5 converts every vertex into the render body's
   * frame, so the segment across a boundary is geometrically sound, merely coarser. What forcing it
   * still buys is that {@link ArcRun#vertexCount()} of the outgoing arc actually contains its own
   * boundary — a written debt one chooses not to pay gets paid twice.
   */
  private static int[] arcBoundaries(int[] arcStart) {
    int[] both = new int[arcStart.length * 2];
    int count = 0;
    for (int i = 0; i < arcStart.length; i++) {
      if (i > 0 && arcStart[i] - 1 > both[count - 1]) {
        both[count++] = arcStart[i] - 1;
      }
      both[count++] = arcStart[i];
    }
    return Arrays.copyOf(both, count);
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
        renderTables(p, t, arcPart.indexOf(), arcs),
        t,
        runPart.indexOf(),
        List.copyOf(runs),
        arcPart.indexOf(),
        List.copyOf(arcs));
  }

  /**
   * One table of vertices per distinct arc body: the whole trajectory expressed about that body,
   * ready to be drawn without any per-frame conversion.
   *
   * <p><b>The single-body case returns the sampled array itself.</b> No copy, no Orekit call, no
   * arithmetic — which is what makes every trajectory that exists today bit-for-bit what it was, by
   * identity of reference rather than by a measured equality (spec {@code
   * docs/multi-corps/07-conception-L5.md} §3.4). It is also what keeps the four test classes that
   * build polylines without initialising {@code OrekitService} working.
   *
   * <p>Beyond one body the conversion is a pure translation between two body-centred ICRF frames,
   * done here — once, at build time, off the render thread — rather than per frame. The render
   * thread cannot do it: {@code EphemerisConfig} buffers 33 h back and 66 h forward, while a lunar
   * transfer's trace spans three to five days, so a per-frame lookup through {@code
   * EphemerisService} would silently fail on the oldest vertices (spec §1.7).
   */
  private static Map<SolarSystemBody, Vector3D[]> renderTables(
      Vector3D[] p, AbsoluteDate[] t, short[] arcOf, List<ArcRun> arcs) {
    Map<SolarSystemBody, TrajectoryArc> targets = new EnumMap<>(SolarSystemBody.class);
    for (ArcRun run : arcs) {
      targets.putIfAbsent(run.arc().body(), run.arc());
    }
    if (targets.size() == 1) {
      Map.Entry<SolarSystemBody, TrajectoryArc> only = targets.entrySet().iterator().next();
      return Map.of(only.getKey(), p);
    }

    Map<SolarSystemBody, Vector3D[]> tables = new EnumMap<>(SolarSystemBody.class);
    for (Map.Entry<SolarSystemBody, TrajectoryArc> target : targets.entrySet()) {
      Vector3D[] table = new Vector3D[p.length];
      for (int j = 0; j < p.length; j++) {
        table[j] = arcs.get(arcOf[j]).arc().convertPosition(p[j], t[j], target.getValue());
      }
      tables.put(target.getKey(), table);
    }
    return Map.copyOf(tables);
  }

  /**
   * One partition of the kept vertices: which stretch each belongs to, and where each stretch
   * opens.
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
    return times.length;
  }

  /**
   * Returns the vertex at the given index, in meters, expressed about {@code renderBody} — that is,
   * in that body's ICRF-oriented inertial frame, whatever arc the vertex was flown in.
   *
   * <p><b>There is deliberately no single-argument overload.</b> The whole point of L5 is that a
   * vertex no longer has one true frame, and while an Earth-implicit overload existed a forgotten
   * call site would compile and draw a lunar arc about the Earth in silence. Same rule, same
   * reason, as the one L3 §2.2 applied to {@code MissionEphemerisPoint}.
   *
   * @param index the vertex index
   * @param renderBody the body every drawn coordinate of this frame is expressed about
   * @return the position, in {@code renderBody}'s frame
   * @throws IllegalArgumentException if this trajectory has no arc about that body — a render path
   *     asking for one must fail loudly rather than be served the wrong table. {@code
   *     FocusView.isMissionVisible} is what guarantees the case does not arise.
   */
  public Vector3D positionAt(int index, SolarSystemBody renderBody) {
    Vector3D[] table = positionsByRenderBody.get(renderBody);
    if (table == null) {
      throw new IllegalArgumentException(
          "this trajectory has no arc about " + renderBody + "; it flies " + arcBodies());
    }
    return table[index];
  }

  /**
   * The bodies this trajectory can be drawn about: one per distinct arc central body.
   *
   * <p>This is what decides whether the mission belongs on screen at all — a lunar transfer is
   * legitimate viewed from the Earth, whose arc it starts in, as well as from the Moon (spec {@code
   * docs/multi-corps/07-conception-L5.md} §5.4).
   *
   * @return the render bodies, never empty
   */
  public Set<SolarSystemBody> arcBodies() {
    return positionsByRenderBody.keySet();
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
