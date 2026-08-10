package com.smousseur.orbitlab.engine;

import java.util.Objects;

/**
 * Tuning of the animated camera transitions played when the focus changes (solar view, a planet, a
 * spacecraft).
 *
 * @param durationSec how long a transition lasts, in real seconds (must be finite and positive)
 * @param easing the timing curve applied to the pivot, the distance and the orientation
 * @param orientationLeadFraction the fraction of the duration over which the orientation reaches
 *     its target, in {@code (0,1]} — see below
 */
public record CameraTransitionConfig(
    float durationSec, Easing easing, float orientationLeadFraction) {

  public CameraTransitionConfig {
    if (!Float.isFinite(durationSec) || durationSec <= 0f) {
      throw new IllegalArgumentException("durationSec must be finite and > 0");
    }
    Objects.requireNonNull(easing, "easing");
    if (!Float.isFinite(orientationLeadFraction)
        || orientationLeadFraction <= 0f
        || orientationLeadFraction > 1f) {
      throw new IllegalArgumentException("orientationLeadFraction must be in (0,1]");
    }
  }

  /**
   * Creates the default transition tuning: 2.5 s of {@link Easing#SMOOTHSTEP}, with the camera
   * done turning after the first 35 %.
   *
   * <p>The orientation deliberately leads rather than sharing the pivot's schedule. Interpolated
   * over the whole duration it would only frame the destination on the very last frames, which is
   * exactly when it needs no help — the middle of a transition is where the pivot is out in empty
   * space and the destination has to already be centred. Turning first, while still far out, and
   * then flying at it is what makes the move read as going somewhere.
   *
   * @return the default camera transition configuration
   */
  public static CameraTransitionConfig defaults() {
    return new CameraTransitionConfig(2.5f, Easing.SMOOTHSTEP, 0.35f);
  }
}
