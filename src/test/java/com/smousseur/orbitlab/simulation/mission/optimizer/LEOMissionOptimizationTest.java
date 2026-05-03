package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.LEOMission;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

public class LEOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(LEOMissionOptimizationTest.class);

  public static final double ORBIT_MARGIN_RATIO = 0.07;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @RepeatedTest(50)
  void testSensibleMission() {
    testLEOMission(275_000);
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(
      doubles = {
        185_000, 200_000, 225_000, 250_000, 275_000, 300_000, 325_000, 350_000, 375_000, 400_000,
        425_000, 450_000, 475_000, 500_000, 525_000, 550_000, 600_000, 625_000, 650_000, 675_000,
        700_000, 725_000, 750_000, 775_000, 800_000, 825_000, 850_000, 875_000, 900_000, 925_000,
        950_000, 975_000, 1_000_000
      })
  void testMissionsLowAlt(double targetAltitude) {
    testLEOMission(targetAltitude);
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(
      doubles = {
        1_050_000, 1_200_000, 1_225_000, 1_250_000, 1_275_000, 1_300_000, 1_325_000, 1_350_000,
            1_375_000, 1_400_000,
        1_425_000, 1_450_000, 1_475_000, 1_500_000, 1_525_000, 1_550_000, 1_600_000, 1_625_000,
            1_650_000, 1_675_000,
        1_700_000, 1_725_000, 1_750_000, 1_775_000, 1_800_000, 1_825_000, 1_850_000, 1_875_000,
            1_900_000, 1_925_000,
        1_950_000, 1_975_000, 2_000_000
      })
  void testMissionsHighAlt(double targetAltitude) {
    testLEOMission(targetAltitude);
  }

  void testLEOMission(double targetAltitude) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Mission mission = new LEOMission("LEO mission", targetAltitude);
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    MissionOptimizer optimizer = new MissionOptimizer(mission, 40_000);
    MissionComputeResult computeResult = optimizer.optimize();
    MissionEphemeris ephemeris = computeResult.ephemeris();

    MinMaxAltitudeResults results = extractMinMaxAltitudes(ephemeris, "Coasting");

    logger.info(
        "[{}km] Max coast altitude: {} m", (int) (targetAltitude / 1000), results.maxAltitude);
    logger.info(
        "[{}km] Min coast altitude: {} m", (int) (targetAltitude / 1000), results.minAltitude);

    double errorMargin = ORBIT_MARGIN_RATIO * targetAltitude;
    Assertions.assertTrue(
        Math.abs(results.maxAltitude - targetAltitude) <= errorMargin,
        () ->
            String.format(
                "Max coast altitude %.0f m not within %.0f m of target %.0f m",
                results.maxAltitude, errorMargin, targetAltitude));
    Assertions.assertTrue(
        Math.abs(results.minAltitude - targetAltitude) <= errorMargin,
        () ->
            String.format(
                "Min coast altitude %.0f m not within %.0f m of target %.0f m",
                results.minAltitude, errorMargin, targetAltitude));
  }
}
