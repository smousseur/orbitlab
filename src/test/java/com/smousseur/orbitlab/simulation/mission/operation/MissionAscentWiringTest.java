package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnSecondBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every mission profile flies the ascent as three explicit phases (étape 3 of spec {@code
 * docs/mission-stages/01-separations-implicites.md} §8).
 *
 * <p>Cheap on purpose: no propagation, no optimizer. It holds the property the numeric fixtures
 * cannot — that <b>all four</b> construction sites were switched over, not just the one a test
 * happened to exercise. A profile left on a single-phase gravity turn would optimize and fly fine
 * while quietly keeping the implicit jettison this migration exists to remove.
 */
class MissionAscentWiringTest {

  private static final double TARGET_ALT = 400_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static LaunchConfiguration falconHeavy() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static LaunchConfiguration ariane62() {
    return LaunchConfiguration.fullyLoaded(Launchers.ARIANE_62, Spacecraft.LEGACY);
  }

  static Stream<Arguments> profiles() {
    return Stream.of(
        Arguments.of(
            "LEO circular (analytic Hohmann transfer)",
            (Mission) new EarthOrbitMission("LEO analytic", falconHeavy(), TARGET_ALT)),
        Arguments.of(
            "LEO circular (optimized transfer)",
            (Mission)
                EarthOrbitMission.circularWithOptimizedTransfer(
                    "LEO optimized", falconHeavy(), TARGET_ALT)),
        Arguments.of(
            "LEO elliptic (optimized transfer)",
            (Mission)
                EarthOrbitMission.ellipticWithOptimizedTransfer(
                    "LEO elliptic", falconHeavy(), TARGET_ALT, 800_000.0)),
        Arguments.of("GEO", (Mission) new GEOMission("GEO", TARGET_ALT, GEOMission.GEO_ALTITUDE)),
        // A second launcher holds the property where a single one could not: the three-phase
        // ascent is a property of every mission, not a shape Falcon Heavy's figures happen to
        // produce (spec docs/launchers/01-ariane-62.md §6.2).
        Arguments.of(
            "LEO circular, Ariane 62 (analytic Hohmann transfer)",
            (Mission) new EarthOrbitMission("LEO Ariane 62", ariane62(), TARGET_ALT)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profiles")
  void everyProfile_fliesTheThreePhaseAscent(String profile, Mission mission) {
    List<MissionStage> stages = mission.getStages();

    // The vertical ascent still opens the flight; the three ascent phases follow it in order.
    assertEquals("Vertical Ascent", stages.get(0).getName(), profile);
    assertEquals(AscentSequence.FIRST_BURN_NAME, stages.get(1).getName(), profile);
    assertEquals(AscentSequence.SEPARATION_NAME, stages.get(2).getName(), profile);
    assertEquals(AscentSequence.SECOND_BURN_NAME, stages.get(3).getName(), profile);

    assertInstanceOf(GravityTurnFirstBurnStage.class, stages.get(1), profile);
    assertInstanceOf(StageSeparationStage.class, stages.get(2), profile);
    assertInstanceOf(GravityTurnSecondBurnStage.class, stages.get(3), profile);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profiles")
  void everyProfile_hasExactlyOneOptimizableAscentPhase(String profile, Mission mission) {
    // The turn is optimized as a whole, by the first burn only: a second optimizable ascent phase
    // would mean two CMA-ES problems fighting over the same transition time.
    long optimizableAscentPhases =
        mission.getStages().stream().filter(GravityTurnFirstBurnStage.class::isInstance).count();
    assertEquals(1, optimizableAscentPhases, profile);
  }
}
