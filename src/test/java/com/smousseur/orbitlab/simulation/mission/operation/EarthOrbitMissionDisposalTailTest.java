package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An Earth-orbit mission carries a disposal tail exactly when its payload carries a disposal
 * reserve — and the tail sits beside the stage chain, which a reserve leaves identical in names and
 * in types. That identity is what keeps the optimize pass, and the zero-tolerance gates behind it,
 * blind to the tail.
 */
class EarthOrbitMissionDisposalTailTest {

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double ALT = 0.0;
  private static final double PERIGEE = 400_000.0;
  private static final double APOGEE = 800_000.0;

  private static final PayloadModel MODEL = Payloads.EARTH_OBSERVATION_SAT;
  private static final double DRY = 10_000;

  /** An inert payload keeps the upper stage to the end; a propelled one flies its own trim. */
  private static final double[] LOADS = {0, 77};

  private static final List<Function<Spacecraft, EarthOrbitMission>> VARIANTS =
      List.of(
          payload ->
              new EarthOrbitMission(
                  "hohmann", configuration(payload), PERIGEE, APOGEE, LAT, LON, ALT),
          payload ->
              EarthOrbitMission.circularWithOptimizedTransfer(
                  "circular", configuration(payload), PERIGEE, LAT, LON, ALT),
          payload ->
              EarthOrbitMission.ellipticWithOptimizedTransfer(
                  "elliptic", configuration(payload), PERIGEE, APOGEE, LAT, LON, ALT));

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void noReserveMeansNoTail() {
    for (double load : LOADS) {
      for (Function<Spacecraft, EarthOrbitMission> variant : VARIANTS) {
        EarthOrbitMission mission = variant.apply(MODEL.toSpacecraft(DRY, load, 0));
        assertFalse(mission.hasDisposalTail(), mission.getName() + ", load " + load);
      }
    }
    assertFalse(new EarthOrbitMission("legacy", PERIGEE).hasDisposalTail());
  }

  @Test
  void aReserveGivesATailAndLeavesTheStagesAlone() {
    for (double load : LOADS) {
      for (Function<Spacecraft, EarthOrbitMission> variant : VARIANTS) {
        EarthOrbitMission without = variant.apply(MODEL.toSpacecraft(DRY, load, 0));
        EarthOrbitMission with = variant.apply(MODEL.toSpacecraft(DRY, load, 500));

        String label = with.getName() + ", load " + load;
        assertTrue(with.hasDisposalTail(), label);
        assertEquals(names(without.getStages()), names(with.getStages()), label);
        assertEquals(types(without.getStages()), types(with.getStages()), label);
      }
    }
  }

  private static LaunchConfiguration configuration(Spacecraft payload) {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, payload);
  }

  private static List<String> names(List<MissionStage> stages) {
    return stages.stream().map(MissionStage::getName).toList();
  }

  private static List<Class<?>> types(List<MissionStage> stages) {
    return stages.stream().<Class<?>>map(MissionStage::getClass).toList();
  }
}
