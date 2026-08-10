package com.smousseur.orbitlab.ui.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The target orbit as the user asked for it (spec {@code specs/mission-detail/01-vue-detail.md}
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
    MissionSpec.Leo spec =
        new MissionSpec.Leo(
            "LEO", falconHeavy(), 400_000.0, 600_000.0, "Kourou", SITE_LAT, -52.77, 0.0, null);

    MissionTargetOrbit target = MissionTargetOrbit.of(spec);

    assertEquals(400_000.0, target.perigeeAltitude(), 1e-6);
    assertEquals(600_000.0, target.apogeeAltitude(), 1e-6);
    assertEquals(FastMath.toRadians(SITE_LAT), target.inclination(), 1e-12);
  }

  @Test
  void geoTargetIsTheCircularGeoOrbitAndNotTheObjectivesGto() {
    MissionSpec.Geo spec =
        new MissionSpec.Geo(
            "GEO", falconHeavy(), PARKING_ALT, GEO_ALT, 0.0, "Kourou", SITE_LAT, -52.77, 0.0, null);

    MissionTargetOrbit target = MissionTargetOrbit.of(spec);

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

    assertEquals(FastMath.toRadians(3.0), MissionTargetOrbit.of(spec).inclination(), 1e-12);
  }

  @Test
  void legacyEntryWithoutSpecHasNoDisplayableTarget() {
    MissionEntry legacy = new MissionEntry(new LEOMission("legacy", falconHeavy(), 400_000.0));
    assertTrue(MissionTargetOrbit.forEntry(legacy).isEmpty());
  }
}
