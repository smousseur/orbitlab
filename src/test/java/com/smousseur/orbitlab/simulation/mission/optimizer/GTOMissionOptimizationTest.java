package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.GTOMission;
import com.smousseur.orbitlab.simulation.mission.Mission;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GTOMissionOptimizationTest extends AbstractTrajectoryOptimizerTest {
  public static final int GTO_ALTITUDE = 35_786_000;
  public static final int PARKING_ALTITUDE = 300_000;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testGTOMission() {
    Mission GTOMission = new GTOMission("GTO mission", PARKING_ALTITUDE, GTO_ALTITUDE);
    testMission(GTOMission, PARKING_ALTITUDE, GTO_ALTITUDE);
  }
}
