package com.smousseur.orbitlab.simulation.orbit.config;

import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.EnumMap;
import java.util.Objects;

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

  public int bodyPoints(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    return pointsByBody.getOrDefault(body, DEFAULT_POINTS_COUNT);
  }

  public double clampStepSeconds(double stepSeconds) {
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    return Math.max(DEFAULT_MIN_STEP, Math.min(DEFAULT_MAX_STEP, stepSeconds));
  }

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

    return new OrbitWindowConfig(points, 512, 64, 2 * 86400, 0.25);
  }
}
