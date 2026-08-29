package com.smousseur.orbitlab.ui.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The target orbit as the user asked for it (spec {@code docs/mission-detail/01-vue-detail.md}
 * section 4.1).
 */
class MissionTargetOrbitTest {

  private static final double GEO_ALT = 35_786_000.0;
  private static final double PARKING_ALT = 400_000.0;
  private static final double SITE_LAT = 5.23;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static LaunchConfiguration falconHeavy() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  @Test
  void leoTargetIsTheRequestedEllipseAtTheSiteLatitude() {
    MissionSpec.EarthOrbit spec =
        MissionSpec.EarthOrbit.dueEast(
            "LEO", falconHeavy(), 400_000.0, 600_000.0, "Kourou", SITE_LAT, -52.77, 0.0, null);

    MissionTargetOrbit target = MissionTargetOrbit.of(spec).orElseThrow();

    assertEquals(400_000.0, target.perigeeAltitude(), 1e-6);
    assertEquals(600_000.0, target.apogeeAltitude(), 1e-6);
    assertEquals(FastMath.toRadians(SITE_LAT), target.inclination(), 1e-12);
  }

  @Test
  void geoTargetIsTheCircularGeoOrbitAndNotTheObjectivesGto() {
    MissionSpec.Geo spec =
        new MissionSpec.Geo(
            "GEO", falconHeavy(), PARKING_ALT, GEO_ALT, 0.0, "Kourou", SITE_LAT, -52.77, 0.0, null);

    MissionTargetOrbit target = MissionTargetOrbit.of(spec).orElseThrow();

    // GEOMission records its objective as (parking, GEO, i = launch latitude). Reading the target
    // from there would report a ~35 000 km perigee miss and a 5.23 deg inclination miss on a
    // perfectly successful mission.
    assertEquals(
        GEO_ALT, target.perigeeAltitude(), 1e-6, "perigee must be GEO, not the parking orbit");
    assertEquals(GEO_ALT, target.apogeeAltitude(), 1e-6);
    assertEquals(0.0, target.inclination(), 1e-12, "inclination must be the requested final one");
  }

  @Test
  void geoTargetUsesTheRequestedFinalInclinationNotTheSiteLatitude() {
    MissionSpec.Geo spec =
        new MissionSpec.Geo(
            "GEO inclined",
            falconHeavy(),
            PARKING_ALT,
            GEO_ALT,
            3.0,
            "Kourou",
            SITE_LAT,
            -52.77,
            0.0,
            null);

    assertEquals(
        FastMath.toRadians(3.0), MissionTargetOrbit.of(spec).orElseThrow().inclination(), 1e-12);
  }

  /**
   * MIS-4 / L4 §6.1. A flyby aims at no orbit, so it resolves to nothing rather than to a
   * degenerate triple: the detail view drops its TARGET line and the panel footer drops its miss,
   * exactly as they already do for a legacy entry — no new UI case.
   */
  @Test
  void lunarFlybyHasNoDisplayableTargetOrbit() {
    MissionSpec.Lunar spec =
        new MissionSpec.Lunar(
            "Lunar flyby",
            falconHeavy(),
            PARKING_ALT,
            100_000.0,
            "Kourou",
            SITE_LAT,
            -52.77,
            0.0,
            null,
            null);

    assertTrue(
        MissionTargetOrbit.of(spec).isEmpty(),
        "a flyby has no (perigee, apogee, inclination) to display");
  }

  @Test
  void legacyEntryWithoutSpecHasNoDisplayableTarget() {
    MissionEntry legacy =
        new MissionEntry(new EarthOrbitMission("legacy", falconHeavy(), 400_000.0));
    assertTrue(MissionTargetOrbit.forEntry(legacy).isEmpty());
  }

  /**
   * MIS-5 / L5 §3.2 — a lunar orbit shows no target, and the reason is not the flyby's.
   *
   * <p>A flyby has no target orbit at all; this one has one, and since L2 the achieved orbit is
   * reported against the arc body, so the altitudes would be comparable. What cannot be shown is
   * the pair: {@code formatMiss} prints the altitude miss and the inclination miss in one string,
   * and this mission aims at no inclination. L7 brings the card and the reader.
   */
  @Test
  void lunarOrbitTargetIsAbsent() {
    MissionSpec.LunarOrbit spec =
        new MissionSpec.LunarOrbit(
            "Lunar orbit",
            LaunchConfiguration.fullyLoaded(
                Launchers.FALCON_HEAVY, Payloads.LUNAR_ORBITER.toSpacecraft(2_000.0, 657.7)),
            PARKING_ALT,
            100_000.0,
            "Cape Canaveral",
            28.562,
            -80.577,
            3.0,
            null,
            null);

    assertTrue(MissionTargetOrbit.of(spec).isEmpty());
  }
}
