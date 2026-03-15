package com.smousseur.orbitlab.simulation.orbit;

import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * Utility class providing orbit windowing policies for computing and caching orbital paths.
 *
 * <p>Contains pure functions for step size calculation, comfort margin determination,
 * comfort zone checks, and center snapping to avoid excessive rebuild frequency.
 */
public final class OrbitPolicy {
  private OrbitPolicy() {}

  /**
   * Computes the time step between orbit sample points by dividing the orbital period
   * by the desired number of points.
   *
   * @param periodSeconds the orbital period in seconds
   * @param nPoints the desired number of sample points (must be at least 2)
   * @return the step size in seconds
   * @throws IllegalArgumentException if periodSeconds is not positive/finite or nPoints is less than 2
   */
  public static double stepSeconds(double periodSeconds, int nPoints) {
    if (!Double.isFinite(periodSeconds) || periodSeconds <= 0.0) {
      throw new IllegalArgumentException("periodSeconds must be finite and > 0");
    }
    if (nPoints < 2) {
      throw new IllegalArgumentException("nPoints must be >= 2");
    }
    return periodSeconds / nPoints;
  }

  /**
   * Computes the comfort margin in seconds, defining how far the simulation time can drift
   * from the snapshot center before a rebuild is triggered.
   *
   * <p>The margin is computed as {@code marginPoints * stepSeconds}, then clamped between
   * {@code mMinSeconds} and {@code periodSeconds * mMaxFraction}.
   *
   * @param periodSeconds the orbital period in seconds
   * @param stepSeconds the time step between sample points in seconds
   * @param marginPoints the number of margin points
   * @param mMinSeconds the minimum allowed margin in seconds
   * @param mMaxFraction the maximum margin as a fraction of the orbital period (0, 1]
   * @return the comfort margin in seconds
   */
  public static double comfortMarginSeconds(
      double periodSeconds,
      double stepSeconds,
      int marginPoints,
      double mMinSeconds,
      double mMaxFraction) {

    if (!Double.isFinite(periodSeconds) || periodSeconds <= 0.0) {
      throw new IllegalArgumentException("periodSeconds must be finite and > 0");
    }
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    if (marginPoints < 0) {
      throw new IllegalArgumentException("marginPoints must be >= 0");
    }
    if (!Double.isFinite(mMinSeconds) || mMinSeconds < 0.0) {
      throw new IllegalArgumentException("mMinSeconds must be finite and >= 0");
    }
    if (!Double.isFinite(mMaxFraction) || mMaxFraction <= 0.0 || mMaxFraction > 1.0) {
      throw new IllegalArgumentException("mMaxFraction must be in (0, 1]");
    }

    double raw = marginPoints * stepSeconds;
    double max = periodSeconds * mMaxFraction;
    return Math.max(mMinSeconds, Math.min(max, raw));
  }

  /**
   * Checks whether a given time falls within the comfort zone of an orbit snapshot.
   *
   * <p>The time {@code t} is considered in the comfort zone if the absolute distance from
   * the snapshot's center date is at most {@code comfortMarginSeconds}.
   *
   * @param snapshot the current orbit snapshot (may be null, which returns false)
   * @param t the time to check
   * @param comfortMarginSeconds the comfort margin in seconds
   * @return true if the time is within the comfort zone
   */
  public static boolean isInComfort(
      OrbitSnapshot snapshot, AbsoluteDate t, double comfortMarginSeconds) {
    Objects.requireNonNull(t, "t");
    if (snapshot == null) {
      return false;
    }
    if (!Double.isFinite(comfortMarginSeconds) || comfortMarginSeconds < 0.0) {
      throw new IllegalArgumentException("comfortMarginSeconds must be finite and >= 0");
    }
    double dt = Math.abs(t.durationFrom(snapshot.centerDate()));
    return dt <= comfortMarginSeconds;
  }

  /**
   * Snaps a time to the nearest grid point aligned to a reference anchor, preventing
   * unnecessary rebuilds on small time changes.
   *
   * <p>The snap step is typically {@code snapPoints * stepSeconds}.
   *
   * @param t the time to snap
   * @param anchor the reference anchor date defining the grid origin
   * @param snapStepSeconds the grid spacing in seconds
   * @return the snapped time (nearest grid point to {@code t})
   */
  public static AbsoluteDate snapCenter(
      AbsoluteDate t, AbsoluteDate anchor, double snapStepSeconds) {
    Objects.requireNonNull(t, "t");
    Objects.requireNonNull(anchor, "anchor");
    if (!Double.isFinite(snapStepSeconds) || snapStepSeconds <= 0.0) {
      throw new IllegalArgumentException("snapStepSeconds must be finite and > 0");
    }
    double dt = t.durationFrom(anchor);
    long k = Math.round(dt / snapStepSeconds);
    return anchor.shiftedBy(k * snapStepSeconds);
  }
}
