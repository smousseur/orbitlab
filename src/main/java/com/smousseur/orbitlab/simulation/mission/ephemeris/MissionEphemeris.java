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
  private final boolean[] propulsive;
  private final double[] masses;
  private final double[] altitudes;

  /**
   * The frame each sample is expressed in (PHY-4 / L3). One entry per sample rather than a table of
   * spans, for the reason {@link MissionEphemerisPoint#arc()} gives; the storage is one reference
   * per point, about 1% of a point's footprint.
   */
  private final TrajectoryArc[] arcs;

  private final boolean complete;

  /**
   * The drawable form of this trajectory, built once here rather than per frame. See {@link
   * TrajectoryPolyline} for why the display product is separate from this one.
   */
  private final TrajectoryPolyline displayTrail;

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
    propulsive = new boolean[n];
    masses = new double[n];
    altitudes = new double[n];
    arcs = new TrajectoryArc[n];

    for (int i = 0; i < n; i++) {
      MissionEphemerisPoint p = points.get(i);
      times[i] = p.time();
      positions[i] = p.position();
      velocities[i] = p.velocity();
      stageNames[i] = p.stageName();
      propulsive[i] = p.propulsive();
      masses[i] = p.mass();
      altitudes[i] = p.altitudeMeters();
      arcs[i] = p.arc();
    }

    displayTrail = TrajectoryPolyline.of(times, positions, stageNames, propulsive, arcs);
  }

  /**
   * Returns the drawable form of this trajectory: bounded in size, indexable by date, and shared —
   * the same instance is handed out on every call, so a renderer may hold it across frames.
   *
   * @return the display polyline
   */
  public TrajectoryPolyline displayTrail() {
    return displayTrail;
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
   *   <li>Stage name, mass, arc: floor semantics (value of point[i0])
   * </ul>
   *
   * <p><b>Across an arc boundary nothing is interpolated at all</b> (PHY-4 / L3, spec {@code
   * docs/multi-corps/05-conception-L3.md} §3.3). A cubic Hermite between two positions expressed in
   * different frames is not an approximation, it is meaningless — it would blend a geocentric
   * vector with a selenocentric one. The bracketing point is returned unchanged instead, which
   * extends to the arc the floor semantics this method already applies to the stage name and the
   * mass.
   *
   * <p>Two consequences, both wanted. The spacecraft holds still for at most one sampling step at
   * the crossing, rather than being drawn somewhere it never was. And the render context — derived
   * from the returned point by {@code MissionRenderer.renderContextFor} — flips <em>atomically</em>
   * at the incoming sample: there is no frame on which the states reading it could hold different
   * arcs. Converting the two states into a common frame and interpolating properly is L4's work,
   * with L4's millimetre target; doing it here would be doing it early, untested, on a path called
   * three times per frame.
   *
   * @param date the target date
   * @return the interpolated ephemeris point
   * @throws IllegalArgumentException if date is outside [startDate, endDate]
   */
  public MissionEphemerisPoint interpolate(AbsoluteDate date) {
    int[] interval = EphemerisInterpolator.findInterval(times, date);
    int i0 = interval[0];
    int i1 = interval[1];

    // Exact hit. Only here may the index be normalised: when the interval brackets strictly, i0 is
    // the lower bound of a real span and walking back off it would bracket across the boundary.
    if (i0 == i1) {
      return pointAt(floorOfEqualDates(i0));
    }
    if (!arcs[i0].equals(arcs[i1])) {
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
    return new MissionEphemerisPoint(
        date, p, v, stageNames[i0], propulsive[i0], masses[i0], alt, arcs[i0]);
  }

  /**
   * The point to show at {@code date}: interpolated inside the recorded span, and clamped to the
   * first or last sample outside it — before launch the spacecraft is on its pad, after the last
   * sample the mission is over and it stays where it ended.
   *
   * <p>Exists so that the two states that need the spacecraft's current position cannot disagree
   * about it. {@link com.smousseur.orbitlab.states.camera.FloatingOriginAppState} offsets the near
   * frame by the negation of that position while {@link
   * com.smousseur.orbitlab.states.mission.MissionOrchestratorAppState} places the spacecraft anchor
   * at it; the spacecraft lands exactly on the near-view origin only if both read the same point
   * for the same date.
   *
   * @param date the current simulation date
   * @return the point to display, never {@code null}
   */
  public MissionEphemerisPoint displayPointAt(AbsoluteDate date) {
    if (date.compareTo(startDate()) <= 0) {
      return firstPoint();
    }
    if (date.compareTo(endDate()) >= 0) {
      return lastPoint();
    }
    return interpolate(date);
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

  /**
   * The lowest index sharing {@code times[index]}, which matters exactly once: at an arc boundary.
   *
   * <p>PHY-4 / L4 writes the boundary as <b>two samples at the same date</b>, one per frame — the
   * outgoing state in the frame being left, the incoming one in the frame being entered (spec
   * {@code docs/multi-corps/06-conception-L4.md} §5). {@code Arrays.binarySearch}, which {@link
   * EphemerisInterpolator#findInterval} rests on, returns <em>some</em> matching index among equal
   * keys and does not say which. Normalising to the lowest makes the answer the <b>outgoing</b>
   * point, which is the floor semantics this method already applies to the stage name, the mass and
   * the arc: the flip happens at the next sample, exactly as L3 §3.3 wrote it.
   *
   * <p>The behaviour was never actually at risk — the three readers query the same array at the
   * same date, so they get the same index whichever it is. What is closed here is the contract.
   */
  private int floorOfEqualDates(int index) {
    int i = index;
    while (i > 0 && times[i - 1].equals(times[i])) {
      i--;
    }
    return i;
  }

  private MissionEphemerisPoint pointAt(int index) {
    return new MissionEphemerisPoint(
        times[index],
        positions[index],
        velocities[index],
        stageNames[index],
        propulsive[index],
        masses[index],
        altitudes[index],
        arcs[index]);
  }
}
