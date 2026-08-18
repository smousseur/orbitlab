package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslunarInjectionPlanWindowProblemTest {
  private static final double SPACECRAFT_MASS = 1700;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void testWindowSolver() {
    AbsoluteDate EPOCH = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    TranslunarInjectionPlanWindowProblem problem =
        new TranslunarInjectionPlanWindowProblem(SPACECRAFT_MASS);
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            EPOCH, Duration.ofDays(30), Duration.ofHours(6), Duration.ofMinutes(1), 4000, 1);
    LaunchWindowSolver solver = new LaunchWindowSolver(problem);
    List<LaunchWindow> windows = solver.solve(search);
    assertEquals(1, windows.size());
    LaunchWindowCandidate best = windows.getFirst().best();
    assertTrue(best.deltaV() < 4000.0, "the best window is the one with the lowest delta-v");
    assertEquals(9, best.epoch().getComponents(0).getDate().getDay());
  }
}
