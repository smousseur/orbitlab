package com.smousseur.orbitlab.simulation.mission.ephemeris;

import com.smousseur.orbitlab.simulation.ephemeris.EphemerisInterpolator;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * Pre-computed ephemeris of an entire mission trajectory. Points are stored sorted by time in
 * parallel arrays.
 *
 * <p>Interpolation reuses {@link EphemerisInterpolator#findInterval}, {@link
 * EphemerisInterpolator#computeTau}, {@link EphemerisInterpolator#hermitePosition}, and {@link
 * EphemerisInterpolator#hermiteVelocity} — the same utilities used by {@link
 * com.smousseur.orbitlab.simulation.ephemeris.SlidingWindowEphemerisBuffer} for planetary
 * ephemerides.
 */
public final class MissionEphemeris {

  private final AbsoluteDate[] times;
  private final Vector3D[] positions;
  private final Vector3D[] velocities;
  private final String[] stageNames;
  private final double[] masses;
  private final double[] altitudes;
  private final boolean complete;

  /**
   * Constructs a complete ephemeris from a list of sample points (must be sorted by time, >= 2
   * points).
   *
   * @param points the sorted list of ephemeris points
   */
  public MissionEphemeris(List<MissionEphemerisPoint> points) {
    this(points, true);
  }

  /**
   * Constructs from a list of sample points, flagged complete or not (bilan 11 §3.9 prérequis). An
   * <em>incomplete</em> ephemeris is one whose flown trajectory could not be propagated to the end
   * of every stage — a stage threw, or a burn ran its tank dry before its scheduled cutoff and the
   * {@code DepletionGuard} truncated it. The points collected up to that break are still returned
   * (a partial trail is better than none for rendering), but a consumer judging feasibility must
   * read {@link #isComplete()} first: a truncated trajectory is not a mission that flew.
   *
   * @param points the sorted list of ephemeris points
   * @param complete whether every stage propagated to its scheduled end
   */
  public MissionEphemeris(List<MissionEphemerisPoint> points, boolean complete) {
    if (points.size() < 2) {
      throw new IllegalArgumentException("At least 2 points required, got " + points.size());
    }
    this.complete = complete;
    int n = points.size();
    times = new AbsoluteDate[n];
    positions = new Vector3D[n];
    velocities = new Vector3D[n];
    stageNames = new String[n];
    masses = new double[n];
    altitudes = new double[n];

    for (int i = 0; i < n; i++) {
      MissionEphemerisPoint p = points.get(i);
      times[i] = p.time();
      positions[i] = p.position();
      velocities[i] = p.velocity();
      stageNames[i] = p.stageName();
      masses[i] = p.mass();
      altitudes[i] = p.altitudeMeters();
    }
  }

  /**
   * Whether every stage propagated to its scheduled end. {@code false} means the flown trajectory
   * was truncated (a stage threw, or a burn depleted its tank before its cutoff): the samples are
   * still usable for a partial trail, but the mission did not complete and must not be read as
   * feasible (bilan 11 §3.9 prérequis).
   *
   * @return {@code true} when the trajectory reached the end of every stage
   */
  public boolean isComplete() {
    return complete;
  }

  /** First sample time (T_start). */
  public AbsoluteDate startDate() {
    return times[0];
  }

  /** Last sample time (T_end). */
  public AbsoluteDate endDate() {
    return times[times.length - 1];
  }

  /** Total number of sample points. */
  public int size() {
    return times.length;
  }

  /** Returns the first sample point. */
  public MissionEphemerisPoint firstPoint() {
    return pointAt(0);
  }

  /** Returns the last sample point. */
  public MissionEphemerisPoint lastPoint() {
    return pointAt(times.length - 1);
  }

  /**
   * Interpolates a point at the given date within [startDate, endDate].
   *
   * <ul>
   *   <li>Position/velocity: cubic Hermite via EphemerisInterpolator
   *   <li>Altitude: linear interpolation between alt[i0] and alt[i1]
   *   <li>Stage name, mass: floor semantics (value of point[i0])
   * </ul>
   *
   * @param date the target date
   * @return the interpolated ephemeris point
   * @throws IllegalArgumentException if date is outside [startDate, endDate]
   */
  public MissionEphemerisPoint interpolate(AbsoluteDate date) {
    int[] interval = EphemerisInterpolator.findInterval(times, date);
    int i0 = interval[0], i1 = interval[1];

    if (i0 == i1) {
      return pointAt(i0);
    }

    double dt = times[i1].durationFrom(times[i0]);
    double tau = EphemerisInterpolator.computeTau(times, i0, i1, date);

    Vector3D p =
        EphemerisInterpolator.hermitePosition(
            positions[i0], velocities[i0], positions[i1], velocities[i1], dt, tau);
    Vector3D v =
        EphemerisInterpolator.hermiteVelocity(
            positions[i0], velocities[i0], positions[i1], velocities[i1], dt, tau);

    // Altitude: linear interpolation
    double alt = altitudes[i0] + tau * (altitudes[i1] - altitudes[i0]);

    // Mass and stage: floor semantics
    return new MissionEphemerisPoint(date, p, v, stageNames[i0], masses[i0], alt);
  }

  /**
   * Returns all sample positions from T_start up to the given date, plus the interpolated position
   * at date. Used for partial trail rendering.
   *
   * @param date the target date
   * @return list of positions for trail rendering
   */
  public List<Vector3D> positionsUpTo(AbsoluteDate date) {
    int[] interval = EphemerisInterpolator.findInterval(times, date);
    int i0 = interval[0];

    List<Vector3D> result = new ArrayList<>(i0 + 2);
    for (int i = 0; i <= i0; i++) {
      result.add(positions[i]);
    }

    // Add interpolated tip if date is between two points
    if (interval[1] != i0) {
      double dt = times[interval[1]].durationFrom(times[i0]);
      double tau = EphemerisInterpolator.computeTau(times, i0, interval[1], date);
      Vector3D tip =
          EphemerisInterpolator.hermitePosition(
              positions[i0], velocities[i0], positions[interval[1]], velocities[interval[1]], dt,
              tau);
      result.add(tip);
    }

    return result;
  }

  /** All sample positions from T_start to T_end. */
  public List<Vector3D> allPositions() {
    return List.of(positions);
  }

  /**
   * Returns all raw sample points. Used for extracting stage-specific data (e.g. in tests).
   *
   * @return unmodifiable list of all ephemeris points
   */
  public List<MissionEphemerisPoint> allPoints() {
    List<MissionEphemerisPoint> result = new ArrayList<>(times.length);
    for (int i = 0; i < times.length; i++) {
      result.add(pointAt(i));
    }
    return result;
  }

  private MissionEphemerisPoint pointAt(int index) {
    return new MissionEphemerisPoint(
        times[index],
        positions[index],
        velocities[index],
        stageNames[index],
        masses[index],
        altitudes[index]);
  }
}
