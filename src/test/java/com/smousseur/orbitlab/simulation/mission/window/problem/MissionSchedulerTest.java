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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Creating a lunar mission from Kourou on 2026-10-06T08:00Z, the case L0 measured: due east leaves
 * the Moon at arrival 6.9° off the parking plane there, so the date is found at a free azimuth or
 * not at all.
 *
 * <p><b>Behind {@code orbitlab.slowTests}</b>: each creation flies the ascent the window measures
 * the parking orbit on, some fifty seconds for a 2 000 kg orbiter and a hundred for a thirty-tonne
 * probe, then confirms its candidates by flying the aim.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class MissionSchedulerTest {
  private static final Logger logger = LogManager.getLogger(MissionSchedulerTest.class);

  private static final double KOUROU_LATITUDE = 5.236;
  private static final double KOUROU_LONGITUDE = -52.775;
  private static final double KOUROU_ALTITUDE = 0.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  private static AbsoluteDate requested() {
    return new AbsoluteDate("2026-10-06T08:00:00.000Z", TimeScalesFactory.getUTC());
  }

  /**
   * The mission is scheduled on the requested date at a free azimuth, and its spec — budget
   * included — is rebuilt on the plane the window chose. The values carry a plane left over from an
   * earlier date, as an edit would: it must be chosen again, not kept.
   *
   * <p>The confirmation flies the mass the ascent measured due east, less what the azimuth's
   * surcharge burns: a few tens of kilograms, read here on the free-azimuth problem the planner
   * builds from the same ascent.
   */
  @Test
  void kourouIsScheduledOnTheRequestedDateAtAFreeAzimuth() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Kourou lunar orbit");
    values.put("LAUNCH_SITE_LAT", KOUROU_LATITUDE);
    values.put("LAUNCH_SITE_LONG", KOUROU_LONGITUDE);
    values.put("LAUNCH_SITE_ALT", KOUROU_ALTITUDE);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    values.put("LUNAR_PLANE_INCLINATION", 40.0);
    values.put("LUNAR_PLANE_BRANCH", "ASCENDING");
    AbsoluteDate requested = requested();

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

    MissionSpec.LunarOrbit dueEast = (MissionSpec.LunarOrbit) unplanned;
    ParkingInsertion insertion =
        ParkingInsertion.fly(
            dueEast.configuration(),
            dueEast.atmosphere(),
            KOUROU_LATITUDE,
            KOUROU_LONGITUDE,
            KOUROU_ALTITUDE,
            dueEast.parkingAltitude(),
            requested);
    double confirmedMass =
        new LunarLaunchWindowProblem(
                KOUROU_LATITUDE,
                KOUROU_LONGITUDE,
                KOUROU_ALTITUDE,
                dueEast.parkingAltitude(),
                dueEast.orbitAltitude(),
                dueEast.configuration().toVehicleStack(),
                insertion,
                LunarLaunchWindowProblem.PlaneChoice.FREE_AZIMUTH)
            .injectionAt(schedule.date())
            .state()
            .getMass();
    double surchargePropellant = insertion.mass() - confirmedMass;
    logger.info(
        "Kourou free azimuth: {} kg measured in parking due east, {} kg confirmed",
        insertion.mass(),
        confirmedMass);
    assertTrue(
        surchargePropellant > 0.0 && surchargePropellant < 50.0,
        () -> "the surcharge burns " + surchargePropellant + " kg of the measured mass");
  }

  /**
   * A thirty-tonne probe is refused at creation, on the propellant its ascent leaves it: the budget
   * assumes the upper stage reaches parking still holding the injection's propellant, and the flown
   * ascent leaves it some 61 000 kg in parking, about 2 000 m/s of the 3 100 the injection needs.
   */
  @Test
  void aThirtyTonneProbeIsRefusedAtCreation() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Kourou heavy probe");
    values.put("LAUNCH_SITE_LAT", KOUROU_LATITUDE);
    values.put("LAUNCH_SITE_LONG", KOUROU_LONGITUDE);
    values.put("LAUNCH_SITE_ALT", KOUROU_ALTITUDE);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_PROBE");
    values.put("PAYLOAD_MASS", 30_000.0);
    values.put("LUNAR_PERILUNE_ALT", 100.0);
    MissionEntry entry =
        new MissionEntry(MissionScheduler.unplannedSpec(values, MissionType.LUNAR_FLYBY));

    MissionScheduler.Schedule schedule = MissionScheduler.schedule(entry, values, requested());

    logger.info("Thirty-tonne probe: {}", schedule.refusal());
    assertTrue(schedule.refused(), "a thirty-tonne probe cannot be sent to the Moon");
    assertEquals(requested(), schedule.date(), "a refused mission keeps the requested date");
    assertTrue(
        schedule.refusal().contains("neither due east nor at a free azimuth"),
        () -> "the reason says both searches came back empty: " + schedule.refusal());
    assertTrue(
        schedule.refusal().contains("does not carry the propellant"),
        () -> "the reason carries the confirmation's own refusal: " + schedule.refusal());
  }
}
