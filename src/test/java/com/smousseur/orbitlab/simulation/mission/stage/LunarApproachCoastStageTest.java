package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.LunarApproachFixture;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;

/**
 * MIS-5 / L4 §6.2 — the selenocentric approach, on the fabricated hyperbola of {@link
 * LunarApproachFixture}.
 *
 * <p>Twin of {@link TranslunarCoastStageTest} in shape and in motive: the stage walk and the
 * ephemeris pass fly the same phase through two different entry points, and nothing but a test
 * keeps them saying the same thing. Here they must also <em>convert the same frame</em>, which is
 * the one defect of the lot that raises nothing when it happens.
 */
class LunarApproachCoastStageTest {
  private static final Logger logger = LogManager.getLogger(LunarApproachCoastStageTest.class);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        LunarApproachFixture.orekitDataAvailable(), "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("Both passes stop at the same ignition point")
  void bothPassesStopAtTheSameIgnition() {
    SpacecraftState start = LunarApproachFixture.geocentric();

    LunarApproachCoastStage walkCoast = new LunarApproachCoastStage("Lunar approach");
    Mission walkMission = LunarApproachFixture.missionWith(List.of(walkCoast));
    walkMission.setCurrentState(start);
    SpacecraftState walkExit = walkCoast.propagateStandalone(start, walkMission);

    LunarApproachCoastStage chainCoast = new LunarApproachCoastStage("Lunar approach");
    Mission chainMission = LunarApproachFixture.missionWith(List.of(chainCoast));
    SpacecraftState chainExit =
        StageChainRunner.sampling(null, 0.0, null).run(List.of(chainCoast), start, chainMission);

    double flownHours = walkExit.getDate().durationFrom(start.getDate()) / 3600.0;
    double dateGap = chainExit.getDate().durationFrom(walkExit.getDate());
    double positionGap = Vector3D.distance(walkExit.getPosition(), chainExit.getPosition());
    logger.info(
        "Both passes coasted {} h; date gap {} s, position gap {} m",
        String.format(Locale.ROOT, "%.4f", flownHours),
        String.format(Locale.ROOT, "%.3e", dateGap),
        String.format(Locale.ROOT, "%.3e", positionGap));

    // Teeth: a coast collapsed to nothing, or one that ran to some arbitrary bound, would make the
    // agreement meaningless. L0 measure 3 puts the approach at 16.3 to 18.3 h.
    assertTrue(
        flownHours > 15.0 && flownHours < 19.0,
        "the approach must be most of a day, got " + flownHours + " h");
    assertEquals(0.0, dateGap, 1.0e-6, "the two passes must stop at the same ignition date");
    assertEquals(0.0, positionGap, 1.0, "and therefore at the same place");
  }

  /**
   * The hole {@code docs/lunar-orbit/03-conception-L1.md} §8 pt 1 wrote before meeting it. Both
   * passes hand this stage a geocentric state — {@code StageChainRunner} calls {@code enter} before
   * {@code StageLegRunner.fly} converts, and the optimize pass never converts at all. A state left
   * in GCRF would be integrated with a lunar µ at the centre, silently.
   */
  @Test
  @DisplayName("A geocentric entry state comes out selenocentric, on both passes")
  void theEntryStateIsConvertedOnBothPasses() {
    SpacecraftState start = LunarApproachFixture.geocentric();
    assertSame(
        OrekitService.get().gcrf(),
        start.getFrame(),
        "the fixture must hand over what the translunar coast hands over");

    LunarApproachCoastStage coast = new LunarApproachCoastStage("Lunar approach");
    Mission mission = LunarApproachFixture.missionWith(List.of(coast));

    SpacecraftState entered = coast.enter(start, mission);
    assertSame(
        LunarApproachFixture.lunarContext().inertialFrame(),
        entered.getFrame(),
        "enter must publish the selenocentric state, or getCurrentState and the propagation"
            + " disagree about one instant");
    assertEquals(
        LunarApproachFixture.lunarContext().mu(),
        entered.getOrbit().getMu(),
        0.0,
        "and it must carry the lunar µ");

    mission.setCurrentState(start);
    SpacecraftState walked = coast.propagateStandalone(start, mission);
    assertSame(
        LunarApproachFixture.lunarContext().inertialFrame(),
        walked.getFrame(),
        "the stage walk converts nothing itself, so the stage must");

    SpacecraftState chained =
        StageChainRunner.sampling(null, 0.0, null)
            .run(List.of(coast), start, LunarApproachFixture.missionWith(List.of(coast)));
    assertSame(
        LunarApproachFixture.lunarContext().inertialFrame(),
        chained.getFrame(),
        "and the ephemeris pass must end selenocentric too");
  }

  @Test
  @DisplayName("It ends on a date of its own and watches no boundary")
  void itEndsOnADateAndWatchesNoBoundary() {
    LunarApproachCoastStage coast = new LunarApproachCoastStage("Lunar approach");
    Mission mission = LunarApproachFixture.missionWith(List.of(coast));
    SpacecraftState start = LunarApproachFixture.geocentric();

    assertTrue(
        coast.soiTransitions(mission).isEmpty(),
        "it starts ON the sphere; arming a boundary here would cut it at the first step");
    assertEquals(
        false, coast.soiCrossingEndsStage(mission), "the crossing belongs to the coast before it");
    assertEquals(false, coast.isPropulsive(), "and it burns nothing");

    StageChainRunner.sampling(null, 0.0, null).run(List.of(coast), start, mission);
    assertNotNull(
        coast.getConfiguredEndDate(),
        "a stage bounded by StageChainRunner's 7200 s net would stop 17 h short and report itself"
            + " complete");
  }
}
