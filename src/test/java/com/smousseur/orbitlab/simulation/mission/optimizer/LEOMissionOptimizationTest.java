package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import org.junit.jupiter.api.Assertions;
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

  /**
   * Spec 06 I3 integration criterion: a LEO 400 km mission flying the analytic budget loads
   * converges, and the propellant left aboard at mission end (all of it sits in the sized S2)
   * stays under 15 % of that stage's load.
   */
  @Test
  void testFalconHeavyBudgetLoads() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, 400_000, 45.96);
    LEOMission mission =
        new LEOMission(
            "Falcon Heavy (budget loads)",
            new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, payload),
            400_000);

    MissionComputeResult result = testMission(mission, 400_000, 400_000);

    double s2Load = loads[1];
    double residual = result.performanceReport().totalPropellantResidual();
    Assertions.assertTrue(
        residual / s2Load < 0.15,
        () ->
            String.format(
                "S2 residual %.0f kg exceeds 15%% of its sized load %.0f kg", residual, s2Load));
  }
}
