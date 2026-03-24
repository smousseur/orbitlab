package com.smousseur.orbitlab.simulation.orbit.config;

import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Configuration for orbit path windowing and caching.
 *
 * <p>Controls the number of sample points per body, the comfort margin and snap granularity
 * for rebuild decisions, and the allowed step size range. Use {@link #defaultSolarSystem()}
 * for reasonable defaults.
 *
 * @param pointsByBody the number of orbit sample points per celestial body
 * @param marginPoints the number of margin points for comfort zone calculations
 * @param snapPoints the number of snap points for center snapping granularity
 * @param minSizeSeconds the minimum window size in seconds
 * @param maxFraction the maximum comfort margin as a fraction of the orbital period
 */
public record OrbitWindowConfig(
    EnumMap<SolarSystemBody, Integer> pointsByBody,
    int marginPoints,
    int snapPoints,
    int minSizeSeconds,
    double maxFraction) {
  private static final int DEFAULT_POINTS_COUNT = 16;
  private static final double DEFAULT_MIN_STEP = 60.0; // 1 minute
  private static final double DEFAULT_MAX_STEP = 7 * 86400.0; // 7 days

  public OrbitWindowConfig {
    Objects.requireNonNull(pointsByBody, "pointsByBody");
  }

  /**
   * Returns the configured number of orbit sample points for the given body,
   * falling back to a default value if the body is not explicitly configured.
   *
   * @param body the celestial body
   * @return the number of sample points to use for the body's orbit path
   */
  public int bodyPoints(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    return pointsByBody.getOrDefault(body, DEFAULT_POINTS_COUNT);
  }

  /**
   * Clamps the given step size to the allowed range [1 minute, 7 days].
   *
   * @param stepSeconds the raw step size in seconds
   * @return the clamped step size in seconds
   * @throws IllegalArgumentException if stepSeconds is not positive or not finite
   */
  public double clampStepSeconds(double stepSeconds) {
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    return Math.max(DEFAULT_MIN_STEP, Math.min(DEFAULT_MAX_STEP, stepSeconds));
  }

  /**
   * Creates a default orbit window configuration for the solar system with 4096 points per
   * body, 512 margin points, 64 snap points, and a 2-day minimum window size.
   *
   * @return a default solar system orbit window configuration
   */
  public static OrbitWindowConfig defaultSolarSystem() {
    EnumMap<SolarSystemBody, Integer> points = new EnumMap<>(SolarSystemBody.class);

    points.put(SolarSystemBody.MERCURY, 4096);
    points.put(SolarSystemBody.VENUS, 4096);
    points.put(SolarSystemBody.EARTH, 4096);
    points.put(SolarSystemBody.MARS, 4096);
    points.put(SolarSystemBody.JUPITER, 4096);
    points.put(SolarSystemBody.SATURN, 4096);
    points.put(SolarSystemBody.URANUS, 4096);
    points.put(SolarSystemBody.NEPTUNE, 4096);
    points.put(SolarSystemBody.PLUTO, 4096);
    points.put(SolarSystemBody.MOON, 4096);

    return new OrbitWindowConfig(points, 512, 64, 2 * 86400, 0.25);
  }
}
