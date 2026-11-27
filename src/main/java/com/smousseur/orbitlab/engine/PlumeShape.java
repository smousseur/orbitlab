package com.smousseur.orbitlab.engine;

/**
 * The shape of one engine's plume in one kind of air: a cone from the nozzle's exit, measured in
 * radii of the nozzle cluster it leaves.
 *
 * @param lengthRadii how far the plume reaches past the exit plane (finite, positive)
 * @param halfAngleDeg how wide the cone opens, its half angle in degrees (in {@code [0, 80]})
 * @param brightness how much light it gives at full intensity (finite, at least 0)
 * @param opacity how much of what lies behind it hides, per cluster radius crossed (finite, at
 *     least 0)
 * @param diamonds how bright its shock diamonds are, 0 for none (finite, at least 0)
 */
public record PlumeShape(
    float lengthRadii, float halfAngleDeg, float brightness, float opacity, float diamonds) {

  public PlumeShape {
    if (!Float.isFinite(lengthRadii) || lengthRadii <= 0f) {
      throw new IllegalArgumentException("lengthRadii must be finite and > 0, got " + lengthRadii);
    }
    if (!(halfAngleDeg >= 0f && halfAngleDeg <= 80f)) {
      throw new IllegalArgumentException("halfAngleDeg must be in [0, 80], got " + halfAngleDeg);
    }
    requireNonNegative(brightness, "brightness");
    requireNonNegative(opacity, "opacity");
    requireNonNegative(diamonds, "diamonds");
  }

  /**
   * The shape a fraction {@code t} of the way from this one to {@code other}.
   *
   * @param other the shape at {@code t = 1}
   * @param t the fraction, in {@code [0, 1]}
   * @return the blend, field by field
   */
  public PlumeShape blend(PlumeShape other, float t) {
    return new PlumeShape(
        lerp(lengthRadii, other.lengthRadii, t),
        lerp(halfAngleDeg, other.halfAngleDeg, t),
        lerp(brightness, other.brightness, t),
        lerp(opacity, other.opacity, t),
        lerp(diamonds, other.diamonds, t));
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  private static void requireNonNegative(float value, String name) {
    if (!Float.isFinite(value) || value < 0f) {
      throw new IllegalArgumentException(name + " must be finite and >= 0, got " + value);
    }
  }
}
