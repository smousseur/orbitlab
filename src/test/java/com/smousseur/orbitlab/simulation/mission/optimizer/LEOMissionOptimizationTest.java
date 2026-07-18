package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(doubles = {300_000, 600_000, 800_000, 1_000_000})
  void testCircularMissions(double targetAltitude) {
    LEOMission mission = new LEOMission("LEO mission", targetAltitude);
    testMission(mission, targetAltitude, targetAltitude);
  }

  @ParameterizedTest(name = "perigee={0}m, apogee={1}m")
  @CsvSource({"300_000, 600_000", "600_000, 800_000", "200_000, 1_000_000"})
  void testEllipticMissions(double perigeeAltitude, double apogeeAltitude) {
    LEOMission mission = new LEOMission("LEO mission", perigeeAltitude, apogeeAltitude);
    testMission(mission, perigeeAltitude, apogeeAltitude);
  }

  @Test
  void testFalconHeavy() {
    LEOMission mission =
        new LEOMission(
            "Falcon Heavy",
            new LaunchConfiguration(
                Launchers.FALCON_HEAVY,
                new double[] {600_000, 100_000},
                Spacecraft.LEGACY),
            400_000);
    testMission(mission, 400_000, 400_000);
  }
}
