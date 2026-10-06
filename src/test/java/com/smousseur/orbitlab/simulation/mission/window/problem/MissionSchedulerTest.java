package com.smousseur.orbitlab.simulation.mission.window.problem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Creating a lunar mission where due east has no window: the case L0 measured, Kourou on
 * 2026-10-06T08:00Z, where due east leaves the Moon at arrival 6.9° off the parking plane.
 *
 * <p><b>It confirms</b>, flying the aim on each free-azimuth candidate — some twenty seconds.
 */
class MissionSchedulerTest {

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  /**
   * The mission is scheduled on the requested date at a free azimuth, and its spec — budget
   * included — is rebuilt on the plane the window chose. The values carry a plane left over from an
   * earlier date, as an edit would: it must be chosen again, not kept.
   */
  @Test
  void kourouIsScheduledOnTheRequestedDateAtAFreeAzimuth() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Kourou lunar orbit");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.775);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    values.put("LUNAR_PLANE_INCLINATION", 40.0);
    values.put("LUNAR_PLANE_BRANCH", "ASCENDING");
    AbsoluteDate requested =
        new AbsoluteDate("2026-10-06T08:00:00.000Z", TimeScalesFactory.getUTC());

    MissionSpec unplanned = MissionScheduler.unplannedSpec(values, MissionType.LUNAR_ORBIT);
    assertFalse(((MissionSpec.LunarOrbit) unplanned).hasPlane(), "a plane is chosen, not kept");
    MissionEntry entry = new MissionEntry(unplanned);

    MissionScheduler.Schedule schedule = MissionScheduler.schedule(entry, values, requested);

    assertFalse(schedule.refused(), schedule::refusal);
    assertEquals(requested, schedule.date(), "the first sample of the search is the optimum");
    MissionSpec.LunarOrbit spec = (MissionSpec.LunarOrbit) entry.spec().orElseThrow();
    assertTrue(spec.hasPlane());
    LaunchPlane plane = spec.plane();
    assertEquals(NodeBranch.DESCENDING, plane.nodeBranch());
    assertEquals(9.0, plane.targetInclinationDeg(), 0.6);

    Map<String, Object> planed = new HashMap<>(values);
    planed.put("LUNAR_PLANE_INCLINATION", plane.targetInclinationDeg());
    planed.put("LUNAR_PLANE_BRANCH", plane.nodeBranch().name());
    assertArrayEquals(
        MissionFactory.specFromWizardValues(planed, MissionType.LUNAR_ORBIT)
            .configuration()
            .propellantLoads(),
        spec.configuration().propellantLoads(),
        0.0,
        "the budget is the one sized on the chosen plane");
  }
}
