package com.smousseur.orbitlab.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.EngineConfig;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link FocusView}'s mode state machine. This class has no JME3/Orekit
 * dependencies and can run without any runtime assets.
 */
class FocusViewTest {

  private FocusView focusView;
  private EngineConfig engineConfig;
  private MissionId leo1;

  @BeforeEach
  void setUp() {
    engineConfig = EngineConfig.defaultSolarSystem();
    focusView = new FocusView(engineConfig);
    leo1 = MissionId.newId();
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
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);

    assertEquals(ViewMode.SPACECRAFT, focusView.getMode());
    assertEquals(SolarSystemBody.EARTH, focusView.getBody());
    assertEquals(leo1, focusView.getFocusedMission());
  }

  @Test
  void isFocusedReturnsFalseWhileInSpacecraftMode() {
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);

    // isFocused only returns true in PLANET mode by contract; document that here.
    assertFalse(focusView.isFocused(SolarSystemBody.EARTH));
  }

  @Test
  void isSatelliteVisibleIsFalseInSolarMode() {
    // Solar mode draws no planet-scale scene at all, so no satellite belongs on screen. This used
    // to also pin SPACECRAFT to false, which was the bug rather than the contract — see
    // isSatelliteVisibleFollowsTheParentBodyInSpacecraftMode.
    assertFalse(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void isSatelliteVisibleIsFalseWhenFocusingAnUnrelatedBody() {
    focusView.viewPlanet(SolarSystemBody.MARS);

    assertFalse(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void isMissionVisibleOnlyAroundTheFocusedBody() {
    focusView.viewPlanet(SolarSystemBody.EARTH);
    assertTrue(focusView.isMissionVisible(Set.of(SolarSystemBody.EARTH)));

    // The reported bug: focusing the Moon kept Earth-centred missions on screen, because the rule
    // tested the view mode and never which body was being looked at.
    focusView.viewPlanet(SolarSystemBody.MOON);
    assertFalse(focusView.isMissionVisible(Set.of(SolarSystemBody.EARTH)));
  }

  @Test
  void isMissionVisibleIsFalseInSolarMode() {
    assertFalse(focusView.isMissionVisible(Set.of(SolarSystemBody.EARTH)));
  }

  @Test
  void isMissionVisibleKeepsSiblingMissionsWhileFollowingOne() {
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);

    assertTrue(focusView.isMissionVisible(Set.of(SolarSystemBody.EARTH)));
    assertFalse(focusView.isMissionVisible(Set.of(SolarSystemBody.MARS)));
  }

  @Test
  void isMissionVisibleAboutAnyBodyTheTrajectoryFliesAbout() {
    // PHY-4 / L5: a lunar transfer flies about the Earth and then about the Moon, and every vertex
    // is converted into whichever of the two is being looked at. Both viewings are legitimate.
    // Keeping the objective's body as the criterion would have hidden such a mission for the whole
    // of its terrestrial ascent and shown it only at the sphere-of-influence crossing.
    Set<SolarSystemBody> transfer = Set.of(SolarSystemBody.EARTH, SolarSystemBody.MOON);

    focusView.viewPlanet(SolarSystemBody.EARTH);
    assertTrue(focusView.isMissionVisible(transfer), "the arc it launched from");

    focusView.viewPlanet(SolarSystemBody.MOON);
    assertTrue(focusView.isMissionVisible(transfer), "the arc it arrives in");

    focusView.viewPlanet(SolarSystemBody.MARS);
    assertFalse(focusView.isMissionVisible(transfer), "a body it never flies about");
  }

  @Test
  void isSatelliteVisibleFollowsTheParentBodyInSpacecraftMode() {
    // Following a mission in Earth orbit still looks at the Earth system: viewSpacecraft keeps the
    // parent body precisely so planet-scale rendering carries on, so the Moon must not vanish.
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);

    assertTrue(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void isSatelliteVisibleIsTrueInPlanetModeForParentBody() {
    focusView.viewPlanet(SolarSystemBody.EARTH);

    assertTrue(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void aPendingTransitionMakesTheViewPlanetScaleEarly() {
    // The far clip floor keys on this. Held to the source alone it would collapse mid-fly-in and
    // sweep the whole system out of the far viewport, then drop it back on the final frame.
    assertFalse(focusView.isPlanetScale());

    focusView.beginTransition(ViewMode.PLANET, SolarSystemBody.EARTH);
    assertTrue(focusView.isPlanetScale());
    assertEquals(ViewMode.SOLAR, focusView.getMode(), "the focus itself must not have moved");

    focusView.endTransition();
    assertFalse(focusView.isPlanetScale());
  }

  @Test
  void aPendingTransitionKeepsTheViewPlanetScaleOnTheWayOut() {
    focusView.viewPlanet(SolarSystemBody.EARTH);
    focusView.beginTransition(ViewMode.SOLAR, SolarSystemBody.SUN);

    assertTrue(focusView.isPlanetScale(), "flying out is still planet scale until it lands");
  }

  @Test
  void satellitesOfTheDestinationShowDuringTheApproach() {
    focusView.beginTransition(ViewMode.PLANET, SolarSystemBody.EARTH);

    assertTrue(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void satellitesOfTheSourceKeepShowingOnTheWayOut() {
    focusView.viewPlanet(SolarSystemBody.EARTH);
    focusView.beginTransition(ViewMode.SOLAR, SolarSystemBody.SUN);

    assertTrue(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void unrelatedSatellitesStayHiddenDuringATransition() {
    focusView.beginTransition(ViewMode.PLANET, SolarSystemBody.MARS);

    assertFalse(focusView.isSatelliteVisible(SolarSystemBody.MOON));
  }

  @Test
  void missionsIgnoreATransitionsDestination() {
    // Deliberately NOT symmetric with the satellites above: a mission's trajectory is drawn in the
    // near viewport, whose origin is still the transition's source. Showing an Earth mission while
    // the frame is centred on the Sun would draw its orbit around the Sun.
    focusView.beginTransition(ViewMode.PLANET, SolarSystemBody.EARTH);

    assertFalse(focusView.isMissionVisible(Set.of(SolarSystemBody.EARTH)));
  }

  @Test
  void missionsOfTheSourceKeepShowingOnTheWayOut() {
    focusView.viewPlanet(SolarSystemBody.EARTH);
    focusView.beginTransition(ViewMode.SOLAR, SolarSystemBody.SUN);

    assertTrue(focusView.isMissionVisible(Set.of(SolarSystemBody.EARTH)));
  }

  @Test
  void viewPlanetClearsFocusedMission() {
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);
    focusView.viewPlanet(SolarSystemBody.MARS);

    assertEquals(ViewMode.PLANET, focusView.getMode());
    assertEquals(SolarSystemBody.MARS, focusView.getBody());
    assertNull(focusView.getFocusedMission());
  }

  @Test
  void resetGoesBackToSolarSunWithNoMission() {
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);

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
    focusView.viewSpacecraft(leo1, SolarSystemBody.EARTH);

    focusView.setMode(ViewMode.PLANET);
    focusView.setBody(SolarSystemBody.EARTH);

    assertEquals(leo1, focusView.getFocusedMission());
  }
}
