package com.smousseur.orbitlab.engine;

import java.util.Objects;

/**
 * Tuning of the half turn the primary makes around a retrograde burn: it pitches over through its
 * local vertical before the burn, flies the burn tail first so its plume leaves its real nozzles,
 * and turns back once the burn is over.
 *
 * @param flipSeconds how long each half of the manoeuvre lasts, in <b>simulated</b> seconds, so the
 *     turn is a function of the date and replays backward when the clock is rewound (finite,
 *     positive)
 * @param easing the timing curve of each half
 * @param retrogradeCosine the energy-weighted cosine between thrust and velocity below which a burn
 *     counts as retrograde, in {@code (-1, 0)}
 */
public record RetrogradeFlipConfig(double flipSeconds, Easing easing, double retrogradeCosine) {

  public RetrogradeFlipConfig {
    if (!Double.isFinite(flipSeconds) || flipSeconds <= 0.0) {
      throw new IllegalArgumentException("flipSeconds must be finite and > 0, got " + flipSeconds);
    }
    Objects.requireNonNull(easing, "easing");
    if (!(retrogradeCosine > -1.0 && retrogradeCosine < 0.0)) {
      throw new IllegalArgumentException(
          "retrogradeCosine must be in (-1, 0), got " + retrogradeCosine);
    }
  }

  /**
   * Creates the default flip tuning.
   *
   * <p>Both values come from the burns measured on the production missions. Every retrograde burn
   * there has a cosine of −0.997 or below and every other burn one of −0.0000 or above, the plane
   * corrections sitting at zero, so −0.5 parts them with room on both sides. And the shortest
   * stretch measured between a retrograde burn and the burn on either side of it is 2,776.5 s, so
   * five simulated minutes before and after fit everywhere without being squeezed.
   *
   * @return the default retrograde flip configuration
   */
  public static RetrogradeFlipConfig defaults() {
    return new RetrogradeFlipConfig(300.0, Easing.SMOOTHSTEP, -0.5);
  }
}
