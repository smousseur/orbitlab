package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.orekit.utils.Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.stage.JettisonStage;
import com.smousseur.orbitlab.simulation.mission.stage.MissionStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnStage;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.VerticalAscentStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {

  public static final int ASCENSION_DURATION = 60;
  public static final double TARGET_ALTITUDE = 300_000;

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
    propagateMission(mission, epoch);

    mission.updateMass();
    PropulsionSystem propulsion = mission.getVehicle().getPropulsion();
    Orbit orbit = mission.getCurrentState().getOrbit();
    TwoManeuverTransferProblem problem =
        new TwoManeuverTransferProblem(
            new KeplerianOrbit(orbit), mission.getVehicle().getMass(), TARGET_ALTITUDE, propulsion);
    CMAESTrajectoryOptimizer optimizer = new CMAESTrajectoryOptimizer(problem, 20000);
    OptimizationResult result = optimizer.optimize();
    SpacecraftState finalState = result.bestState();

    double finalAlt =
        finalState.getPVCoordinates().getPosition().getNorm() - WGS84_EARTH_EQUATORIAL_RADIUS;
    System.out.printf("Final altitude: %.1f m (target: %.1f m)%n", finalAlt, TARGET_ALTITUDE);

    double finalEcc = finalState.getOrbit().getE();
    System.out.printf("Final eccentricity: %.6f (target: ~0)%n", finalEcc);

    System.out.printf("Final mass %.2f", finalState.getMass());

    assertEquals(TARGET_ALTITUDE, finalAlt, 50_000, "Final altitude within 50 km of target");
    assertTrue(finalEcc < 0.1, "Eccentricity should be < 0.1, got " + finalEcc);

    return;
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
        new GravityTurnStage("Gravity Turn", ASCENSION_DURATION, 3),
        new JettisonStage("Separation stage 1"));
    //        new ConstantThrustStage("Conservation"),
    //        new JettisonStage("Separation stage 2"));
  }
}
