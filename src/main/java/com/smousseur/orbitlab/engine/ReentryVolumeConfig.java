package com.smousseur.orbitlab.engine;

/**
 * Tuning of the plasma volume a hot falling piece is wrapped in up close: a box around the piece in
 * which the user's prototype plasma — bow shock, shock layer, boundary layer, turbulent wake — is
 * ray-marched, at a reduced resolution, then laid over the near view.
 *
 * <p>The box is the prototype's, scaled to the piece: it reaches {@code aheadRadii} ahead of the
 * nose, {@code behindRadii} beyond the base, and {@code halfWidthRadii} to each side of the
 * air-relative axis, all in radii of the piece's body. Its length in between is the piece's own.
 *
 * @param steps the most samples a ray takes through the box before its last body radius (at least
 *     1): the shader spaces them a fixed distance apart, so a shorter ray takes fewer, and a ray
 *     that ends on the body adds a fixed few over its last radius, where the boundary layer lies
 * @param resolutionDivisor how many times smaller than the near view, in each direction, the volume
 *     is marched (at least 1)
 * @param aheadRadii how far ahead of the nose the box reaches, in body radii (finite, at least 0)
 * @param behindRadii how far beyond the base the box reaches, in body radii (finite, positive)
 * @param halfWidthRadii how far to each side of the axis the box reaches, in body radii (finite,
 *     above the body's own radius of 1)
 * @param soundSpeedMs the speed of sound the Mach number is taken against, in m/s (finite,
 *     positive)
 * @param emissionGain how much light the plasma gives at a heat intensity of 1 (finite, positive)
 * @param exposure the exposure of the volume's own tone mapping (finite, positive)
 */
public record ReentryVolumeConfig(
    int steps,
    int resolutionDivisor,
    float aheadRadii,
    float behindRadii,
    float halfWidthRadii,
    float soundSpeedMs,
    float emissionGain,
    float exposure) {

  public ReentryVolumeConfig {
    if (steps < 1) {
      throw new IllegalArgumentException("steps must be at least 1, got " + steps);
    }
    if (resolutionDivisor < 1) {
      throw new IllegalArgumentException(
          "resolutionDivisor must be at least 1, got " + resolutionDivisor);
    }
    if (!Float.isFinite(aheadRadii) || aheadRadii < 0f) {
      throw new IllegalArgumentException("aheadRadii must be finite and >= 0, got " + aheadRadii);
    }
    requirePositive(behindRadii, "behindRadii");
    if (!Float.isFinite(halfWidthRadii) || halfWidthRadii <= 1f) {
      throw new IllegalArgumentException(
          "halfWidthRadii must be finite and > 1, got " + halfWidthRadii);
    }
    requirePositive(soundSpeedMs, "soundSpeedMs");
    requirePositive(emissionGain, "emissionGain");
    requirePositive(exposure, "exposure");
  }

  /**
   * Creates the default volume tuning.
   *
   * <p>The steps and the resolution are measured, on this project's reference GPU (an RTX 2060), at
   * the application's 1280x720: the prototype marched at full resolution, with the camera inside
   * the volume, cost 17 ms at 56 steps and 30 ms at 96, against a budget of 2 ms. At half
   * resolution, its noise read from a 3D texture and its samples spaced 0.45 body radius apart up
   * to 56, with 16 more over the last radius of a ray that ends on the body, the volume and the
   * composite cost at most 1.83 ms at the median, the camera inside the volume and a side view from
   * 40 m being the costliest views measured; inside the volume, 1.86 ms at the 95th percentile. The
   * box is the prototype's, which held its shock and wake. The speed of sound is the prototype's.
   * The gain and the exposure are starting points, to be set on screen: a gain of 6 lights the
   * hottest piece, at an intensity of 1, as the prototype lit its hottest case, and 0.7 is the
   * prototype's default exposure.
   *
   * @return the default volume configuration
   */
  public static ReentryVolumeConfig defaults() {
    return new ReentryVolumeConfig(56, 2, 3f, 20f, 11f, 290f, 6f, 0.7f);
  }

  private static void requirePositive(float value, String name) {
    if (!Float.isFinite(value) || value <= 0f) {
      throw new IllegalArgumentException(name + " must be finite and > 0, got " + value);
    }
  }
}
