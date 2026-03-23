package com.smousseur.orbitlab.simulation.ephemeris.config;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Immutable configuration for ephemeris computation and buffering.
 *
 * <p>Defines the sample step size, window extent (points back and forward), and the known
 * sidereal orbital periods for each solar system body. Use {@link #defaultSolarSystem()} to
 * obtain a configuration with reasonable defaults.
 *
 * @param sampleStepSeconds the time interval between consecutive ephemeris samples in seconds
 * @param windowPointsBack the number of sample points before the center of the window
 * @param windowPointsForward the number of sample points after the center of the window
 * @param orbitalPeriodSecondsByBody the approximate sidereal orbital period for each body in seconds
 */
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

  /**
   * Returns the configured orbital period for the given body.
   *
   * @param body the solar system body
   * @return the orbital period in seconds
   * @throws IllegalArgumentException if no period is configured for the body
   */
  public double orbitalPeriodSeconds(SolarSystemBody body) {
    Objects.requireNonNull(body, "body");
    Double v = orbitalPeriodSecondsByBody.get(body);
    if (v == null) {
      throw new IllegalArgumentException(
          "No orbital period configured for bodyId=" + body.displayName());
    }
    return v;
  }

  /**
   * Returns the total number of sample points in the window (back + center + forward).
   *
   * @return the total point count
   */
  public int windowTotalPoints() {
    return windowPointsBack + 1 + windowPointsForward;
  }

  /**
   * Creates a default configuration with approximate sidereal orbital periods for all
   * solar system bodies, a 10-minute sample step, and a window spanning roughly 33 hours
   * back and 66 hours forward.
   *
   * @return a default solar system ephemeris configuration
   */
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
    periods.put(SolarSystemBody.MOON, 27.321661 * day);

    return new EphemerisConfig(
        600.0, // 10 min step (starter value)
        200, // ~33h back at 10min
        400, // ~66h forward at 10min
        periods);
  }
}
