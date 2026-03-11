package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimzerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPlayer;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(LEOMissionOptimizationTest.class);

  public static final int ASCENSION_DURATION = 10;
  public static final double ORBIT_MARGIN_RATIO = 0.07;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(doubles = {185_000, 400_000})
  void testLEOMission(double targetAltitude) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Mission mission =
        new AbstractTrajectoryOptimizerTest.TestMission(
            "Gravity Turn", getStages(targetAltitude), getMissionVehicle(), 5.23, -52.77, 0.0, targetAltitude);
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);
    MissionOptimizer optimizer = new MissionOptimizer(mission, 40_000);
    MissionOptimzerResult optimResults = optimizer.optimize();
    mission =
        new AbstractTrajectoryOptimizerTest.TestMission(
            "Gravity Turn", getStages(targetAltitude), getMissionVehicle(), 5.23, -52.77, 0.0, targetAltitude);
    initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);
    MissionPlayer player = new MissionPlayer(mission);
    player.play(optimResults, epoch);
    PropagationResults results = propagateMission(mission, "Coasting", epoch);
    logger.info("[{}km] Max coast altitude: {} m", (int) (targetAltitude / 1000), results.maxCoastAltitude);
    logger.info("[{}km] Min coast altitude: {} m", (int) (targetAltitude / 1000), results.minCoastAltitude);
    double errorMargin = ORBIT_MARGIN_RATIO * targetAltitude;
    Assertions.assertTrue(
        Math.abs(results.maxCoastAltitude - targetAltitude) <= errorMargin,
        () -> String.format("Max coast altitude %.0f m not within %.0f m of target %.0f m",
            results.maxCoastAltitude, errorMargin, targetAltitude));
    Assertions.assertTrue(
        Math.abs(results.minCoastAltitude - targetAltitude) <= errorMargin,
        () -> String.format("Min coast altitude %.0f m not within %.0f m of target %.0f m",
            results.minCoastAltitude, errorMargin, targetAltitude));
  }

  private static VehicleStack getMissionVehicle() {
    return new VehicleStack(
        new ArrayList<>(
            List.of(
                LaunchVehicle.getLauncherStage1Vechicle(),
                LaunchVehicle.getLauncherStage2Vechicle(),
                Spacecraft.getSpacecraft())));
  }

  private static List<MissionStage> getStages(double targetAltitude) {
    return List.of(
        new VerticalAscentStage("Vertical Ascent", ASCENSION_DURATION),
        new GravityTurnStage(
            "Gravity turn",
            ASCENSION_DURATION,
            3.0,
            GravityTurnConstraints.forTarget(targetAltitude)),
        new TransfertTwoManeuverStage("Transfert", targetAltitude),
        new CoastingStage("Coasting", null));
  }
}
