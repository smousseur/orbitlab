package com.smousseur.orbitlab.simulation.orbit;

import java.util.Objects;
import org.orekit.time.AbsoluteDate;

public final class OrbitPolicy {
  private OrbitPolicy() {}

  /** Δ = P/N */
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
   * Comfort margin M (seconds) computed from: M = clamp(marginPoints * step, M_min, P *
   * M_maxFraction)
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

  /** t is in comfort iff |t - Tc| <= M. */
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
   * Snap the center Tc so we don't rebuild on every tiny time change. snapStepSeconds usually =
   * snapPoints * stepSeconds.
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
