package com.smousseur.orbitlab.engine;

import com.jme3.math.ColorRGBA;
import java.util.Objects;

/**
 * Tuning of the re-entry effects: when a falling object starts to glow and how hard, and how its
 * plasma sheath looks.
 *
 * <p>The threshold and the ceiling map a stagnation heat flux onto the intensity {@code ln(flux /
 * threshold) / ln(ceiling / threshold)}, bounded to {@code [0, 1]}. The sheath is a paraboloid
 * shell standing off the drawn nose of the piece, sized in multiples of that piece's drawn body
 * radius, so one set of proportions fits every launcher.
 *
 * <p>The colours are copied in and out: {@link ColorRGBA} is mutable, and a tuning shared by every
 * sheath must not change under them.
 *
 * @param thresholdWm2 the heat flux, in W/m², at and below which nothing glows (finite, positive)
 * @param ceilingWm2 the heat flux, in W/m², at and above which the glow saturates (finite, above
 *     the threshold)
 * @param rimColor the colour of the sheath's edge, the same at every intensity
 * @param hotCoreColor the colour the sheath's stagnation core turns toward as the intensity rises
 * @param coverage how much of the background the sheath hides where it is seen edge-on, at full
 *     intensity, in {@code [0, 1]}; seen face-on it hides nothing, and its light is added on top
 * @param standoffRatio how far ahead of the nose the shell's apex stands, in body radii (at least
 *     0)
 * @param lengthRatio how far back from its apex the shell reaches, in body radii (positive)
 * @param openingRatio the radius of the shell's open end, in body radii (positive)
 */
public record ReentryFxConfig(
    double thresholdWm2,
    double ceilingWm2,
    ColorRGBA rimColor,
    ColorRGBA hotCoreColor,
    float coverage,
    float standoffRatio,
    float lengthRatio,
    float openingRatio) {

  public ReentryFxConfig {
    if (!Double.isFinite(thresholdWm2) || thresholdWm2 <= 0.0) {
      throw new IllegalArgumentException("thresholdWm2 must be finite and > 0");
    }
    if (!Double.isFinite(ceilingWm2) || ceilingWm2 <= thresholdWm2) {
      throw new IllegalArgumentException("ceilingWm2 must be finite and above the threshold");
    }
    rimColor = Objects.requireNonNull(rimColor, "rimColor").clone();
    hotCoreColor = Objects.requireNonNull(hotCoreColor, "hotCoreColor").clone();
    if (!(coverage >= 0f && coverage <= 1f)) {
      throw new IllegalArgumentException("coverage must be in [0,1]");
    }
    if (!Float.isFinite(standoffRatio) || standoffRatio < 0f) {
      throw new IllegalArgumentException("standoffRatio must be finite and >= 0");
    }
    if (!Float.isFinite(lengthRatio) || lengthRatio <= 0f) {
      throw new IllegalArgumentException("lengthRatio must be finite and > 0");
    }
    if (!Float.isFinite(openingRatio) || openingRatio <= 0f) {
      throw new IllegalArgumentException("openingRatio must be finite and > 0");
    }
  }

  /**
   * The edge colour, as a copy.
   *
   * @return a copy of the rim colour
   */
  @Override
  public ColorRGBA rimColor() {
    return rimColor.clone();
  }

  /**
   * The hot core colour, as a copy.
   *
   * @return a copy of the hot core colour
   */
  @Override
  public ColorRGBA hotCoreColor() {
    return hotCoreColor.clone();
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
   * <p>Everything else is set on screen, not measured: an orange edge and a near-white core, and a
   * short paraboloid two and a half radii long. The first look, on a Falcon Heavy booster at its
   * heating peak, moved two of them. Half a radius of standoff and a 1.6-radius opening hugged the
   * nose like a helmet, so the apex now stands a full radius ahead and the shell opens to two. And
   * since the background is now veiled only where the shell is seen edge-on, the coverage went up
   * from a third to 0.6 — a guess, to be judged on screen, at how much blue sky the edge must hide
   * to stay orange instead of washing out to pink.
   *
   * @return the default re-entry configuration
   */
  public static ReentryFxConfig defaults() {
    return new ReentryFxConfig(
        100_000.0,
        11_000_000.0,
        new ColorRGBA(1f, 0.45f, 0.1f, 1f),
        new ColorRGBA(1f, 0.97f, 0.9f, 1f),
        0.6f,
        1.0f,
        2.5f,
        2.0f);
  }
}
