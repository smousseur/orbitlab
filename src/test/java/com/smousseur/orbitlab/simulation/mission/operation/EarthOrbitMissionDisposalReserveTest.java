package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PHY-10 / L1 — a disposal reserve is dead mass through ascent. It counts in {@code getMass()} but
 * not in {@code hasUsablePropellant()}, which is the single switch deciding whether the upper stage
 * is dropped and the payload flies its own trim ({@code EarthOrbitMission.buildStages}). So a
 * reserve must never change the stage chain — the guarantee that keeps the four zero-tolerance
 * gates safe when a mission carries one.
 */
class EarthOrbitMissionDisposalReserveTest {

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double ALT = 0.0;
  private static final double TARGET = 400_000.0;

  private static final PayloadModel MODEL = Payloads.EARTH_OBSERVATION_SAT;
  private static final double DRY = 10_000;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void aReserveDoesNotFlipTheUpperStageDropDecision() {
    // Load 0 → the payload does not fly its own trim, so the upper stage stays. A reserve would
    // flip this only if hasUsablePropellant leaked it; it must not, so the chains stay identical.
    assertEquals(
        chainFor(MODEL.toSpacecraft(DRY, 0, 0)),
        chainFor(MODEL.toSpacecraft(DRY, 0, 500)),
        "a disposal reserve must not flip the upper-stage drop decision");
  }

  @Test
  void aReserveDoesNotChangeThePropelledChain() {
    // Load 77 → the payload flies its own final trim (upper stage dropped). The reserve rides as
    // dead mass and changes nothing in the chain.
    assertEquals(
        chainFor(MODEL.toSpacecraft(DRY, 77, 0)), chainFor(MODEL.toSpacecraft(DRY, 77, 500)));
    assertTrue(
        MODEL.toSpacecraft(DRY, 77, 500).hasUsablePropellant(),
        "the payload still flies its own trim with a reserve aboard");
  }

  private static List<String> chainFor(Spacecraft payload) {
    LaunchConfiguration config = LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, payload);
    EarthOrbitMission mission = new EarthOrbitMission("m", config, TARGET, TARGET, LAT, LON, ALT);
    return mission.getStages().stream().map(MissionStage::getName).toList();
  }
}
