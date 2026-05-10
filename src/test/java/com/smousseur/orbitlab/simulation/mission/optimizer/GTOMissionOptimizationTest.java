package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.GTOMission;
import com.smousseur.orbitlab.simulation.mission.Mission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GTOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(GTOMissionOptimizationTest.class);
  public static final int GTO_ALTITUDE = 400_000; // 35_786_000;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testGTOMission() {
    Mission GTOMission = new GTOMission("GTO mission", 300_000, GTO_ALTITUDE);
    testMission(GTOMission, 300_000, GTO_ALTITUDE);
  }
}
