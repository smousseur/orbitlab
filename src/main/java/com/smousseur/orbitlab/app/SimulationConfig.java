package com.smousseur.orbitlab.app;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.orbit.OrbitPathConfig;
import org.orekit.time.AbsoluteDate;

import java.util.EnumSet;
import java.util.Objects;

public record SimulationConfig(
    EnumSet<SolarSystemBody> orbitWarmupBodies,
    EphemerisConfig ephemerisConfig,
    SlidingWindowConfig slidingWindowConfig,
    OrbitPathConfig orbitPathConfig) {

  public SimulationConfig {
    Objects.requireNonNull(orbitWarmupBodies, "orbitWarmupBodies");
    Objects.requireNonNull(ephemerisConfig, "ephemerisConfig");
    Objects.requireNonNull(slidingWindowConfig, "slidingWindowConfig");
    Objects.requireNonNull(orbitPathConfig, "orbitPathConfig");
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
            SolarSystemBody.PLUTO),
        EphemerisConfig.defaultSolarSystem(),
        SlidingWindowConfig.defaultSolarSystem(),
        OrbitPathConfig.defaultSolarSystem());
  }
}
