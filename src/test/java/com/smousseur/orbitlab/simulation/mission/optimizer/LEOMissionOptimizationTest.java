package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimzerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPlayer;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.JettisonStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.TransfertTwoManeuverStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(LEOMissionOptimizationTest.class);

  public static final int ASCENSION_DURATION = 10;
  public static final double TARGET_ALTITUDE = 400_000;
  public static final double ORBIT_MARGIN_RATIO = 0.07;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testLEOMission() {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Mission mission =
        new AbstractTrajectoryOptimizerTest.TestMission(
            "Gravity Turn", getStages(), getMissionVehicle(), 5.23, -52.77, 0.0, TARGET_ALTITUDE);
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);
    MissionOptimizer optimizer = new MissionOptimizer(mission);
    MissionOptimzerResult optimResults = optimizer.optimize();
    mission =
        new AbstractTrajectoryOptimizerTest.TestMission(
            "Gravity Turn", getStages(), getMissionVehicle(), 5.23, -52.77, 0.0, TARGET_ALTITUDE);
    initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);
    MissionPlayer player = new MissionPlayer(mission);
    player.play(optimResults, epoch);
    PropagationResults results = propagateMission(mission, "Coasting", epoch);
    logger.info("Max coast altitude: {} m", results.maxCoastAltitude);
    logger.info("Min coast altitude: {} m", results.minCoastAltitude);
    double errorMargin = ORBIT_MARGIN_RATIO * TARGET_ALTITUDE;
    Assertions.assertTrue(Math.abs(results.maxCoastAltitude - TARGET_ALTITUDE) <= errorMargin);
    Assertions.assertTrue(Math.abs(results.minCoastAltitude - TARGET_ALTITUDE) <= errorMargin);
  }

  private static VehicleStack getMissionVehicle() {
    return new VehicleStack(
        new ArrayList<>(
            List.of(
                LaunchVehicle.getLauncherStage1Vechicle(),
                LaunchVehicle.getLauncherStage2Vechicle(),
                Spacecraft.getSpacecraft())));
  }

  private static List<MissionStage> getStages() {
    return List.of(
        new VerticalAscentStage("Vertical Ascent", ASCENSION_DURATION),
        new GravityTurnStage(
            "Gravity turn",
            ASCENSION_DURATION,
            3.0,
            new GravityTurnConstraints(300_000, 350_000, 2000.0, 18.0)),
        new TransfertTwoManeuverStage("Transfert", TARGET_ALTITUDE),
        new CoastingStage("Coasting", null));
  }
}
