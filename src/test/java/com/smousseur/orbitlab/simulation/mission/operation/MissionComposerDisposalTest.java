package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
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
 * The flyable-disposal guard pinned. A disposal reserve only ever rides on a mission that can burn
 * it: an Earth-orbit mission, within the direct chain's reach, in the reentry regime, on a payload
 * that drops its own upper stage. Anything else is refused at composition rather than carried
 * silently to an engine that never fires it.
 */
class MissionComposerDisposalTest {

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double ALT = 0.0;
  private static final OptimizationType FAST = OptimizationType.FAST;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static LaunchConfiguration falconHeavy(Spacecraft payload) {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, payload);
  }

  @Test
  void earthObsWithReserve_on400kmLeo_composesWithATail() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 77, 500);
    MissionSpec.EarthOrbit spec =
        MissionSpec.EarthOrbit.dueEast(
            "LEO", falconHeavy(payload), 400_000.0, 400_000.0, "Kourou", LAT, LON, ALT, null);

    assertTrue(spec.deorbits());
    Mission mission = MissionComposer.compose(spec, FAST);
    assertTrue(mission.hasDisposalTail());
  }

  @Test
  void earthObsWithoutReserve_on400kmLeo_composesWithNoTail() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 77, 0);
    MissionSpec.EarthOrbit spec =
        MissionSpec.EarthOrbit.dueEast(
            "LEO", falconHeavy(payload), 400_000.0, 400_000.0, "Kourou", LAT, LON, ALT, null);

    assertFalse(spec.deorbits());
    Mission mission = MissionComposer.compose(spec, FAST);
    assertFalse(mission.hasDisposalTail());
  }

  @Test
  void geoSatWithReserveAndNoNominalLoad_on400kmLeo_isRefused() {
    Spacecraft payload = Payloads.GEO_SAT.toSpacecraft(2_000, 0, 100);
    MissionSpec.EarthOrbit spec =
        MissionSpec.EarthOrbit.dueEast(
            "LEO", falconHeavy(payload), 400_000.0, 400_000.0, "Kourou", LAT, LON, ALT, null);

    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> MissionComposer.compose(spec, FAST));
    assertTrue(
        failure.getMessage().toLowerCase(Locale.ROOT).contains("upper stage"),
        () -> "the message must name the upper stage staying attached: " + failure.getMessage());
  }

  @Test
  void earthObsWithReserve_on20200kmTarget_isRefusedForTheParkingChain() {
    // Ariane 64's upper stage holds the transfer coast on its own, so composeHighOrbit would
    // otherwise accept this target: the refusal below can only come from the disposal guard.
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 77, 500);
    LaunchConfiguration configuration =
        LaunchConfiguration.fullyLoaded(Launchers.ARIANE_64, payload);
    MissionSpec.EarthOrbit spec =
        MissionSpec.EarthOrbit.dueEast(
            "MEO", configuration, 20_200_000.0, 20_200_000.0, "Kourou", LAT, LON, ALT, null);

    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> MissionComposer.compose(spec, FAST));
    String message = failure.getMessage().toLowerCase(Locale.ROOT);
    assertTrue(
        message.contains("disposal reserve"),
        () -> "the message must name the disposal reserve: " + failure.getMessage());
    assertTrue(
        message.contains("parking"),
        () -> "the message must name the parking chain: " + failure.getMessage());
  }

  @Test
  void geoSatWithReserve_onAGeoSpec_isRefusedForNotBeingAnEarthOrbit() {
    Spacecraft payload = Payloads.GEO_SAT.toSpacecraft(2_000, 0, 100);
    MissionSpec.Geo spec =
        new MissionSpec.Geo(
            "GEO",
            falconHeavy(payload),
            400_000.0,
            GEOMission.GEO_ALTITUDE,
            0.0,
            "Kourou",
            LAT,
            LON,
            ALT,
            null);

    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> MissionComposer.compose(spec, FAST));
    assertTrue(
        failure.getMessage().toLowerCase(Locale.ROOT).contains("only a leo mission"),
        () -> "the message must name the LEO-only rule: " + failure.getMessage());
  }
}
