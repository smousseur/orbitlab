package com.smousseur.orbitlab.simulation.ephemeris.config;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.EnumMap;
import java.util.Objects;

public record SlidingWindowConfig(
    double speedMaxAbs,
    double lookaheadRealSeconds,
    int minPointsEachSide,
    int maxPointsEachSide,
    double marginRatio,
    EnumMap<SolarSystemBody, Double> stepSecondsByBody) {
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
  }

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

  public static SlidingWindowConfig defaultSolarSystem() {
    EnumMap<SolarSystemBody, Double> steps = new EnumMap<>(SolarSystemBody.class);
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

    return new SlidingWindowConfig(2_000_000.0, 10.0, 8, 2_000, 0.25, steps);
  }
}
