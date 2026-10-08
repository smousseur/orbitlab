package com.smousseur.orbitlab.engine;

/**
 * Tuning of the hover over planets and their orbits: the band the cursor must reach an orbit
 * within, how often the orbit polylines are sampled to find it, how fast a planet fades in and out,
 * and how its icon and its orbit look once lit.
 *
 * @param enterPx the screen distance, in pixels, within which an orbit is lit (must be positive)
 * @param exitPx the screen distance, in pixels, beyond which the lit orbit goes out; at least
 *     {@code enterPx}, the gap between the two being the hysteresis that keeps an orbit from
 *     flickering on its edge
 * @param detectionStride read one orbit point in this many when projecting the orbits (at least 1)
 * @param fadeInTauSec time constant of the fade toward lit, in real seconds (must be positive)
 * @param fadeOutTauSec time constant of the fade toward unlit, in real seconds (must be positive)
 * @param fadeSnap distance to its target under which the fade lands on it exactly
 * @param iconRestPx the icon's size at rest, in pixels
 * @param iconHoverPx the icon's size fully lit, in pixels
 * @param ringWhiteBlend how far toward white the icon's ring goes once fully lit, in {@code [0,1]}
 * @param labelWhiteBlend how far toward white the icon's name goes once fully lit, in {@code [0,1]}
 * @param orbitCoreGrowPx how much wider the orbit's core is once fully lit, in pixels (at least 0)
 * @param orbitCoreWhiteBlend how far toward white the orbit's core goes once fully lit, in {@code
 *     [0,1]}
 * @param haloPx the full width of the halo around a lit orbit, in pixels (must be positive)
 * @param haloPeakAlpha the halo's opacity on the orbit's axis once fully lit, in {@code [0,1]}
 */
public record HoverConfig(
    float enterPx,
    float exitPx,
    int detectionStride,
    float fadeInTauSec,
    float fadeOutTauSec,
    float fadeSnap,
    float iconRestPx,
    float iconHoverPx,
    float ringWhiteBlend,
    float labelWhiteBlend,
    float orbitCoreGrowPx,
    float orbitCoreWhiteBlend,
    float haloPx,
    float haloPeakAlpha) {

  public HoverConfig {
    if (!(enterPx > 0f) || !(exitPx >= enterPx)) {
      throw new IllegalArgumentException(
          "Expected 0 < enterPx <= exitPx, got enterPx=" + enterPx + ", exitPx=" + exitPx);
    }
    if (detectionStride < 1) {
      throw new IllegalArgumentException("detectionStride must be at least 1");
    }
    if (!(fadeInTauSec > 0f) || !(fadeOutTauSec > 0f)) {
      throw new IllegalArgumentException("fade time constants must be > 0");
    }
    if (!isUnitFraction(ringWhiteBlend) || !isUnitFraction(labelWhiteBlend)) {
      throw new IllegalArgumentException("white blends must be in [0,1]");
    }
    if (!(orbitCoreGrowPx >= 0f)) {
      throw new IllegalArgumentException("orbitCoreGrowPx must be >= 0");
    }
    if (!(haloPx > 0f)) {
      throw new IllegalArgumentException("haloPx must be > 0");
    }
    if (!isUnitFraction(orbitCoreWhiteBlend) || !isUnitFraction(haloPeakAlpha)) {
      throw new IllegalArgumentException("orbit white blend and halo peak must be in [0,1]");
    }
  }

  /**
   * Creates the default hover tuning.
   *
   * <p>The bands, 8 px to light and 12 px to put out, and the icon's 16 to 25 px are the chantier's
   * own values. One orbit point in 4 is the stride its baseline measured: the cheapest that held
   * the projection cost under budget on every run, at a 0.065 px error. The fade and the white
   * blends come from the mockup the hover was designed on — an exponential smoothing of 50 ms in
   * and 85 ms out, which covers 95 % of the way in three time constants, about 150 and 250 ms.
   *
   * <p>The lit orbit is the mockup's too: a core one pixel wider and 35 % of the way to white,
   * under a halo 18 px wide. The mockup draws that halo as three additive strokes of 18, 11 and 6
   * px at 0.06, 0.10 and 0.20; their sum on the axis, 0.36, is the peak here.
   *
   * @return the default hover configuration
   */
  public static HoverConfig defaults() {
    return new HoverConfig(
        8f, 12f, 4, 0.05f, 0.085f, 1e-3f, 16f, 25f, 0.45f, 0.85f, 1f, 0.35f, 18f, 0.36f);
  }

  private static boolean isUnitFraction(float value) {
    return value >= 0f && value <= 1f;
  }
}
