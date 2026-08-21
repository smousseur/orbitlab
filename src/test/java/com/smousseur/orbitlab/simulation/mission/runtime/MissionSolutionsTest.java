package com.smousseur.orbitlab.simulation.mission.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The all-or-nothing rule of the replay, tested as what it is: a pure function of a composition and
 * a set of keys, with no propagation anywhere near it (spec {@code
 * docs/scenario/01-persistance-missions.md} §5.1).
 */
class MissionSolutionsTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Mission leoMission() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Replay coverage");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.769);
    values.put("LAUNCH_SITE_ALT", 14.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", 8_000.0);
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 400.0);
    return MissionComposer.compose(
        MissionFactory.specFromWizardValues(values, MissionType.LEO), OptimizationType.BALANCED);
  }

  private static List<String> stageKeys(Mission mission) {
    return mission.getStages().stream()
        .filter(OptimizableMissionStage.class::isInstance)
        .map(stage -> ((OptimizableMissionStage<?>) stage).optimizationKey())
        .toList();
  }

  private static MissionSolutions covering(Mission mission) {
    Map<String, double[]> vectors = new LinkedHashMap<>();
    stageKeys(mission).forEach(key -> vectors.put(key, new double[] {1.0, 2.0}));
    return new MissionSolutions(vectors, null);
  }

  @Test
  void exactCoverage_isTheOnlyOneAccepted() {
    Mission mission = leoMission();
    assertFalse(stageKeys(mission).isEmpty(), "the fixture must have something to cover");

    assertTrue(covering(mission).covers(mission));
  }

  /** One stage left to be optimized beside replayed ones is a trajectory nobody asked for. */
  @Test
  void missingKey_doesNotCover() {
    Mission mission = leoMission();
    Map<String, double[]> vectors = new LinkedHashMap<>(covering(mission).vectors());
    vectors.remove(stageKeys(mission).getFirst());

    assertFalse(new MissionSolutions(vectors, null).covers(mission));
  }

  /** A surplus key is the same mismatch seen from the other side: the file describes another
   * composition. */
  @Test
  void surplusKey_doesNotCover() {
    Mission mission = leoMission();
    Map<String, double[]> vectors = new LinkedHashMap<>(covering(mission).vectors());
    vectors.put("A stage this composition does not have", new double[] {0.0});

    assertFalse(new MissionSolutions(vectors, null).covers(mission));
  }

  @Test
  void emptySolutions_coverNothingThatHasStages() {
    Mission mission = leoMission();
    assertFalse(new MissionSolutions(Map.of(), null).covers(mission));
  }

  /** Outside PRECISE the loads are derived, so their absence is the normal case, not a defect. */
  @Test
  void launcherLoads_areOptional() {
    assertFalse(new MissionSolutions(Map.of(), null).hasLauncherLoads());
    assertFalse(new MissionSolutions(Map.of(), new double[0]).hasLauncherLoads());
    assertTrue(new MissionSolutions(Map.of(), new double[] {320_000.0}).hasLauncherLoads());
  }

  /** The vectors are handed out by copy: a replay must not be able to edit what it was given. */
  @Test
  void vectors_areNotSharedWithCallers() {
    Map<String, double[]> vectors = new LinkedHashMap<>();
    vectors.put("Gravity turn (S1)", new double[] {0.31, 12.4});
    MissionSolutions solutions = new MissionSolutions(vectors, null);

    vectors.put("Injected later", new double[] {0.0});
    solutions.vectorFor("Gravity turn (S1)")[0] = 99.0;

    assertNull(solutions.vectorFor("Injected later"));
    assertEquals(0.31, solutions.vectorFor("Gravity turn (S1)")[0], 1e-12);
  }

  @Test
  void unknownStage_hasNoVector() {
    assertNull(new MissionSolutions(Map.of(), null).vectorFor("Gravity turn (S1)"));
  }
}
