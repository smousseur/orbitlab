package com.smousseur.orbitlab.engine.scene.spacecraft;

/**
 * How much of its turbulence the plasma keeps at a given clock rate.
 *
 * <p>The plasma's turbulence drifts with the simulated time — frozen when the clock is paused,
 * flowing backward when it is rewound, a function of the date as everything else drawn. Its noise
 * drifts 5.5 body radii per simulated second, and its structures span 0.3 to 0.9 radius. Played at
 * a few times real time, the drift from one frame to the next outgrows them, and the noise would
 * flicker instead of flowing: derived at 60 frames a second, 0.46 radius per frame at five times
 * and 0.92 at ten. So past {@value #WHOLE_UP_TO_RATE} times the turbulence is eased toward its
 * mean, and gone from {@value #GONE_FROM_RATE} times on, where the plasma reads as its smooth
 * average — the eye's own blur at that rate.
 */
final class ReentryFlowClock {

  /** The clock rate, either way, up to which the turbulence is whole. */
  static final double WHOLE_UP_TO_RATE = 2.0;

  /** The clock rate, either way, from which the turbulence is gone. */
  static final double GONE_FROM_RATE = 10.0;

  private ReentryFlowClock() {}

  /**
   * The share of the turbulence kept at a clock rate.
   *
   * @param clockRate the simulated seconds per real second, negative when rewinding, zero when
   *     paused
   * @return the share, 1 for whole and 0 for none
   */
  static float turbulence(double clockRate) {
    double t =
        Math.min(
            1.0,
            Math.max(
                0.0,
                (Math.abs(clockRate) - WHOLE_UP_TO_RATE) / (GONE_FROM_RATE - WHOLE_UP_TO_RATE)));
    return (float) (1.0 - t * t * (3.0 - 2.0 * t));
  }
}
