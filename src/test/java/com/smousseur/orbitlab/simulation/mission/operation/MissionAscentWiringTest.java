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
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every mission profile flies the ascent its staging plan declares — three explicit phases on a
 * sequential launcher, five when the launcher burns a parallel block and outlives it (étape 3 of
 * the explicit-staging migration; spec {@code docs/etagement/03-conception-L1.md} §3.5).
 *
 * <p>Cheap on purpose: no propagation, no optimizer. It holds the property the numeric fixtures
 * cannot — that <b>all four</b> construction sites were switched over, not just the one a test
 * happened to exercise. A profile left on a single-phase gravity turn would optimize and fly fine
 * while quietly keeping the implicit jettison this migration exists to remove.
 *
 * <p><b>The property used to be stronger, and {@code PHY-8 / L3} weakened it.</b> Until the Falcon
 * Heavy's core was throttled, the three-phase shape held for every launcher, and an Ariane 62 was
 * added here on purpose to show it was not an accident of Falcon Heavy's figures. It now
 * <em>is</em> a property of the launcher's figures: the Ariane flies three phases and the Falcon
 * five. What survives, and is what this fixture asserts, is that the shape flown is the one the
 * vehicle's staging plan declares — never a shape one construction site invented (spec {@code
 * docs/etagement/05-conception-L3.md} §7.5).
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
        // The second launcher is what keeps the property from collapsing into "whatever Falcon
        // Heavy does". Since PHY-8 / L3 the two shapes actually differ — three phases here, five
        // on the Falcon — so it is now the only profile exercising the sequential branch.
        Arguments.of(
            "LEO circular, Ariane 62 (analytic Hohmann transfer)",
            (Mission) new EarthOrbitMission("LEO Ariane 62", ariane62(), TARGET_ALT)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profiles")
  void everyProfile_fliesTheAscentItsStagingPlanDeclares(String profile, Mission mission) {
    List<MissionStage> stages = mission.getStages();
    StagingPlan plan = mission.getVehicle().stagingPlan();
    boolean corePhase = plan.hasParallelBlock() && !plan.parallelBlock().groupedJettison();

    // The vertical ascent still opens the flight; the ascent phases follow it in order.
    assertEquals("Vertical Ascent", stages.get(0).getName(), profile);
    assertEquals(expectedAscent(corePhase), ascentNames(stages, corePhase), profile);

    assertInstanceOf(GravityTurnFirstBurnStage.class, stages.get(1), profile);
    assertInstanceOf(StageSeparationStage.class, stages.get(2), profile);
    int secondBurn = corePhase ? 5 : 3;
    assertInstanceOf(GravityTurnSecondBurnStage.class, stages.get(secondBurn), profile);
  }

  private static List<String> expectedAscent(boolean corePhase) {
    return corePhase
        ? List.of(
            AscentSequence.FIRST_BURN_NAME,
            AscentSequence.BOOSTER_SEPARATION_NAME,
            AscentSequence.CORE_BURN_NAME,
            AscentSequence.SEPARATION_NAME,
            AscentSequence.SECOND_BURN_NAME)
        : List.of(
            AscentSequence.FIRST_BURN_NAME,
            AscentSequence.SEPARATION_NAME,
            AscentSequence.SECOND_BURN_NAME);
  }

  private static List<String> ascentNames(List<MissionStage> stages, boolean corePhase) {
    return stages.subList(1, (corePhase ? 5 : 3) + 1).stream().map(MissionStage::getName).toList();
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
