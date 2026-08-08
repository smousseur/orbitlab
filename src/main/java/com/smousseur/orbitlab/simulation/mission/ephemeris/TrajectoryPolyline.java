package com.smousseur.orbitlab.simulation.mission.ephemeris;

import java.util.Arrays;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * The drawable form of a mission trajectory: a bounded, time-indexed list of positions, built once
 * when the ephemeris is built.
 *
 * <p><b>Why this is a separate product.</b> One array used to serve two consumers with incompatible
 * needs (spec {@code specs/mission-horizon/01-horizon-explicite.md} §6). The flight recorder —
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
   * mission at the 1 s / 60 s sampling steps), so nothing is dropped. Decimation only engages on the
   * long horizons a user sets by hand.
   */
  public static final int MAX_POINTS = 8192;

  private final Vector3D[] positions;
  private final AbsoluteDate[] times;

  private TrajectoryPolyline(Vector3D[] positions, AbsoluteDate[] times) {
    this.positions = positions;
    this.times = times;
  }

  /**
   * Builds the drawable form of the given samples, decimating at a constant stride when they exceed
   * {@link #MAX_POINTS}. The first and last samples are always kept, so the drawn line spans the
   * whole flown trajectory whatever the horizon.
   *
   * <p>The arrays are copied, not aliased: the caller keeps ownership of its own storage.
   *
   * @param times the sample times, sorted ascending
   * @param positions the sample positions, parallel to {@code times}
   * @return the decimated polyline
   */
  static TrajectoryPolyline of(AbsoluteDate[] times, Vector3D[] positions) {
    Objects.requireNonNull(times, "times");
    Objects.requireNonNull(positions, "positions");
    int n = times.length;
    if (n <= MAX_POINTS) {
      return new TrajectoryPolyline(positions.clone(), times.clone());
    }

    int stride = (n + MAX_POINTS - 1) / MAX_POINTS;
    int kept = (n + stride - 1) / stride; // <= MAX_POINTS by construction
    Vector3D[] p = new Vector3D[kept];
    AbsoluteDate[] t = new AbsoluteDate[kept];
    for (int i = 0; i < kept; i++) {
      int src = i * stride;
      p[i] = positions[src];
      t[i] = times[src];
    }
    // Overwrite the final slot rather than append one: the trail must end on the real last sample
    // (that is where the spacecraft is), and doing it in place keeps the count within the budget.
    p[kept - 1] = positions[n - 1];
    t[kept - 1] = times[n - 1];
    return new TrajectoryPolyline(p, t);
  }

  /**
   * Index of the last vertex at or before {@code date} — the end of the prefix to draw.
   *
   * <p>Allocation-free, which is the point: this is called once per frame per visible mission, where
   * the previous API allocated a fresh list of up to 86 400 positions each time.
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
   * Returns the vertex at the given index, in GCRF meters.
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
}
