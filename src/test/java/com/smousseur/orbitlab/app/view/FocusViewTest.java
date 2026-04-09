package com.smousseur.orbitlab.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.EngineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link FocusView}'s mode state machine. This class has no JME3/Orekit
 * dependencies and can run without any runtime assets.
 */
class FocusViewTest {

  private FocusView focusView;
  private EngineConfig engineConfig;

  @BeforeEach
  void setUp() {
    engineConfig = EngineConfig.defaultSolarSystem();
    focusView = new FocusView(engineConfig);
  }

  @Test
  void defaultsToSolarOnSun() {
    assertEquals(ViewMode.SOLAR, focusView.getMode());
    assertEquals(SolarSystemBody.SUN, focusView.getBody());
    assertNull(focusView.getFocusedMission());
  }

  @Test
  void viewPlanetSwitchesToPlanetMode() {
    focusView.viewPlanet(SolarSystemBody.EARTH);

    assertEquals(ViewMode.PLANET, focusView.getMode());
    assertEquals(SolarSystemBody.EARTH, focusView.getBody());
    assertNull(focusView.getFocusedMission());
    assertTrue(focusView.isFocused(SolarSystemBody.EARTH));
    assertFalse(focusView.isFocused(SolarSystemBody.MARS));
  }

  @Test
  void viewSpacecraftStoresMissionAndParentBody() {
    focusView.viewSpacecraft("LEO-1", SolarSystemBody.EARTH);

    assertEquals(ViewMode.SPACECRAFT, focusView.getMode());
    assertEquals(SolarSystemBody.EARTH, focusView.getBody());
    assertEquals("LEO-1", focusView.getFocusedMission());
  }

  @Test
  void isFocusedReturnsFalseWhileInSpacecraftMode() {
    focusView.viewSpacecraft("LEO-1", SolarSystemBody.EARTH);

    // isFocused only returns true in PLANET mode by contract; document that here.
    assertFalse(focusView.isFocused(SolarSystemBody.EARTH));
  }

  @Test
  void isSatelliteVisibleIsFalseOutsidePlanetMode() {
    // In SOLAR/SPACECRAFT the Moon should not be considered visible as a satellite — only the
    // PLANET branch drives that contract. Pin it down.
    assertFalse(focusView.isSatelliteVisible(SolarSystemBody.MOON));

    focusView.viewSpacecraft("LEO-1", SolarSystemBody.EARTH);
    assertFalse(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void isSatelliteVisibleIsTrueInPlanetModeForParentBody() {
    focusView.viewPlanet(SolarSystemBody.EARTH);

    assertTrue(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void viewPlanetClearsFocusedMission() {
    focusView.viewSpacecraft("LEO-1", SolarSystemBody.EARTH);
    focusView.viewPlanet(SolarSystemBody.MARS);

    assertEquals(ViewMode.PLANET, focusView.getMode());
    assertEquals(SolarSystemBody.MARS, focusView.getBody());
    assertNull(focusView.getFocusedMission());
  }

  @Test
  void resetGoesBackToSolarSunWithNoMission() {
    focusView.viewSpacecraft("LEO-1", SolarSystemBody.EARTH);

    focusView.reset();

    assertEquals(ViewMode.SOLAR, focusView.getMode());
    assertEquals(SolarSystemBody.SUN, focusView.getBody());
    assertNull(focusView.getFocusedMission());
    assertEquals(
        engineConfig.orbitCamera().defaultDistance(), focusView.getCameraDistance(), 1e-6f);
  }

  @Test
  void setModeAndSetBodyDoNotAutomaticallyClearFocusedMission() {
    // Low-level setters are kept on the class for backwards compatibility. Document that they
    // do NOT touch the focused-mission field — only the high-level viewXxx / reset methods do.
    focusView.viewSpacecraft("LEO-1", SolarSystemBody.EARTH);

    focusView.setMode(ViewMode.PLANET);
    focusView.setBody(SolarSystemBody.EARTH);

    assertEquals("LEO-1", focusView.getFocusedMission());
  }
}
