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

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(doubles = {185_000, 400_000})
  void testLEOMission(double targetAltitude) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    Mission mission = new LEOMission("LEO mission", targetAltitude);
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    MissionOptimizer optimizer = new MissionOptimizer(mission, 40_000);
    MissionComputeResult computeResult = optimizer.optimize();
    MissionEphemeris ephemeris = computeResult.ephemeris();

    CoastAltitudeResults results = extractCoastAltitudes(ephemeris, "Coasting");

    logger.info(
        "[{}km] Max coast altitude: {} m",
        (int) (targetAltitude / 1000),
        results.maxCoastAltitude);
    logger.info(
        "[{}km] Min coast altitude: {} m",
        (int) (targetAltitude / 1000),
        results.minCoastAltitude);

    double errorMargin = ORBIT_MARGIN_RATIO * targetAltitude;
    Assertions.assertTrue(
        Math.abs(results.maxCoastAltitude - targetAltitude) <= errorMargin,
        () ->
            String.format(
                "Max coast altitude %.0f m not within %.0f m of target %.0f m",
                results.maxCoastAltitude, errorMargin, targetAltitude));
    Assertions.assertTrue(
        Math.abs(results.minCoastAltitude - targetAltitude) <= errorMargin,
        () ->
            String.format(
                "Min coast altitude %.0f m not within %.0f m of target %.0f m",
                results.minCoastAltitude, errorMargin, targetAltitude));
  }
}
