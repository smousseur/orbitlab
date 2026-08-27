package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * MIS-4 / L2 §5.2 — the one flown case of the lot: a launch window that is <b>dated and
 * confirmed</b>, from Cape Canaveral, on the plane the pad actually reaches.
 *
 * <p><b>What it adds to the closed tests next door.</b> Those measure the shape of the screening
 * criterion; nothing in them ever flies. Here the solver's second tier is on, so the epochs it
 * offers have been through the perilune bisection and the depletion floor of the active stage — the
 * two verdicts that decide whether a date is a plan or a wish. The 1 700 kg vehicle leaves some 577
 * kg after a 3 178 m/s injection at Isp 300, against a 500 kg floor: the confirmation is a real
 * test of that floor rather than a formality it passes with room to spare.
 *
 * <p><b>The screen-to-confirmation gap is the closing measurement of the lot.</b> The solver
 * anchors its acceptance margin on the screening tier on both sides, which is only sound while the
 * two tiers differ by less than that margin; the translunar problem next door was measured at 6-8
 * m/s, and this is the same measurement taken on a suffered plane. It is logged, not asserted:
 * pinning it would pin a number this lot exists to find out.
 *
 * <p><b>Contrainte de méthode</b> (découpage §3): this case flies some thirty four-day propagations
 * per confirmed epoch, so it costs some fifteen seconds, and it is the user who runs it.
 */
class LunarLaunchWindowFlightTest {
  private static final Logger logger = LogManager.getLogger(LunarLaunchWindowFlightTest.class);

  private static final double CANAVERAL_LATITUDE = 28.562;
  private static final double CANAVERAL_LONGITUDE = -80.577;
  private static final double CANAVERAL_ALTITUDE = 3.0;

  private static final double TARGET_PERILUNE = 100_000.0;

  /**
   * The band the flown perilune must land in (m). The bisection of {@link
   * TranslunarInjectionPlan#solve} stops at 1 km, so this is the order the demo flight is pinned at
   * rather than a looser one bought for a suffered plane.
   */
  private static final double PERILUNE_BAND = 10_000.0;

  /** Mass at injection (kg) — the figure L0 and L1 measured their tables at. */
  private static final double INJECTION_MASS = 1_700.0;

  /** The budget the search accepts an epoch under (m/s), a little above the 3 178 m/s baseline. */
  private static final double MAX_DELTA_V = 3_400.0;

  /** The margin that carves the slot out of the criterion (m/s) — the Earth problem's own. */
  private static final double MARGIN = 50.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  @DisplayName("A launch window from Canaveral is dated, confirmed, and flies its perilune")
  void aWindowFromCanaveralIsDatedAndConfirmed() {
    LunarLaunchWindowProblem problem =
        new LunarLaunchWindowProblem(
            CANAVERAL_LATITUDE,
            CANAVERAL_LONGITUDE,
            CANAVERAL_ALTITUDE,
            TranslunarInjectionPlan.PARKING_ALTITUDE,
            TARGET_PERILUNE,
            new Spacecraft(500, 1200, 1200, PropulsionSystem.getSpacecraftPropulsion()),
            INJECTION_MASS);
    AbsoluteDate start = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());

    long startedAt = System.nanoTime();
    List<LaunchWindow> windows =
        new LaunchWindowSolver(problem)
            .solve(
                new LaunchWindowSearch(
                    start,
                    Duration.ofHours(26),
                    problem.coarseStep(),
                    problem.refinementPrecision(),
                    MAX_DELTA_V,
                    MARGIN,
                    5));
    double wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;

    assertFalse(windows.isEmpty(), "twenty-six hours must hold at least one lunar window");
    LaunchWindow window =
        windows.stream().min(Comparator.comparingDouble(a -> a.best().deltaV())).orElseThrow();

    // The same epoch flown again, to read what the confirmation could not return: the perilune the
    // aim converged to. The solver's own confirmation gives back a cost and a verdict only.
    LunarLaunchWindowProblem.Injection injection = problem.injectionAt(window.date());
    double exhaustVelocity =
        PropulsionSystem.getSpacecraftPropulsion().isp() * Constants.G0_STANDARD_GRAVITY;
    TranslunarInjectionPlan plan =
        TranslunarInjectionPlan.solve(
            injection.state(),
            TARGET_PERILUNE,
            exhaustVelocity,
            new FlightContext(
                GravitationalContext.earth()
                    .withPerturbers(SolarSystemBody.MOON, SolarSystemBody.SUN)));
    SpacecraftState injected = plan.applyTo(injection.state(), exhaustVelocity);

    double screened = problem.evaluate(window.date()).deltaV();

    logger.info(
        "Lunar window from Canaveral: launch at {}, slot {} — {} ({} min wide), {} window(s) over"
            + " 26 h, solved in {} s",
        window.date(),
        window.opening(),
        window.closing(),
        String.format(Locale.ROOT, "%.1f", window.duration().toSeconds() / 60.0),
        windows.size(),
        String.format(Locale.ROOT, "%.1f", wallSeconds));
    logger.info(
        "at the optimum: β = {}°, screened {} m/s, confirmed {} m/s (gap {} m/s against the 6-8 m/s"
            + " of the translunar problem), flown perilune {} km, {} kg left against a 500 kg floor",
        String.format(Locale.ROOT, "%.3f", FastMath.toDegrees(injection.planeMisalignment())),
        String.format(Locale.ROOT, "%.1f", screened),
        String.format(Locale.ROOT, "%.1f", window.best().deltaV()),
        String.format(Locale.ROOT, "%.1f", window.best().deltaV() - screened),
        String.format(Locale.ROOT, "%.1f", plan.perileneAltitude() / 1000.0),
        String.format(Locale.ROOT, "%.0f", injected.getMass()));

    assertTrue(window.best().feasible(), "a window's optimum must be a flyable epoch");
    assertTrue(
        window.best().deltaV() <= MAX_DELTA_V,
        "a confirmed epoch must stay inside the budget it was searched under, got "
            + Math.round(window.best().deltaV()));
    assertTrue(
        !window.date().isBefore(window.opening()) && !window.date().isAfter(window.closing()),
        "the optimum must lie inside the slot the solver cut around it");
    assertEquals(
        TARGET_PERILUNE,
        plan.perileneAltitude(),
        PERILUNE_BAND,
        "the confirmed date must fly the perilune it was scheduled for");
  }
}
