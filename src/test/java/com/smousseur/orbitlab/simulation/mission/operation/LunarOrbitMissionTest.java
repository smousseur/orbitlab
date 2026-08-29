package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.LunarApproachCoastStage;
import com.smousseur.orbitlab.simulation.mission.stage.LunarInsertionStage;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.TranslunarCoastStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MIS-5 / L5 §9.2 — everything about the lunar orbit spec and its composition that costs
 * milliseconds. Nothing here propagates; the flight is {@link LunarOrbitFlightTest}.
 *
 * <p>Shaped after {@link LunarFlybyMissionTest}, including the choice not to add a {@code
 * MissionComposerTest}: the repository asserts each branch of the composer beside the mission it
 * builds, and a class per composer branch would scatter the same fixture over two files.
 */
class LunarOrbitMissionTest {

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double ORBIT_ALTITUDE = 100_000.0;
  private static final double CANAVERAL_LATITUDE = 28.562;

  /** Dry mass and insertion load of the catalogue orbiter, as L3 sizes them at 100 km. */
  private static final double ORBITER_DRY_MASS = 2_000.0;

  private static final double ORBITER_INSERTION_LOAD = 657.7;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static MissionSpec.LunarOrbit spec(MissionHorizon horizon, AtmosphereModel atmosphere) {
    return new MissionSpec.LunarOrbit(
        "Lunar orbit",
        LaunchConfiguration.fullyLoaded(
            Launchers.FALCON_HEAVY,
            Payloads.LUNAR_ORBITER.toSpacecraft(ORBITER_DRY_MASS, ORBITER_INSERTION_LOAD)),
        PARKING_ALTITUDE,
        ORBIT_ALTITUDE,
        "Cape Canaveral",
        CANAVERAL_LATITUDE,
        -80.577,
        3.0,
        horizon,
        atmosphere);
  }

  @Test
  @DisplayName("A spec built without a horizon gets the twelve revolutions of the profile")
  void nullHorizon_normalisesToTwelveRevolutions() {
    assertEquals(
        new MissionHorizon.Revolutions(MissionHorizon.DEFAULT_LUNAR_ORBIT_REVOLUTIONS),
        spec(null, null).horizon());
    assertEquals(MissionType.LUNAR_ORBIT, spec(null, null).type());
  }

  @Test
  @DisplayName("A spec built without an atmosphere flies in vacuum")
  void nullAtmosphere_normalisesToNone() {
    assertEquals(AtmosphereModel.NONE, spec(null, null).atmosphere());
  }

  @Test
  @DisplayName("The composer builds the twelve stages, in any optimization mode")
  void compose_yieldsTheLunarOrbitChain() {
    for (OptimizationType mode : OptimizationType.values()) {
      Mission mission = MissionComposer.compose(spec(null, null), mode);
      assertInstanceOf(LunarOrbitMission.class, mission, mode.name());

      List<MissionStage> stages = mission.getStages();
      assertEquals(12, stages.size(), mode.name());

      // The five phases the lot adds, in order, closing the chain. The ascent prefix ahead of them
      // is the ordinary one and is guarded by MissionAscentWiringTest.
      assertInstanceOf(TLIBurnStage.class, stages.get(6), "translunar injection");
      assertInstanceOf(StageSeparationStage.class, stages.get(7), "S2 separation");
      assertInstanceOf(TranslunarCoastStage.class, stages.get(8), "translunar coast");
      assertInstanceOf(LunarApproachCoastStage.class, stages.get(9), "lunar approach");
      assertInstanceOf(LunarInsertionStage.class, stages.get(10), "lunar orbit insertion");
      assertInstanceOf(CoastingStage.class, stages.getLast(), "terminal coast");
    }
  }

  @Test
  @DisplayName("The terminal coast is named Coasting and flies the lunar arc")
  void theTerminalCoastCarriesTheArcAndTheName() {
    Mission mission = MissionComposer.compose(spec(null, null), OptimizationType.FAST);
    MissionStage terminal = mission.getStages().getLast();

    // The one load-bearing string of the chain: MissionLoadEvaluator.FINAL_COAST_STAGE compares
    // against it to decide which samples the insertion objective is scored on.
    assertEquals("Coasting", terminal.getName());

    // Without this declaration StageLegRunner would convert the arrival back into GCRF, and the
    // mission would be measured against the Earth from 380 000 km away (§3.1).
    assertEquals(
        GravitationalContext.moon().withPerturbers(SolarSystemBody.EARTH, SolarSystemBody.SUN),
        terminal.gravitationalContext(mission));
  }

  @Test
  @DisplayName("The objective is a circular lunar insertion carrying no inclination")
  void theObjectiveTargetsTheMoonAndNoPlane() {
    Mission mission = MissionComposer.compose(spec(null, null), OptimizationType.FAST);
    OrbitInsertionObjective objective =
        assertInstanceOf(OrbitInsertionObjective.class, mission.getObjective());

    assertEquals(SolarSystemBody.MOON, objective.body());
    assertEquals(ORBIT_ALTITUDE, objective.perigeeAltitude(), 1e-9);
    assertEquals(ORBIT_ALTITUDE, objective.apogeeAltitude(), 1e-9);
    // NaN and not a plausible number: the inclination is undergone, not aimed at, and the closed
    // form that would predict it is wrong by 20.3° at one epoch in four (L0 measure 1).
    assertTrue(
        Double.isNaN(objective.inclination()), "the objective must carry no target inclination");
  }

  @Test
  @DisplayName("The composer is the single writer of the horizon on this branch too")
  void compose_appliesTheSpecHorizon() {
    MissionHorizon asked = new MissionHorizon.Revolutions(3);
    assertEquals(
        asked, MissionComposer.compose(spec(asked, null), OptimizationType.FAST).getHorizon());
  }

  /** §3.3 — the bound is derived from the transfer's own time of flight, not a fresh number. */
  @Test
  void theTranslunarCoastBoundIsFiveDaysDerivedFromTheTimeOfFlight() {
    assertEquals(5.0 * 86_400.0, LunarOrbitMission.TRANSLUNAR_COAST_BOUND_SECONDS, 1e-9);
    assertEquals(
        TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS * 1.25,
        LunarOrbitMission.TRANSLUNAR_COAST_BOUND_SECONDS,
        0.0);
  }
}
