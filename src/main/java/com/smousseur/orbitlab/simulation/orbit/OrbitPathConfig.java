package com.smousseur.orbitlab.simulation.orbit;

import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.EnumMap;
import java.util.Objects;

public record OrbitPathConfig(
    int defaultTargetPoints,
    EnumMap<SolarSystemBody, Integer> targetPointsByBody,
    double minStepSeconds,
    double maxStepSeconds) {
  public OrbitPathConfig {
    if (defaultTargetPoints < 16) {
      throw new IllegalArgumentException("defaultTargetPoints must be >= 16");
    }
    Objects.requireNonNull(targetPointsByBody, "targetPointsByBody");
    if (!Double.isFinite(minStepSeconds) || minStepSeconds <= 0.0) {
      throw new IllegalArgumentException("minStepSeconds must be finite and > 0");
    }
    if (!Double.isFinite(maxStepSeconds) || maxStepSeconds <= minStepSeconds) {
      throw new IllegalArgumentException("maxStepSeconds must be finite and > minStepSeconds");
    }
  }

  public int targetPoints(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    Integer v = targetPointsByBody.get(body);
    return (v != null) ? v : defaultTargetPoints;
  }

  public double clampStepSeconds(double stepSeconds) {
    if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
      throw new IllegalArgumentException("stepSeconds must be finite and > 0");
    }
    return Math.max(minStepSeconds, Math.min(maxStepSeconds, stepSeconds));
  }

  public static OrbitPathConfig defaultSolarSystem() {
    EnumMap<SolarSystemBody, Integer> points = new EnumMap<>(SolarSystemBody.class);

    points.put(SolarSystemBody.MERCURY, 300);
    points.put(SolarSystemBody.VENUS, 360);
    points.put(SolarSystemBody.EARTH, 400);
    points.put(SolarSystemBody.MARS, 420);
    points.put(SolarSystemBody.JUPITER, 500);
    points.put(SolarSystemBody.SATURN, 550);
    points.put(SolarSystemBody.URANUS, 600);
    points.put(SolarSystemBody.NEPTUNE, 650);
    points.put(SolarSystemBody.PLUTO, 700);

    return new OrbitPathConfig(
        400,
        points,
        60.0, // minStep: 1 minute
        7 * 86400.0 // maxStep: 7 days
        );
  }
}
