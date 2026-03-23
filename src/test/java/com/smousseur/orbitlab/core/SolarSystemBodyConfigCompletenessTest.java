package com.smousseur.orbitlab.core;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.engine.scene.PlanetColors;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.ephemeris.config.EphemerisConfig;
import com.smousseur.orbitlab.simulation.ephemeris.config.SlidingWindowConfig;
import com.smousseur.orbitlab.simulation.orbit.config.OrbitWindowConfig;
import org.junit.jupiter.api.Test;

/**
 * Ensures that every {@link SolarSystemBody} value is properly configured in all default configs.
 * This test catches missing entries when a new body is added to the enum.
 */
class SolarSystemBodyConfigCompletenessTest {

  @Test
  void allNonSunBodies_haveOrbitalPeriod() {
    EphemerisConfig cfg = EphemerisConfig.defaultSolarSystem();
    for (SolarSystemBody b : SolarSystemBody.values()) {
      if (b == SolarSystemBody.SUN) continue;
      assertDoesNotThrow(
          () -> cfg.orbitalPeriodSeconds(b),
          "Missing orbital period for " + b);
    }
  }

  @Test
  void allBodies_haveSlidingWindowStep() {
    SlidingWindowConfig cfg = SlidingWindowConfig.defaultSolarSystem();
    for (SolarSystemBody b : SolarSystemBody.values()) {
      assertDoesNotThrow(
          () -> cfg.stepSeconds(b),
          "Missing sliding window step for " + b);
    }
  }

  @Test
  void allNonSunBodies_haveOrbitPoints() {
    OrbitWindowConfig cfg = OrbitWindowConfig.defaultSolarSystem();
    for (SolarSystemBody b : SolarSystemBody.values()) {
      if (b == SolarSystemBody.SUN) continue;
      assertTrue(
          cfg.bodyPoints(b) > 0,
          "Missing or zero orbit points for " + b);
    }
  }

  @Test
  void allBodies_havePlanetRadius() {
    for (SolarSystemBody b : SolarSystemBody.values()) {
      assertDoesNotThrow(
          () -> PlanetRadius.radiusFor(b),
          "Missing radius for " + b);
    }
  }

  @Test
  void allBodies_havePlanetColor() {
    for (SolarSystemBody b : SolarSystemBody.values()) {
      assertNotNull(
          PlanetColors.colorFor(b),
          "Missing color for " + b);
    }
  }
}
