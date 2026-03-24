package com.smousseur.orbitlab.app;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import org.orekit.time.AbsoluteDate;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Immutable configuration for the orbital simulation session.
 *
 * <p>Defines which solar system bodies to simulate, ephemeris computation settings,
 * sliding window buffer parameters, and orbit rendering window configuration.
 *
 * @param orbitBodies         the set of solar system bodies whose orbits are simulated
 * @param ephemerisConfig     configuration for ephemeris computation
 * @param slidingWindowConfig configuration for the sliding window ephemeris buffer
 * @param orbitWindowConfig   configuration for the orbit visualization window
 */
public record SimulationConfig(
    EnumSet<SolarSystemBody> orbitBodies,
    EphemerisConfig ephemerisConfig,
    SlidingWindowConfig slidingWindowConfig,
    OrbitWindowConfig orbitWindowConfig) {

  public SimulationConfig {
    Objects.requireNonNull(orbitBodies, "orbitBodies");
    Objects.requireNonNull(ephemerisConfig, "ephemerisConfig");
    Objects.requireNonNull(slidingWindowConfig, "slidingWindowConfig");
    Objects.requireNonNull(orbitWindowConfig, "orbitWindowConfig");
  }

  /** Clock start is always "now" (UTC) for a user session. */
  public AbsoluteDate computeClockStart() {
    return OrekitTime.utcNow();
  }

  /** Orbit reference start follows the session time (same as clockStart). */
  public AbsoluteDate computeOrbitReferenceStart(AbsoluteDate clockStart) {
    Objects.requireNonNull(clockStart, "clockStart");
    return clockStart;
  }

  /**
   * Creates a default configuration for a full solar system simulation including all major planets
   * and Pluto.
   *
   * @return a new default solar system simulation configuration
   */
  public static SimulationConfig defaultSolarSystem() {
    return new SimulationConfig(
        EnumSet.of(
            SolarSystemBody.MERCURY,
            SolarSystemBody.VENUS,
            SolarSystemBody.EARTH,
            SolarSystemBody.MARS,
            SolarSystemBody.JUPITER,
            SolarSystemBody.SATURN,
            SolarSystemBody.URANUS,
            SolarSystemBody.NEPTUNE,
            SolarSystemBody.PLUTO,
            SolarSystemBody.MOON),
        EphemerisConfig.defaultSolarSystem(),
        SlidingWindowConfig.defaultSolarSystem(),
        OrbitWindowConfig.defaultSolarSystem());
  }
}
