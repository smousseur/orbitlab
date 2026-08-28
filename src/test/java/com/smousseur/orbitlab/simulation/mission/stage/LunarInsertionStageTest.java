package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.LunarApproachFixture;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
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
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;

/**
 * MIS-5 / L4 §6.3 — the insertion burn, entered at the ignition point the approach coast stops at.
 *
 * <p>The chain is the two stages of the lot and nothing else: the fixture stands in for the three
 * days of transfer that precede them, which is what lets the lot close without a four-day flight.
 */
class LunarInsertionStageTest {
  private static final Logger logger = LogManager.getLogger(LunarInsertionStageTest.class);

  /** The band L4 §5 retains. */
  private static final double APSIDE_BAND = 500.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        LunarApproachFixture.orekitDataAvailable(), "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("The approach and the insertion deliver a circular orbit at the flown perilune")
  void theChainCircularisesAtTheFlownPerilune() {
    LunarApproachCoastStage coast = new LunarApproachCoastStage("Lunar approach");
    LunarInsertionStage insertion = new LunarInsertionStage("Lunar insertion");
    List<MissionStage> chain = List.of(coast, insertion);
    Mission mission = LunarApproachFixture.missionWith(chain);

    SpacecraftState inserted =
        StageChainRunner.sampling(null, 0.0, null)
            .run(chain, LunarApproachFixture.geocentric(), mission);

    KeplerianOrbit achieved = read(inserted);
    double radius = LunarApproachFixture.lunarContext().equatorialRadius();
    double perilune = achieved.getA() * (1.0 - achieved.getE()) - radius;
    double apolune = achieved.getA() * (1.0 + achieved.getE()) - radius;
    logger.info(
        "Chain delivered {} x {} km, e = {}, i = {}°, mass {} kg",
        String.format(Locale.ROOT, "%.3f", perilune / 1000.0),
        String.format(Locale.ROOT, "%.3f", apolune / 1000.0),
        String.format(Locale.ROOT, "%.2e", achieved.getE()),
        String.format(Locale.ROOT, "%.3f", Math.toDegrees(achieved.getI())),
        String.format(Locale.ROOT, "%.1f", inserted.getMass()));

    assertEquals(
        LunarApproachFixture.FLOWN_PERILUNE_ALTITUDE,
        perilune,
        APSIDE_BAND + 1_000.0,
        "perilune outside the band the fixture aims at");
    assertEquals(
        LunarApproachFixture.FLOWN_PERILUNE_ALTITUDE,
        apolune,
        APSIDE_BAND + 1_000.0,
        "apolune outside the band the fixture aims at");
    assertTrue(
        inserted.getMass() < LunarApproachFixture.orbiter().getMass(),
        "the insertion must have spent propellant");
  }

  @Test
  @DisplayName("Both passes fly the same burn")
  void bothPassesFlyTheSameBurn() {
    SpacecraftState start = LunarApproachFixture.geocentric();

    LunarApproachCoastStage walkCoast = new LunarApproachCoastStage("Lunar approach");
    LunarInsertionStage walkInsertion = new LunarInsertionStage("Lunar insertion");
    Mission walkMission = LunarApproachFixture.missionWith(List.of(walkCoast, walkInsertion));
    walkMission.setCurrentState(start);
    SpacecraftState atIgnition = walkCoast.propagateStandalone(start, walkMission);
    walkMission.setCurrentState(atIgnition);
    SpacecraftState walkExit = walkInsertion.propagateStandalone(atIgnition, walkMission);

    LunarApproachCoastStage chainCoast = new LunarApproachCoastStage("Lunar approach");
    LunarInsertionStage chainInsertion = new LunarInsertionStage("Lunar insertion");
    List<MissionStage> chain = List.of(chainCoast, chainInsertion);
    SpacecraftState chainExit =
        StageChainRunner.sampling(null, 0.0, null)
            .run(chain, start, LunarApproachFixture.missionWith(chain));

    double dateGap = chainExit.getDate().durationFrom(walkExit.getDate());
    double positionGap = Vector3D.distance(walkExit.getPosition(), chainExit.getPosition());
    double massGap = chainExit.getMass() - walkExit.getMass();
    logger.info(
        "Date gap {} s, position gap {} m, mass gap {} kg",
        String.format(Locale.ROOT, "%.3e", dateGap),
        String.format(Locale.ROOT, "%.3e", positionGap),
        String.format(Locale.ROOT, "%.3e", massGap));

    assertEquals(0.0, dateGap, 1.0e-6, "the two passes must cut off at the same instant");
    assertEquals(0.0, massGap, 1.0e-6, "and have burned the same propellant");
    assertEquals(0.0, positionGap, 1.0, "and be in the same place");
  }

  @Test
  @DisplayName("It refuses an approach that meets the surface")
  void itRefusesAnImpactingApproach() {
    LunarInsertionStage insertion = new LunarInsertionStage("Lunar insertion");
    Mission mission = LunarApproachFixture.missionWith(List.of(insertion));
    SpacecraftState impacting =
        LunarApproachFixture.selenocentric(600_000.0, LunarApproachFixture.RAAN_DEG);

    OrbitlabException refusal =
        assertThrows(OrbitlabException.class, () -> insertion.enter(impacting, mission));
    logger.info("Stage refusal: {}", refusal.getMessage());
  }

  private static KeplerianOrbit read(SpacecraftState state) {
    return new KeplerianOrbit(
        state.getPVCoordinates(),
        state.getFrame(),
        state.getDate(),
        LunarApproachFixture.lunarContext().mu());
  }
}
