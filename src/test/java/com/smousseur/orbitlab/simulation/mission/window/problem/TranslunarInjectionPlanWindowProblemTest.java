package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LunarTransferMission;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowCandidate;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * MIS-2 — the translunar problem wired onto the real ephemeris.
 *
 * <p><b>No date and no cost is pinned</b>, deliberately: an assertion on "the 9th" only proves the
 * ephemeris still says what it said yesterday. What is asserted is what the search is <em>for</em>
 * — that the criterion has relief to search, and that the epoch it returns is one the mission can
 * actually fly.
 */
class TranslunarInjectionPlanWindowProblemTest {
  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  /**
   * Built on call and not held in a static field: {@link TimeScalesFactory} needs the Orekit data
   * loaded, and a static initialiser runs before {@link #init()}.
   */
  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("The screening criterion has relief over a month: there is something to search")
  void theScreeningCriterionVariesOverALunarMonth() {
    // evaluate() only, so this stays closed-form and costs milliseconds.
    TranslunarInjectionPlanWindowProblem problem =
        new TranslunarInjectionPlanWindowProblem(new LunarTransferMission("Translunar transfer"));
    AbsoluteDate epoch = epoch();
    double cheapest = Double.POSITIVE_INFINITY;
    double dearest = Double.NEGATIVE_INFINITY;

    for (int sample = 0; sample * 6 <= 30 * 24; sample++) {
      LaunchWindowCandidate candidate = problem.evaluate(epoch.shiftedBy(sample * 6 * 3_600.0));
      assertTrue(
          candidate.feasible(),
          "the 30° parking inclination clears the lunar declination all month, got a refusal at "
              + candidate.epoch()
              + ": "
              + candidate.refusal());
      cheapest = Math.min(cheapest, candidate.deltaV());
      dearest = Math.max(dearest, candidate.deltaV());
    }

    // Measured at 3 182.8 -> 3 196.9 m/s over January 2026; asserting a spread rather than a
    // figure keeps the test from recording one particular month.
    assertTrue(
        dearest - cheapest > 5.0,
        "a flat criterion would make the search pointless, spread is " + (dearest - cheapest));
  }

  @Test
  @DisplayName("The date the search returns is one the mission's own injection stage accepts")
  void theReturnedWindowIsConfirmedByTheMission() {
    // This one flies: confirm() runs the real aim solve, some thirty four-day propagations per
    // bracketed minimum. Seconds, not milliseconds.
    LunarTransferMission mission = new LunarTransferMission("Translunar transfer");
    TranslunarInjectionPlanWindowProblem problem =
        new TranslunarInjectionPlanWindowProblem(mission);
    AbsoluteDate epoch = epoch();
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            epoch,
            Duration.ofDays(30),
            problem.coarseStep(),
            Duration.ofMinutes(10),
            4_000.0,
            5.0,
            1);

    List<LaunchWindow> windows = new LaunchWindowSolver(problem).solve(search);

    assertFalse(windows.isEmpty(), "a lunar month holds at least one flyable encounter geometry");
    LaunchWindow window = windows.getFirst();
    LaunchWindowCandidate best = window.best();
    // A window is only returned once confirm() has flown it, so this asserts the two-tier contract
    // rather than the arithmetic: an epoch whose aimed perilune is out of reach is withdrawn.
    assertTrue(best.feasible(), "the offered epoch must carry a finite confirmed cost");
    assertTrue(
        best.deltaV() > 2_500.0 && best.deltaV() < 3_600.0,
        "a translunar injection from a 185 km parking orbit costs about 3.1 km/s, got "
            + best.deltaV());
    assertTrue(
        window.opening().compareTo(best.epoch()) <= 0
            && window.closing().compareTo(best.epoch()) >= 0,
        "the optimum must sit inside its own slot");
    // The 5 m/s margin is what makes this a slot at all: against the 4 000 m/s budget alone the
    // whole month is affordable and the edges are the search bounds. Measured at 12.9 days out of
    // 30 on January 2026 — wide, because 14 m/s of relief is a ranking more than a window.
    assertTrue(
        window.duration().toSeconds() > 0
            && window.duration().toSeconds() < (long) search.spanSeconds(),
        "the margin must cut a slot strictly inside the searched range, got " + window.duration());
  }
}
