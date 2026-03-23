package com.smousseur.orbitlab.states.scene;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.view.ViewMode;
import com.smousseur.orbitlab.core.SolarSystemBody;
import org.junit.jupiter.api.Test;

class SatelliteVisibilityTest {

  @Test
  void satellite_visibleWhenFocusedOnParent() {
    assertTrue(
        PlanetPoseAppState.isSatelliteVisible(
            SolarSystemBody.MOON, ViewMode.PLANET, SolarSystemBody.EARTH));
  }

  @Test
  void satellite_visibleWhenFocusedOnSelf() {
    assertTrue(
        PlanetPoseAppState.isSatelliteVisible(
            SolarSystemBody.MOON, ViewMode.PLANET, SolarSystemBody.MOON));
  }

  @Test
  void satellite_hiddenInSolarMode() {
    assertFalse(
        PlanetPoseAppState.isSatelliteVisible(
            SolarSystemBody.MOON, ViewMode.SOLAR, SolarSystemBody.SUN));
  }

  @Test
  void satellite_hiddenWhenFocusedOnOtherPlanet() {
    assertFalse(
        PlanetPoseAppState.isSatelliteVisible(
            SolarSystemBody.MOON, ViewMode.PLANET, SolarSystemBody.MARS));
  }
}
