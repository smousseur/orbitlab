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
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The lunar planner's two searches: due east first, the free azimuth only when due east has
 * nothing, and the reason carried back when neither has anything.
 *
 * <p><b>These confirm</b>, flying the aim on every candidate offered — some twenty seconds a search
 * — because the order of the two searches is decided on confirmed windows, not on screened ones.
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
   * Where due east has a window, it is the one taken, and no plane comes with it: the date is the
   * first window of the Canaveral baseline the production compute was measured on before this lot.
   */
  @Test
  void dueEastKeepsPriorityWhereItHasAWindow() {
    LunarLaunchWindowPlanner.Opportunity opportunity =
        LunarLaunchWindowPlanner.opportunity(
            lunarOrbit(28.562, -80.577, 3.0), utc("2026-10-06T00:00:00.000Z"));

    assertTrue(opportunity.found(), () -> "no window: " + opportunity.refusal());
    assertEquals(utc("2026-10-06T10:02:16.000Z"), opportunity.window().date());
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
   * Neither search finds anything for a probe the launcher cannot send to the Moon: due east from
   * Kourou prices every epoch above the ceiling, and every free-azimuth candidate is refused by the
   * injection's own propellant check. The planner then hands back that refusal, not an empty
   * answer.
   *
   * <p><b>A hundred tonnes, and not thirty</b>: the confirmation flies the budget's mass at
   * injection, which assumes the upper stage reaches the parking orbit holding the injection's
   * propellant. A 30 t probe is confirmed on that assumption; only once the payload outweighs what
   * a <em>full</em> upper stage can push to the Moon does the budget itself fall short.
   */
  @Test
  void noWindowAtAllCarriesTheLastRefusal() {
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
        refusal.contains("neither due east nor at a free azimuth"),
        () -> "the reason says both searches came back empty: " + refusal);
    assertTrue(
        refusal.contains("does not carry the propellant"),
        () -> "the reason carries the confirmation's own refusal: " + refusal);
  }
}
