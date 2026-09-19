package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageNames;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PHY-5 / L4: a LEO whose payload carries usable propellant drops its upper stage after the
 * transfer and lets the payload fly its own final trim; an inert payload keeps flying the upper
 * stage to the end exactly as before.
 */
class EarthOrbitMissionPayloadDeliveryTest {

  private static final double TARGET_ALT = 400_000.0;
  private static final double CANAVERAL_LAT = 28.5;
  private static final double CANAVERAL_LON = -80.6;

  private static LaunchConfiguration with(Spacecraft payload) {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, payload);
  }

  private static Spacecraft propelledPayload() {
    return Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 50);
  }

  private static List<String> stageNames(EarthOrbitMission mission) {
    return mission.getStages().stream().map(MissionStage::getName).toList();
  }

  @Test
  void propelledPayloadSeparatesTheUpperStageBeforeItsOwnTrim() {
    List<String> names =
        stageNames(new EarthOrbitMission("propelled", with(propelledPayload()), TARGET_ALT));

    assertTrue(
        names.contains(StageNames.UPPER_SEPARATION), () -> "expected an S2 separation: " + names);
    assertTrue(
        names.indexOf("Transfert") < names.indexOf(StageNames.UPPER_SEPARATION)
            && names.indexOf(StageNames.UPPER_SEPARATION) < names.indexOf("Trim"),
        () -> "S2 separation must sit between Transfert and Trim: " + names);
  }

  @Test
  void inertPayloadKeepsTodaysChain() {
    List<String> names =
        stageNames(new EarthOrbitMission("inert", with(Spacecraft.LEGACY), TARGET_ALT));

    assertFalse(
        names.contains(StageNames.UPPER_SEPARATION),
        () -> "an inert LEO must not drop its upper stage: " + names);
    assertTrue(names.indexOf("Transfert") < names.indexOf("Trim"));
  }

  @Test
  void commandedPlaneTrimStaysOnTheUpperStageBeforeSeparation() {
    List<String> names =
        stageNames(
            new EarthOrbitMission(
                "polar",
                with(propelledPayload()),
                TARGET_ALT,
                TARGET_ALT,
                LaunchPlane.ofDegrees(90.0),
                CANAVERAL_LAT,
                CANAVERAL_LON,
                0.0));

    assertTrue(names.contains("Plane trim"), () -> "polar ascent should clean its plane: " + names);
    assertTrue(
        names.indexOf("Transfert") < names.indexOf("Plane trim")
            && names.indexOf("Plane trim") < names.indexOf(StageNames.UPPER_SEPARATION)
            && names.indexOf(StageNames.UPPER_SEPARATION) < names.indexOf("Trim"),
        () -> "order must be Transfert, Plane trim, S2 separation, Trim: " + names);
  }

  @Test
  void inertCommandedPlaneKeepsTodaysOrder() {
    List<String> names =
        stageNames(
            new EarthOrbitMission(
                "polar-inert",
                with(Spacecraft.LEGACY),
                TARGET_ALT,
                TARGET_ALT,
                LaunchPlane.ofDegrees(90.0),
                CANAVERAL_LAT,
                CANAVERAL_LON,
                0.0));

    assertFalse(names.contains(StageNames.UPPER_SEPARATION));
    assertEquals(
        List.of("Transfert", "Trim", "Plane trim"),
        names.stream().filter(n -> List.of("Transfert", "Trim", "Plane trim").contains(n)).toList(),
        () -> "inert order stays Transfert, Trim, Plane trim: " + names);
  }
}
