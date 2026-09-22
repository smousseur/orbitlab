package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PHY-10 / L2 — the orbital-propulsion invariant. A mission that delivers its payload to a stable
 * orbit (LEO, GEO, LUNAR_ORBIT) cannot fly an inert one: it is refused at composition, so the
 * payload always has an engine to burn its disposal reserve with. A lunar flyby does not stay in
 * orbit and is spared.
 */
class OrbitalPropulsionInvariantTest {

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final OptimizationType FAST = OptimizationType.FAST;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /** A payload with no engine at all — the case the invariant forbids on an orbital mission. */
  private static Spacecraft inert() {
    return new Spacecraft(2_000, 0, 0, null);
  }

  private static LaunchConfiguration falconHeavy(Spacecraft payload) {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, payload);
  }

  @Test
  void aLeoWithAnInertPayload_isRefused() {
    MissionSpec.EarthOrbit leo =
        MissionSpec.EarthOrbit.dueEast(
            "LEO", falconHeavy(inert()), 400_000.0, 400_000.0, "Kourou", LAT, LON, 0.0, null);

    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> MissionComposer.compose(leo, FAST));
    assertTrue(
        failure.getMessage().toLowerCase(Locale.ROOT).contains("propulsion"),
        () -> "the message must name the missing propulsion: " + failure.getMessage());
  }

  @Test
  void aGeoWithAnInertPayload_isRefused() {
    MissionSpec.Geo geo =
        new MissionSpec.Geo(
            "GEO",
            falconHeavy(inert()),
            400_000.0,
            35_786_000.0,
            0.0,
            "Kourou",
            LAT,
            LON,
            0.0,
            null);

    assertThrows(OrbitlabException.class, () -> MissionComposer.compose(geo, FAST));
  }

  @Test
  void aLunarOrbitWithAnInertPayload_isRefused() {
    MissionSpec.LunarOrbit lunarOrbit =
        new MissionSpec.LunarOrbit(
            "LUNAR ORBIT",
            falconHeavy(inert()),
            400_000.0,
            100_000.0,
            "Kourou",
            LAT,
            LON,
            0.0,
            null,
            null);

    assertThrows(OrbitlabException.class, () -> MissionComposer.compose(lunarOrbit, FAST));
  }

  @Test
  void aLunarFlybyWithAnInertProbe_composes() {
    // A flyby does not stay in orbit, so an inert probe is legitimate and must not be refused.
    MissionSpec.Lunar flyby =
        new MissionSpec.Lunar(
            "FLYBY",
            falconHeavy(Payloads.LUNAR_PROBE.toSpacecraft(2_000, 0.0)),
            400_000.0,
            100_000.0,
            "Kourou",
            LAT,
            LON,
            0.0,
            null,
            null);

    assertDoesNotThrow(() -> MissionComposer.compose(flyby, FAST));
  }

  @Test
  void aLeoWithAPropelledPayload_composes() {
    MissionSpec.EarthOrbit leo =
        MissionSpec.EarthOrbit.dueEast(
            "LEO",
            falconHeavy(Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 50.0)),
            400_000.0,
            400_000.0,
            "Kourou",
            LAT,
            LON,
            0.0,
            null);

    assertDoesNotThrow(() -> MissionComposer.compose(leo, FAST));
  }
}
