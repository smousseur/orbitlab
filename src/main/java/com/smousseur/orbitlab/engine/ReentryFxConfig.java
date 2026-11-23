package com.smousseur.orbitlab.engine;

import java.util.Objects;

/**
 * Tuning of the re-entry effects: when a falling object starts to glow and how hard, and the plasma
 * volume it is wrapped in up close.
 *
 * <p>The threshold and the ceiling map a stagnation heat flux onto the intensity {@code ln(flux /
 * threshold) / ln(ceiling / threshold)}, bounded to {@code [0, 1]}.
 *
 * @param thresholdWm2 the heat flux, in W/m², at and below which nothing glows (finite, positive)
 * @param ceilingWm2 the heat flux, in W/m², at and above which the glow saturates (finite, above
 *     the threshold)
 * @param volume the tuning of the plasma volume drawn around a hot piece up close
 */
public record ReentryFxConfig(double thresholdWm2, double ceilingWm2, ReentryVolumeConfig volume) {

  public ReentryFxConfig {
    if (!Double.isFinite(thresholdWm2) || thresholdWm2 <= 0.0) {
      throw new IllegalArgumentException("thresholdWm2 must be finite and > 0");
    }
    if (!Double.isFinite(ceilingWm2) || ceilingWm2 <= thresholdWm2) {
      throw new IllegalArgumentException("ceilingWm2 must be finite and above the threshold");
    }
    Objects.requireNonNull(volume, "volume");
  }

  /**
   * Creates the default re-entry tuning.
   *
   * <p>The threshold and the ceiling are the ones the re-entry baseline measured on the production
   * path: 100 kW/m² keeps every falling piece above it at its peak (605 kW/m² for the weakest, an
   * Ariane 64 booster) while the flux at the 120 km crossing stays under 10 kW/m²; 11 MW/m² is the
   * hottest peak measured (a Falcon Heavy core, 10.5 MW/m²) rounded up, so only that core comes
   * near saturation.
   *
   * @return the default re-entry configuration
   */
  public static ReentryFxConfig defaults() {
    return new ReentryFxConfig(100_000.0, 11_000_000.0, ReentryVolumeConfig.defaults());
  }
}
