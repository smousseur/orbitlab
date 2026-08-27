package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.objective.FlybyObjective;
import com.smousseur.orbitlab.simulation.mission.stage.AnalyticParkingInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ParkingCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarInjectionStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MIS-4 / L4 §8.1 — everything about the lunar spec and its composition that costs milliseconds:
 * the normalisations, the chain the composer builds, and the refusal that names the lot which will
 * replace it. Nothing here propagates.
 */
class LunarFlybyMissionTest {

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double PERILUNE_ALTITUDE = 100_000.0;
  private static final double CANAVERAL_LATITUDE = 28.562;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static MissionSpec.Lunar spec(MissionHorizon horizon, AtmosphereModel atmosphere) {
    return new MissionSpec.Lunar(
        "Lunar flyby",
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
        PARKING_ALTITUDE,
        PERILUNE_ALTITUDE,
        "Cape Canaveral",
        CANAVERAL_LATITUDE,
        -80.577,
        3.0,
        horizon,
        atmosphere);
  }

  @Test
  @DisplayName("A spec built without a horizon gets the seven days of the lot")
  void nullHorizon_normalisesToSevenDays() {
    assertEquals(
        new MissionHorizon.FixedDuration(7.0 * MissionHorizon.SECONDS_PER_DAY),
        spec(null, null).horizon());
    assertEquals(MissionType.LUNAR_FLYBY, spec(null, null).type());
  }

  @Test
  @DisplayName("A spec built without an atmosphere flies in vacuum")
  void nullAtmosphere_normalisesToNone() {
    assertEquals(AtmosphereModel.NONE, spec(null, null).atmosphere());
  }

  @Test
  @DisplayName("An explicit horizon is kept as it was asked for")
  void explicitHorizon_isNotOverwritten() {
    MissionHorizon asked = new MissionHorizon.FixedDuration(9.0 * MissionHorizon.SECONDS_PER_DAY);
    assertEquals(asked, spec(asked, AtmosphereModel.NONE).horizon());
  }

  @Test
  @DisplayName("The composer builds the ascent, the parking coast, the injection and the coast")
  void compose_yieldsTheLunarChain() {
    Mission mission = MissionComposer.compose(spec(null, null), OptimizationType.FAST);

    assertInstanceOf(LunarFlybyMission.class, mission);

    List<MissionStage> stages = mission.getStages();
    // The three lunar-specific phases close the chain, in this order. The ascent prefix ahead of
    // them is the ordinary one and is guarded by MissionAscentWiringTest.
    assertInstanceOf(
        AnalyticParkingInsertionStage.class, stages.get(stages.size() - 4), "parking insertion");
    assertInstanceOf(ParkingCoastStage.class, stages.get(stages.size() - 3), "parking coast");
    assertInstanceOf(
        TranslunarInjectionStage.class, stages.get(stages.size() - 2), "translunar injection");
    assertInstanceOf(TranslunarCoastStage.class, stages.getLast(), "translunar coast");

    // §3.3: no separation after the injection, so the chain ends on the coast and nothing
    // jettisons the spent upper stage.
    assertEquals("Coasting", stages.getLast().getName());

    FlybyObjective objective = assertInstanceOf(FlybyObjective.class, mission.getObjective());
    assertEquals(SolarSystemBody.MOON, objective.body());
    assertEquals(PERILUNE_ALTITUDE, objective.closestApproachAltitude(), 1e-9);
    assertEquals(LunarFlybyMission.PERILUNE_TOLERANCE, objective.toleranceMeters(), 1e-9);

    // The composer is the single writer of the horizon, on this branch as on the others.
    assertEquals(
        new MissionHorizon.FixedDuration(7.0 * MissionHorizon.SECONDS_PER_DAY),
        mission.getHorizon());
  }

  /**
   * §4.1 — the demo reads its band from the mission of the product, and the value did not move. If
   * the closure flight of §8.3 widens the band, this is where both classes learn it at once.
   */
  @Test
  void theDemoReadsItsPeriluneBandFromTheProductMission() {
    assertEquals(
        LunarFlybyMission.PERILUNE_TOLERANCE, LunarTransferMission.DEFAULT_PERILUNE_TOLERANCE, 0.0);
    assertEquals(10_000.0, LunarFlybyMission.PERILUNE_TOLERANCE, 0.0);
  }
}
