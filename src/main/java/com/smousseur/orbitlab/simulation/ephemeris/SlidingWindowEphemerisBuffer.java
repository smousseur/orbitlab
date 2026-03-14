package com.smousseur.orbitlab.simulation.ephemeris;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import com.smousseur.orbitlab.simulation.source.PrefetchingEphemerisSource;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe sliding window buffer of time-stamped position/velocity and rotation samples
 * for a single celestial body, stored in the ICRF frame.
 *
 * <p>Designed for a single-writer (ephemeris worker thread) / multiple-reader (render thread
 * and others) concurrency model. The writer atomically publishes new snapshots via
 * {@link #rebuildWindow} or {@link #ensureWindow}, and readers interpolate from the current
 * snapshot via {@link #trySampleInterpolated} without blocking.
 */
public final class SlidingWindowEphemerisBuffer {

  private final EphemerisSource source;
  private final EphemerisConfig config;
  private final SolarSystemBody body;

  private final AtomicReference<Snapshot> ref = new AtomicReference<>();

  /**
   * Creates a new sliding window ephemeris buffer for the given body.
   *
   * @param source the ephemeris data source providing raw body samples
   * @param config the ephemeris configuration controlling default window parameters
   * @param bodyId the celestial body this buffer tracks
   */
  public SlidingWindowEphemerisBuffer(
      EphemerisSource source, EphemerisConfig config, SolarSystemBody bodyId) {
    this.source = Objects.requireNonNull(source, "source");
    this.config = Objects.requireNonNull(config, "config");
    this.body = Objects.requireNonNull(bodyId, "body");
  }

  /**
   * Returns information about the currently published window, if any.
   *
   * <p>This is a non-blocking read of the current snapshot state.
   *
   * @return the window info, or empty if no snapshot has been published yet
   */
  public Optional<WindowInfo> windowInfo() {
    Snapshot s = ref.get();
    if (s == null) return Optional.empty();
    return Optional.of(new WindowInfo(s.start, s.end, s.stepSeconds, s.dates.length));
  }

  /**
   * Attempts to produce an interpolated body sample at the given time.
   *
   * <p>This is a non-blocking operation. Returns empty if no snapshot exists or if the
   * requested time falls outside the current window boundaries.
   *
   * @param t the time at which to interpolate
   * @return the interpolated body sample, or empty if unavailable
   */
  public Optional<BodySample> trySampleInterpolated(AbsoluteDate t) {
    Objects.requireNonNull(t, "t");
    Snapshot s = ref.get();
    if (s == null) {
      return Optional.empty();
    }
    if (t.compareTo(s.start) < 0 || t.compareTo(s.end) > 0) {
      return Optional.empty();
    }
    return Optional.of(interpolate(s, t));
  }

  /**
   * Rebuilds the entire buffer window centered on the given date using default configuration
   * parameters, and publishes the result atomically.
   *
   * <p>Intended to be called from the worker thread.
   *
   * @param centerDate the center of the new window
   */
  public void rebuildWindow(AbsoluteDate centerDate) {
    Objects.requireNonNull(centerDate, "centerDate");

    double step = config.sampleStepSeconds();
    int back = config.windowPointsBack();
    int fwd = config.windowPointsForward();

    rebuildWindow(centerDate, step, back, fwd);
  }

  /**
   * Rebuilds the entire buffer window centered on the given date using explicit parameters,
   * and publishes the result atomically.
   *
   * <p>Preferred over {@link #rebuildWindow(AbsoluteDate)} when per-body tuning of step size
   * and window extent is needed. Intended to be called from the worker thread.
   *
   * @param centerDate the center of the new window
   * @param stepSeconds the time interval between consecutive samples in seconds
   * @param pointsBack the number of sample points before the center
   * @param pointsForward the number of sample points after the center
   */
  public void rebuildWindow(
      AbsoluteDate centerDate, double stepSeconds, int pointsBack, int pointsForward) {
    Objects.requireNonNull(centerDate, "centerDate");
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    if (pointsBack < 2) {
      throw new IllegalArgumentException("pointsBack must be >= 2");
    }
    if (pointsForward < 2) {
      throw new IllegalArgumentException("pointsForward must be >= 2");
    }

    AbsoluteDate start = centerDate.shiftedBy(-pointsBack * stepSeconds);
    AbsoluteDate end = centerDate.shiftedBy(pointsForward * stepSeconds);

    int total = pointsBack + 1 + pointsForward;
    AbsoluteDate[] dates = new AbsoluteDate[total];
    Vector3D[] pos = new Vector3D[total];
    Vector3D[] vel = new Vector3D[total];
    Rotation[] rot = new Rotation[total];

    AbsoluteDate t = start;
    for (int i = 0; i < total; i++) {
      BodySample sample = source.sampleIcrf(body, t);
      dates[i] = sample.date();
      pos[i] = sample.pvIcrf().getPosition();
      vel[i] = sample.pvIcrf().getVelocity();
      rot[i] = sample.rotationIcrf();
      t = t.shiftedBy(stepSeconds);
    }

    ref.set(new Snapshot(start, end, stepSeconds, dates, pos, vel, rot));
  }

  private static BodySample interpolate(Snapshot s, AbsoluteDate t) {
    int i = Arrays.binarySearch(s.dates, t);
    if (i >= 0) {
      return new BodySample(t, new PVCoordinates(s.pos[i], s.vel[i]), s.rot[i]);
    }
    int insertPoint = -i - 1;
    int i0 = Math.max(0, insertPoint - 1);
    int i1 = Math.min(s.dates.length - 1, insertPoint);

    if (i0 == i1) {
      return new BodySample(t, new PVCoordinates(s.pos[i0], s.vel[i0]), s.rot[i0]);
    }

    AbsoluteDate t0 = s.dates[i0];
    AbsoluteDate t1 = s.dates[i1];
    double dt = t1.durationFrom(t0);
    if (dt <= 0.0) {
      return new BodySample(t, new PVCoordinates(s.pos[i0], s.vel[i0]), s.rot[i0]);
    }

    double tau = t.durationFrom(t0) / dt;

    Vector3D p = EphemerisInterpolator.hermitePosition(s.pos[i0], s.vel[i0], s.pos[i1], s.vel[i1], dt, tau);
    Vector3D v = EphemerisInterpolator.hermiteVelocity(s.pos[i0], s.vel[i0], s.pos[i1], s.vel[i1], dt, tau);
    Rotation r = EphemerisInterpolator.slerp(s.rot[i0], s.rot[i1], tau);

    return new BodySample(t, new PVCoordinates(p, v), r);
  }

  /**
   * Ensures the buffer window remains valid for the current simulation time.
   *
   * <p>This method implements the sliding window maintenance logic:
   * <ul>
   *   <li>If {@code forceFullRebuild} is true (e.g., after a seek), a full rebuild is performed.</li>
   *   <li>If {@code now} falls within the comfort zone of the current window, no action is taken.</li>
   *   <li>If {@code now} leaves the comfort zone, the window is repositioned on a stable grid
   *       aligned to the session anchor; a slide is attempted if the shift is small, otherwise
   *       a full rebuild is triggered.</li>
   * </ul>
   *
   * <p>Intended to be called from the worker thread.
   *
   * @param sessionAnchor the fixed reference date for grid alignment
   * @param now the current simulation time
   * @param speed the current clock speed multiplier (sign indicates direction)
   * @param plan the window plan determining step size, extent, and margin
   * @param forceFullRebuild if true, forces a complete rebuild regardless of comfort zone
   */
  public void ensureWindow(
      AbsoluteDate sessionAnchor,
      AbsoluteDate now,
      double speed,
      SlidingWindowConfig.WindowPlan plan,
      boolean forceFullRebuild) {

    Objects.requireNonNull(sessionAnchor, "sessionAnchor");
    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(plan, "plan");
    if (!Double.isFinite(speed)) {
      throw new IllegalArgumentException("speed must be finite");
    }

    Snapshot s = ref.get();

    if (forceFullRebuild || s == null) {
      AbsoluteDate start = computeSnappedStartForFullRebuild(sessionAnchor, now, plan);
      publishFullBuildFromStart(
          start, speed, plan.stepSeconds(), plan.pointsBack(), plan.pointsForward());
      return;
    }

    // Plan compatibility: keep V1 simple. If step/size changes => full rebuild.
    int total = plan.pointsBack() + 1 + plan.pointsForward();
    if (Double.compare(s.stepSeconds, plan.stepSeconds()) != 0 || s.dates.length != total) {
      AbsoluteDate start = computeSnappedStartForFullRebuild(sessionAnchor, now, plan);
      publishFullBuildFromStart(
          start, speed, plan.stepSeconds(), plan.pointsBack(), plan.pointsForward());
      return;
    }

    // Comfort zone check (same semantics as before; decision lives here now)
    AbsoluteDate comfortStart = s.start.shiftedBy(plan.marginPoints() * s.stepSeconds);
    AbsoluteDate comfortEnd = s.end.shiftedBy(-plan.marginPoints() * s.stepSeconds);

    if (now.compareTo(comfortStart) >= 0 && now.compareTo(comfortEnd) <= 0) {
      return; // still safe
    }

    // Reposition: put now back into comfort zone, biased by speed direction.
    AbsoluteDate desiredStart = computeSnappedStartForReposition(sessionAnchor, now, speed, plan);

    long shiftSteps = Math.round(desiredStart.durationFrom(s.start) / s.stepSeconds);
    if (Math.abs(shiftSteps) <= plan.marginPoints()) {
      Snapshot slid =
          buildSlidSnapshot(
              s,
              desiredStart,
              plan.stepSeconds(),
              plan.pointsBack(),
              plan.pointsForward(),
              (int) shiftSteps);
      ref.set(slid);
      return;
    }

    publishFullBuildFromStart(
        desiredStart, speed, plan.stepSeconds(), plan.pointsBack(), plan.pointsForward());
  }

  private AbsoluteDate computeSnappedStartForFullRebuild(
      AbsoluteDate anchor, AbsoluteDate centerDate, SlidingWindowConfig.WindowPlan plan) {

    // "Centered" rebuild: keep the same meaning as before (pointsBack behind, pointsForward ahead),
    // but snap the start to the grid anchored at session start.
    AbsoluteDate startCandidate = centerDate.shiftedBy(-plan.pointsBack() * plan.stepSeconds());
    return snapRoundToGrid(startCandidate, anchor, plan.stepSeconds());
  }

  private AbsoluteDate computeSnappedStartForReposition(
      AbsoluteDate anchor, AbsoluteDate now, double speed, SlidingWindowConfig.WindowPlan plan) {

    // Symmetric window, but we reposition so that 'now' falls back inside the comfort zone.
    // speed >= 0 => now near the beginning comfort area
    // speed < 0  => now near the ending comfort area
    int targetIndex =
        (speed >= 0.0)
            ? (plan.pointsBack() - plan.marginPoints())
            : (plan.pointsBack() + plan.marginPoints());

    // Guard: shouldn't happen with sane plans, but avoid negative index.
    targetIndex = Math.max(0, targetIndex);

    AbsoluteDate startCandidate = now.shiftedBy(-targetIndex * plan.stepSeconds());
    return snapRoundToGrid(startCandidate, anchor, plan.stepSeconds());
  }

  private static AbsoluteDate snapRoundToGrid(
      AbsoluteDate candidate, AbsoluteDate anchor, double stepSeconds) {
    long k = Math.round(candidate.durationFrom(anchor) / stepSeconds);
    return anchor.shiftedBy(k * stepSeconds);
  }

  private void publishFullBuildFromStart(
      AbsoluteDate start, double speed, double stepSeconds, int pointsBack, int pointsForward) {

    int total = pointsBack + 1 + pointsForward;

    AbsoluteDate end = start.shiftedBy((long) (total - 1) * stepSeconds);

    if (source instanceof PrefetchingEphemerisSource p) {
      p.prefetch(body, start, end, speed);
    }

    AbsoluteDate[] dates = new AbsoluteDate[total];
    Vector3D[] pos = new Vector3D[total];
    Vector3D[] vel = new Vector3D[total];
    Rotation[] rot = new Rotation[total];

    AbsoluteDate t = start;
    for (int i = 0; i < total; i++) {
      BodySample sample = source.sampleIcrf(body, t);
      dates[i] = sample.date();
      pos[i] = sample.pvIcrf().getPosition();
      vel[i] = sample.pvIcrf().getVelocity();
      rot[i] = sample.rotationIcrf();
      t = t.shiftedBy(stepSeconds);
    }

    ref.set(new Snapshot(start, end, stepSeconds, dates, pos, vel, rot));
  }

  private Snapshot buildSlidSnapshot(
      Snapshot old,
      AbsoluteDate newStart,
      double stepSeconds,
      int pointsBack,
      int pointsForward,
      int shiftSteps) {

    int total = pointsBack + 1 + pointsForward;
    AbsoluteDate newEnd = newStart.shiftedBy((long) (total - 1) * stepSeconds);

    AbsoluteDate[] dates = new AbsoluteDate[total];
    Vector3D[] pos = new Vector3D[total];
    Vector3D[] vel = new Vector3D[total];
    Rotation[] rot = new Rotation[total];

    // Build dates deterministically => always sorted
    AbsoluteDate t = newStart;
    for (int i = 0; i < total; i++) {
      dates[i] = t;
      t = t.shiftedBy(stepSeconds);
    }

    // Copy overlap and compute missing edges
    if (shiftSteps > 0) {
      int kept = total - shiftSteps;
      if (kept > 0) {
        System.arraycopy(old.pos, shiftSteps, pos, 0, kept);
        System.arraycopy(old.vel, shiftSteps, vel, 0, kept);
        System.arraycopy(old.rot, shiftSteps, rot, 0, kept);
      }
      for (int i = kept; i < total; i++) {
        BodySample sample = source.sampleIcrf(body, dates[i]);
        pos[i] = sample.pvIcrf().getPosition();
        vel[i] = sample.pvIcrf().getVelocity();
        rot[i] = sample.rotationIcrf();
      }
    } else if (shiftSteps < 0) {
      int s = -shiftSteps;
      int kept = total - s;
      if (kept > 0) {
        System.arraycopy(old.pos, 0, pos, s, kept);
        System.arraycopy(old.vel, 0, vel, s, kept);
        System.arraycopy(old.rot, 0, rot, s, kept);
      }
      for (int i = 0; i < s; i++) {
        BodySample sample = source.sampleIcrf(body, dates[i]);
        pos[i] = sample.pvIcrf().getPosition();
        vel[i] = sample.pvIcrf().getVelocity();
        rot[i] = sample.rotationIcrf();
      }
    } else {
      // No shift; shouldn't happen here, but keep it correct.
      System.arraycopy(old.pos, 0, pos, 0, total);
      System.arraycopy(old.vel, 0, vel, 0, total);
      System.arraycopy(old.rot, 0, rot, 0, total);
    }

    return new Snapshot(newStart, newEnd, stepSeconds, dates, pos, vel, rot);
  }

  /**
   * Describes the extent and resolution of the currently published ephemeris window.
   *
   * @param start the start time of the window
   * @param end the end time of the window
   * @param stepSeconds the time interval between consecutive samples in seconds
   * @param totalPoints the total number of sample points in the window
   */
  public record WindowInfo(
      AbsoluteDate start, AbsoluteDate end, double stepSeconds, int totalPoints) {
    public WindowInfo {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
        throw new IllegalArgumentException("stepSeconds must be finite and > 0");
      }
      if (totalPoints < 1) {
        throw new IllegalArgumentException("totalPoints must be >= 1");
      }
    }
  }

  private record Snapshot(
      AbsoluteDate start,
      AbsoluteDate end,
      double stepSeconds,
      AbsoluteDate[] dates,
      Vector3D[] pos,
      Vector3D[] vel,
      Rotation[] rot) {
    private Snapshot {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
        throw new IllegalArgumentException("stepSeconds must be finite and > 0");
      }
      Objects.requireNonNull(dates, "dates");
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(vel, "vel");
      Objects.requireNonNull(rot, "rot");
    }
  }
}
