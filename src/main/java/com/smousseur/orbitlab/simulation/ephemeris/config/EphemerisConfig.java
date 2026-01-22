package com.smousseur.orbitlab.simulation.ephemeris.config;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.EnumMap;
import java.util.Objects;

public record EphemerisConfig(
    double sampleStepSeconds,
    int windowPointsBack,
    int windowPointsForward,
    EnumMap<SolarSystemBody, Double> orbitalPeriodSecondsByBody) {
  public EphemerisConfig {
    if (!Double.isFinite(sampleStepSeconds) || sampleStepSeconds <= 0.0) {
      throw new IllegalArgumentException("sampleStepSeconds must be finite and > 0");
    }
    if (windowPointsBack < 2) {
      throw new IllegalArgumentException("windowPointsBack must be >= 2");
    }
    if (windowPointsForward < 2) {
      throw new IllegalArgumentException("windowPointsForward must be >= 2");
    }
    Objects.requireNonNull(orbitalPeriodSecondsByBody, "orbitalPeriodSecondsByBody");
  }

  public double orbitalPeriodSeconds(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    Double v = orbitalPeriodSecondsByBody.get(body);
    if (v == null) {
      throw new IllegalArgumentException(
          "No orbital period configured for bodyId=" + body.displayName());
    }
    return v;
  }

  public int windowTotalPoints() {
    return windowPointsBack + 1 + windowPointsForward;
  }

  /** Reasonable defaults (sidereal orbital periods, approximate) in seconds. */
  public static EphemerisConfig defaultSolarSystem() {
    double day = 86400.0;
    EnumMap<SolarSystemBody, Double> periods = new EnumMap<>(SolarSystemBody.class);
    periods.put(SolarSystemBody.MERCURY, 87.9691 * day);
    periods.put(SolarSystemBody.VENUS, 224.701 * day);
    periods.put(SolarSystemBody.EARTH, 365.256363004 * day);
    periods.put(SolarSystemBody.MARS, 686.980 * day);
    periods.put(SolarSystemBody.JUPITER, 4332.589 * day);
    periods.put(SolarSystemBody.SATURN, 10759.22 * day);
    periods.put(SolarSystemBody.URANUS, 30688.5 * day);
    periods.put(SolarSystemBody.NEPTUNE, 60182.0 * day);
    periods.put(SolarSystemBody.PLUTO, 90560.0 * day);

    return new EphemerisConfig(
        600.0, // 10 min step (starter value)
        200, // ~33h back at 10min
        400, // ~66h forward at 10min
        periods);
  }
}
