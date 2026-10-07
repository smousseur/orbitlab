package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.operation.LunarOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The lunar planner's two searches: due east first, the free azimuth only when due east has
 * nothing, and the reason carried back when neither has anything.
 *
 * <p><b>The creation's searches run behind {@code orbitlab.slowTests}</b>: they fly the ascent the
 * window measures the parking orbit on, some fifty seconds, then confirm by flying the aim on every
 * candidate offered, because the order of the two searches is decided on confirmed windows, not on
 * screened ones. The timeline's, which screens only, runs in the default suite.
 */
class LunarLaunchWindowPlannerTest {
  private static final Logger logger = LogManager.getLogger(LunarLaunchWindowPlannerTest.class);

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  /** The configuration L0 measured: Falcon Heavy, a 2 000 kg orbiter, a 100 km lunar orbit. */
  private static MissionSpec.LunarOrbit lunarOrbit(double lat, double lon, double alt) {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "planner");
    values.put("LAUNCH_SITE_LAT", lat);
    values.put("LAUNCH_SITE_LONG", lon);
    values.put("LAUNCH_SITE_ALT", alt);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    return (MissionSpec.LunarOrbit)
        MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
  }

  private static AbsoluteDate utc(String iso) {
    return new AbsoluteDate(iso, TimeScalesFactory.getUTC());
  }

  /**
   * Where due east has a window, it is the one taken, and no plane comes with it: the first window
   * of the Canaveral baseline, dated on the insertion the ascent flown at the requested date
   * reaches — 80 s past the 10:02:16 the parking orbit posed at the pad gave.
   */
  @Test
  @EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
  void dueEastKeepsPriorityWhereItHasAWindow() {
    LunarLaunchWindowPlanner.Opportunity opportunity =
        LunarLaunchWindowPlanner.opportunity(
            lunarOrbit(28.562, -80.577, 3.0), utc("2026-10-06T00:00:00.000Z"));

    assertTrue(opportunity.found(), () -> "no window: " + opportunity.refusal());
    assertEquals(utc("2026-10-06T10:03:36.000Z"), opportunity.window().date());
    assertFalse(opportunity.hasPlane(), "due east carries no plane");
    assertNull(opportunity.refusal());
  }

  /**
   * The wizard's timeline falls back the way the creation does: from Kourou, where due east prices
   * every epoch of the day at twice a translunar injection, it offers the free-azimuth
   * opportunities, priced at what they will cost — under the ceiling the creation's search applies.
   */
  @Test
  void theTimelineOffersTheFreeAzimuthWhereDueEastHasNothing() {
    List<LaunchWindow> windows =
        LaunchWindowPlanner.nextOpportunities(
            new LunarLaunchWindowRequest(
                5.236, -52.775, 0.0, LunarOrbitMission.DEFAULT_PARKING_ALTITUDE, 100_000.0),
            utc("2026-10-06T08:00:00.000Z"),
            3);

    for (LaunchWindow window : windows) {
      logger.info(
          "Kourou timeline: {} at {} m/s", window.date(), Math.round(window.best().deltaV()));
    }
    assertFalse(windows.isEmpty(), "the timeline offers something to click");
    for (LaunchWindow window : windows) {
      assertTrue(
          window.best().deltaV() < 3_400.0,
          () -> window.date() + " is offered at " + window.best().deltaV() + " m/s");
    }
  }

  /**
   * A probe the launcher cannot even put in parking: the ascent the window measures the parking
   * orbit on refuses itself, its parking insertion short of the propellant to circularise, and that
   * refusal is the planner's answer — neither search runs.
   */
  @Test
  @EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
  void anAscentThatDoesNotReachParkingIsTheRefusal() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "too heavy");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.775);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_PROBE");
    values.put("PAYLOAD_MASS", 100_000.0);
    values.put("LUNAR_PERILUNE_ALT", 100.0);
    MissionSpec.Lunar spec =
        (MissionSpec.Lunar) MissionFactory.specFromWizardValues(values, MissionType.LUNAR_FLYBY);

    LunarLaunchWindowPlanner.Opportunity opportunity =
        LunarLaunchWindowPlanner.opportunity(spec, utc("2026-10-06T08:00:00.000Z"));

    assertFalse(opportunity.found());
    assertFalse(opportunity.hasPlane());
    String refusal = opportunity.refusal();
    assertTrue(
        refusal.contains("[Parking]") && refusal.contains("400 km circular orbit"),
        () -> "the reason is the parking insertion's own refusal: " + refusal);
  }

  /**
   * An ascent the force model cannot fly is a refusal too, not an exception thrown at the wizard:
   * from Canaveral on 2026-10-09T09:57:11 the NRLMSISE00 density turns infinite during the parking
   * insertion, measured on the production compute and on the ascent alone.
   */
  @Test
  @EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
  void anAscentTheForceModelCannotFlyIsTheRefusal() {
    LunarLaunchWindowPlanner.Opportunity opportunity =
        LunarLaunchWindowPlanner.opportunity(
            lunarOrbit(28.562, -80.577, 3.0), utc("2026-10-09T09:57:11.000Z"));

    assertFalse(opportunity.found());
    String refusal = opportunity.refusal();
    assertTrue(
        refusal.contains("parking orbit") && refusal.contains("NRLMSISE00"),
        () -> "the reason names the ascent and what stopped it: " + refusal);
  }
}
