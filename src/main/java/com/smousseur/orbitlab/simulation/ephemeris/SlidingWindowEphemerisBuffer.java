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
 * Sliding window buffer of time-stamped PV + rotation samples, stored in ICRF. Threading: - One
 * writer (worker thread) replaces the snapshot. - Many readers (render thread + others) read
 * snapshot and interpolate.
 */
public final class SlidingWindowEphemerisBuffer {

  private final EphemerisSource source;
  private final EphemerisConfig config;
  private final SolarSystemBody body;

  private final AtomicReference<Snapshot> ref = new AtomicReference<>();

  public SlidingWindowEphemerisBuffer(
      EphemerisSource source, EphemerisConfig config, SolarSystemBody bodyId) {
    this.source = Objects.requireNonNull(source, "source");
    this.config = Objects.requireNonNull(config, "config");
    this.body = Objects.requireNonNull(bodyId, "body");
  }

  /** Non-blocking: current published window info (empty if none). */
  public Optional<WindowInfo> windowInfo() {
    Snapshot s = ref.get();
    if (s == null) return Optional.empty();
    return Optional.of(new WindowInfo(s.start, s.end, s.stepSeconds, s.dates.length));
  }

  /**
   * Non-blocking sample. Returns empty if there is no snapshot or t is outside the current window.
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

  /** (Worker) rebuild a new window centered on centerDate and publish atomically. */
  public void rebuildWindow(AbsoluteDate centerDate) {
    Objects.requireNonNull(centerDate, "centerDate");

    double step = config.sampleStepSeconds();
    int back = config.windowPointsBack();
    int fwd = config.windowPointsForward();

    rebuildWindow(centerDate, step, back, fwd);
  }

  /** (Worker) rebuild using explicit parameters (preferred for per-body tuning). */
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

    Vector3D p = hermitePosition(s.pos[i0], s.vel[i0], s.pos[i1], s.vel[i1], dt, tau);
    Vector3D v = hermiteVelocity(s.pos[i0], s.vel[i0], s.pos[i1], s.vel[i1], dt, tau);
    Rotation r = slerp(s.rot[i0], s.rot[i1], tau);

    return new BodySample(t, new PVCoordinates(p, v), r);
  }

  private static Vector3D hermitePosition(
      Vector3D p0, Vector3D v0, Vector3D p1, Vector3D v1, double dt, double t) {
    double t2 = t * t;
    double t3 = t2 * t;

    double h00 = 2 * t3 - 3 * t2 + 1;
    double h10 = t3 - 2 * t2 + t;
    double h01 = -2 * t3 + 3 * t2;
    double h11 = t3 - t2;

    return new Vector3D(h00, p0, h10 * dt, v0, h01, p1, h11 * dt, v1);
  }

  private static Vector3D hermiteVelocity(
      Vector3D p0, Vector3D v0, Vector3D p1, Vector3D v1, double dt, double t) {
    double t2 = t * t;

    double dh00 = 6 * t2 - 6 * t;
    double dh10 = 3 * t2 - 4 * t + 1;
    double dh01 = -6 * t2 + 6 * t;
    double dh11 = 3 * t2 - 2 * t;

    double invDt = 1.0 / dt;

    return new Vector3D(dh00 * invDt, p0, dh10, v0, dh01 * invDt, p1, dh11, v1);
  }

  /**
   * Quaternion slerp for Hipparchus Rotation. Rotation stores quaternion components (q0 scalar, q1
   * q2 q3 vector part).
   */
  private static Rotation slerp(Rotation r0, Rotation r1, double t) {
    if (t <= 0.0) return r0;
    if (t >= 1.0) return r1;

    double q00 = r0.getQ0(), q01 = r0.getQ1(), q02 = r0.getQ2(), q03 = r0.getQ3();
    double q10 = r1.getQ0(), q11 = r1.getQ1(), q12 = r1.getQ2(), q13 = r1.getQ3();

    double dot = q00 * q10 + q01 * q11 + q02 * q12 + q03 * q13;
    if (dot < 0.0) {
      dot = -dot;
      q10 = -q10;
      q11 = -q11;
      q12 = -q12;
      q13 = -q13;
    }

    if (dot > 0.9995) {
      double a = 1.0 - t;
      double b = t;
      double s0 = a * q00 + b * q10;
      double s1 = a * q01 + b * q11;
      double s2 = a * q02 + b * q12;
      double s3 = a * q03 + b * q13;
      return new Rotation(s0, s1, s2, s3, true);
    }

    double theta0 = Math.acos(dot);
    double sinTheta0 = Math.sin(theta0);

    double theta = theta0 * t;
    double sinTheta = Math.sin(theta);

    double sA = Math.sin(theta0 - theta) / sinTheta0;
    double sB = sinTheta / sinTheta0;

    double s0 = sA * q00 + sB * q10;
    double s1 = sA * q01 + sB * q11;
    double s2 = sA * q02 + sB * q12;
    double s3 = sA * q03 + sB * q13;

    return new Rotation(s0, s1, s2, s3, true);
  }

  // ----
  /**
   * (Worker) Ensure the buffer window stays valid for {@code now}.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Seek => full rebuild (no incremental logic for now).
   *   <li>Normal tick => if now is in comfort zone => no-op.
   *   <li>If now leaves comfort zone => reposition window on a stable grid (anchor+round), then
   *       slide if the shift is small; else full rebuild.
   * </ul>
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

  // ----
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
