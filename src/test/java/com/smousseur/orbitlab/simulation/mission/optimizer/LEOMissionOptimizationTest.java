package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferTwoManeuverProblem;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimzerResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionPlayer;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.stage.JettisonStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.TransfertTwoManeuverStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {

  public static final int ASCENSION_DURATION = 10;
  public static final double TARGET_ALTITUDE = 400_000;

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
    propagateMission(mission, epoch);

    return;
    /*
       propagateMission(mission, epoch);
       GravityTurnProblem turnProblem =
           new GravityTurnProblem(
               mission.getCurrentState(),
               mission.getVehicle(),
               3.0,
               ASCENSION_DURATION,
               new GravityTurnConstraints(85_000, TARGET_ALTITUDE, 600_000));
       CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(turnProblem, 5000);
       OptimizationResult result = optimizer.optimize();
       SpacecraftState stateAfterTurn = result.bestState();
       mission.setCurrentState(stateAfterTurn);

       mission.getVehicle().jettison(0);
       mission.updateMass();

       PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
       Orbit orbit = mission.getCurrentState().getOrbit();
       TransferTwoManeuverProblem problem =
           new TransferTwoManeuverProblem(
               new KeplerianOrbit(orbit), mission.getVehicle().getMass(), TARGET_ALTITUDE, propulsion);
       optimizer = new CMAESTrajectoryOptimizer(problem, 5000);
       result = optimizer.optimize();
       SpacecraftState finalState = result.bestState();

       double finalAlt =
           finalState.getPVCoordinates().getPosition().getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
       System.out.printf("Final altitude: %.1f m (target: %.1f m)%n", finalAlt, TARGET_ALTITUDE);

       double finalEcc = finalState.getOrbit().getE();
       System.out.printf("Final eccentricity: %.6f (target: ~0)%n", finalEcc);

       System.out.printf("Final mass %.2f", finalState.getMass());

       assertEquals(TARGET_ALTITUDE, finalAlt, 50_000, "Final altitude within 50 km of target");
       assertTrue(finalEcc < 0.1, "Eccentricity should be < 0.1, got " + finalEcc);
    */
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
            new GravityTurnConstraints(
                80_000, TARGET_ALTITUDE - 200_000, TARGET_ALTITUDE + 200_000)),
        new JettisonStage("Jettison"),
        new TransfertTwoManeuverStage("Transfert", TARGET_ALTITUDE),
        new CoastingStage("Coasting", null));
  }
}
