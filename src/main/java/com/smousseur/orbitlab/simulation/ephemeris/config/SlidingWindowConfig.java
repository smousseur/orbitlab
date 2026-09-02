package com.smousseur.orbitlab.simulation.ephemeris.config;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the adaptive sliding window ephemeris system.
 *
 * <p>Controls how the ephemeris worker sizes and repositions per-body buffers based on the current
 * simulation clock speed. The window adapts by increasing the sample step at higher speeds to keep
 * the total point count bounded.
 *
 * <p><b>Two dynamics share one grid, and the slower one must not size it.</b> A body's base step is
 * chosen from its <i>orbital</i> motion — Neptune takes 165 years to circle the Sun, so 7 days is
 * ample for its position — but the same grid also carries the body's <i>rotation</i>, which turns
 * in 16 hours. {@code SlidingWindowEphemerisBuffer} interpolates attitude by SLERP, which always
 * negotiates the shortest arc: past half a turn between two samples the whole-turn count is lost
 * silently, and the body renders slower than it spins, or backwards. Measured before the fix
 * (`docs/bugs.md`, {@code BUG-19}): Neptune at 4,1 % of its true rate, Saturn and Uranus reversed,
 * and the Earth reversed then frozen once the adaptive step doubled. {@link #plan} therefore caps
 * the step at {@link #ROTATION_SAMPLES_PER_TURN} samples per rotation.
 *
 * @param speedMaxAbs the maximum absolute clock speed to plan for (speeds above this are clamped)
 * @param lookaheadRealSeconds the real-time lookahead duration used to size the window
 * @param minPointsEachSide the minimum number of sample points on each side of the center
 * @param maxPointsEachSide the maximum number of sample points on each side of the center
 * @param marginRatio the fraction of points used as comfort margin before triggering a rebuild (0,
 *     0.5)
 * @param stepSecondsByBody the base sample step in seconds for each celestial body
 * @param rotationPeriodSecondsByBody the sidereal rotation period in seconds for each celestial
 *     body, which caps that body's sample step
 */
public record SlidingWindowConfig(
    double speedMaxAbs,
    double lookaheadRealSeconds,
    int minPointsEachSide,
    int maxPointsEachSide,
    double marginRatio,
    Map<SolarSystemBody, Double> stepSecondsByBody,
    Map<SolarSystemBody, Double> rotationPeriodSecondsByBody) {

  /**
   * Minimum number of grid samples per body rotation.
   *
   * <p>The SLERP breaks at two — half a turn between samples — so this leaves a factor of two
   * before the cliff. Beyond four the margin buys nothing measurable: sampled at a quarter turn,
   * the SLERP reproduces the true attitude to 0,0000° for nine of the eleven bodies, and to 0,0030°
   * for the Moon, whose pole model carries real precession terms.
   */
  public static final int ROTATION_SAMPLES_PER_TURN = 4;

  public SlidingWindowConfig {
    if (!Double.isFinite(speedMaxAbs) || speedMaxAbs <= 0.0) {
      throw new IllegalArgumentException("speedMaxAbs must be finite and > 0");
    }
    if (!Double.isFinite(lookaheadRealSeconds) || lookaheadRealSeconds <= 0.0) {
      throw new IllegalArgumentException("lookaheadRealSeconds must be finite and > 0");
    }
    if (minPointsEachSide < 2) {
      throw new IllegalArgumentException("minPointsEachSide must be >= 2");
    }
    if (maxPointsEachSide < minPointsEachSide) {
      throw new IllegalArgumentException("maxPointsEachSide must be >= minPointsEachSide");
    }
    if (!Double.isFinite(marginRatio) || marginRatio <= 0.0 || marginRatio >= 0.5) {
      throw new IllegalArgumentException("marginRatio must be in (0, 0.5)");
    }
    Objects.requireNonNull(stepSecondsByBody, "stepSecondsByBody");
    Objects.requireNonNull(rotationPeriodSecondsByBody, "rotationPeriodSecondsByBody");
  }

  /**
   * Returns the configured base sample step for the given body.
   *
   * @param body the solar system body
   * @return the base sample step in seconds
   * @throws IllegalArgumentException if no step is configured for the body or the value is invalid
   */
  public double stepSeconds(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    Double v = stepSecondsByBody.get(body);
    if (v == null) {
      throw new IllegalArgumentException(
          "No window step configured for bodyId=" + body.displayName());
    }
    if (!Double.isFinite(v) || v <= 0.0) {
      throw new IllegalArgumentException(
          "Invalid stepSeconds for bodyId=" + body.displayName() + ": " + v);
    }
    return v;
  }

  /**
   * Returns the configured sidereal rotation period for the given body.
   *
   * @param body the solar system body
   * @return the rotation period in seconds
   * @throws IllegalArgumentException if no period is configured for the body or the value is
   *     invalid
   */
  public double rotationPeriodSeconds(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    Double v = rotationPeriodSecondsByBody.get(body);
    if (v == null) {
      throw new IllegalArgumentException(
          "No rotation period configured for bodyId=" + body.displayName());
    }
    if (!Double.isFinite(v) || v <= 0.0) {
      throw new IllegalArgumentException(
          "Invalid rotationPeriodSeconds for bodyId=" + body.displayName() + ": " + v);
    }
    return v;
  }

  /**
   * Computes an adaptive window plan for the given body and clock speed.
   *
   * <p>At higher clock speeds, the sample step is increased (in power-of-two multiples of the base
   * step) to keep the number of points bounded, while the margin is scaled proportionally.
   *
   * <p>That bound is then given up for whichever bodies need it: the rotation cap wins over the
   * point target, so a fast rotator at a high clock speed gets the points its attitude demands
   * rather than the 256 the doubling aims for. {@code maxPointsEachSide} remains the backstop, and
   * it does bite at the top speed for Jupiter and Saturn — their window is truncated instead of
   * their rotation being aliased, which is the trade this method intends.
   *
   * @param body the celestial body to plan for
   * @param clockSpeedAbs the absolute value of the current clock speed multiplier
   * @return the computed window plan
   */
  public WindowPlan plan(SolarSystemBody body, double clockSpeedAbs) {
    double speedAbs = Math.min(Math.abs(clockSpeedAbs), speedMaxAbs);

    double baseStep = stepSeconds(body);

    double lookaheadSimSeconds = speedAbs * lookaheadRealSeconds;

    // Keep rebuild cost bounded by targeting a reasonable number of points per side.
    // At high speed, we increase step (power-of-two multiple of base step).
    final int targetPointsEachSide = 256;

    double step = baseStep;

    double rawPointsEachSide = lookaheadSimSeconds / step;
    if (rawPointsEachSide > targetPointsEachSide) {
      double desiredStep = lookaheadSimSeconds / targetPointsEachSide;
      long mult = (long) Math.ceil(desiredStep / baseStep);
      mult = roundUpToPowerOfTwo(Math.max(1L, mult));
      step = baseStep * mult;
    }

    // After the doubling, never before it: capping baseStep would let the multiplier carry the step
    // back over half a turn, which is how the Earth ends up spinning backwards above 864 000x.
    step = Math.min(step, rotationPeriodSeconds(body) / ROTATION_SAMPLES_PER_TURN);

    int points = (int) Math.ceil(lookaheadSimSeconds / step);
    points = clamp(points, minPointsEachSide, maxPointsEachSide);

    int marginPoints = Math.max(2, (int) Math.floor(points * marginRatio));
    return new WindowPlan(step, points, points, marginPoints);
  }

  private static long roundUpToPowerOfTwo(long x) {
    if (x <= 1L) return 1L;
    long v = x - 1L;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v |= v >> 32;
    return v + 1L;
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  /**
   * An immutable plan describing the concrete window parameters for a single buffer rebuild.
   *
   * @param stepSeconds the time interval between consecutive samples in seconds
   * @param pointsBack the number of sample points before the center
   * @param pointsForward the number of sample points after the center
   * @param marginPoints the number of margin points defining the comfort zone boundary
   */
  public record WindowPlan(
      double stepSeconds, int pointsBack, int pointsForward, int marginPoints) {
    public WindowPlan {
      if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0)
        throw new IllegalArgumentException("stepSeconds");
      if (pointsBack < 2) throw new IllegalArgumentException("pointsBack");
      if (pointsForward < 2) throw new IllegalArgumentException("pointsForward");
      if (marginPoints < 0) throw new IllegalArgumentException("marginPoints");
    }
  }

  /**
   * Creates a default sliding window configuration for the solar system with per-body base sample
   * steps ranging from 3 hours (Mercury) to 14 days (Pluto), each capped by the body's rotation.
   *
   * @return a default solar system sliding window configuration
   */
  public static SlidingWindowConfig defaultSolarSystem() {
    Map<SolarSystemBody, Double> steps = new EnumMap<>(SolarSystemBody.class);
    steps.put(SolarSystemBody.SUN, 6 * 3600.0);
    steps.put(SolarSystemBody.MERCURY, 3 * 3600.0);
    steps.put(SolarSystemBody.VENUS, 6 * 3600.0);
    steps.put(SolarSystemBody.EARTH, 6 * 3600.0);
    steps.put(SolarSystemBody.MARS, 12 * 3600.0);
    steps.put(SolarSystemBody.JUPITER, 86400.0);
    steps.put(SolarSystemBody.SATURN, 2 * 86400.0);
    steps.put(SolarSystemBody.URANUS, 4 * 86400.0);
    steps.put(SolarSystemBody.NEPTUNE, 7 * 86400.0);
    steps.put(SolarSystemBody.PLUTO, 14 * 86400.0);
    steps.put(SolarSystemBody.MOON, 1800.0);

    return new SlidingWindowConfig(
        2_000_000.0, 10.0, 8, 2_000, 0.25, steps, defaultRotationPeriods());
  }

  /**
   * Sidereal rotation periods, in seconds, matching the IAU pole models Orekit exposes through
   * {@code getBodyOrientedFrame} — the same rotation the ephemeris dataset stores. The giants are
   * their System III radio periods.
   *
   * <p>The Moon's value repeats the sidereal <i>orbital</i> period {@code EphemerisConfig} already
   * carries. That is not a copy-paste: the Moon is in synchronous rotation, so the two periods are
   * the same quantity.
   *
   * @return the rotation period of every solar system body, in seconds
   */
  private static Map<SolarSystemBody, Double> defaultRotationPeriods() {
    Map<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.SUN, 25.38 * 86400.0);
    periods.put(SolarSystemBody.MERCURY, 58.6462 * 86400.0);
    periods.put(SolarSystemBody.VENUS, 243.018 * 86400.0);
    periods.put(SolarSystemBody.EARTH, 86164.1);
    periods.put(SolarSystemBody.MARS, 88642.7);
    periods.put(SolarSystemBody.JUPITER, 9 * 3600.0 + 55 * 60.0 + 29.7);
    periods.put(SolarSystemBody.SATURN, 10 * 3600.0 + 39 * 60.0 + 22.4);
    periods.put(SolarSystemBody.URANUS, 17 * 3600.0 + 14 * 60.0 + 24.0);
    periods.put(SolarSystemBody.NEPTUNE, 16 * 3600.0 + 6 * 60.0 + 36.0);
    periods.put(SolarSystemBody.PLUTO, 6.387230 * 86400.0);
    periods.put(SolarSystemBody.MOON, 27.321661 * 86400.0);
    return periods;
  }
}
