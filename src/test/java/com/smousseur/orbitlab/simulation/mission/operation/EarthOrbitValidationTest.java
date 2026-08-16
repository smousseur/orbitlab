package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Test;

/**
 * <b>MIS-7 / P1, test T6</b> — every feasibility rule of spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §8 refuses at spec construction, with a message
 * naming what is reachable.
 *
 * <p><b>Refusals, not clamps.</b> A parameter silently corrected produces a mission that flies
 * something other than what was asked for — precisely the defect MIS-7 exists to remove. The spec is
 * the last place the caller still knows what it wanted, so it is where the refusal belongs; by the
 * time the propagation notices, it is running on a background thread with no idea what was intended.
 *
 * <p>{@link LaunchPlaneTest} covers the same rules one level down, on the plane itself. This fixture
 * checks that {@link MissionSpec.EarthOrbit} actually enforces them rather than merely storing an
 * inclination.
 */
class EarthOrbitValidationTest {

  private static final double KOUROU_LAT = 5.23;
  private static final double KOUROU_LON = -52.77;

  private static LaunchConfiguration falconHeavy() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static MissionSpec.EarthOrbit spec(
      double perigee, double apogee, double inclinationDeg, double latitude) {
    return new MissionSpec.EarthOrbit(
        "T6",
        falconHeavy(),
        perigee,
        apogee,
        FastMath.toRadians(inclinationDeg),
        NodeBranch.ASCENDING,
        "Kourou",
        latitude,
        KOUROU_LON,
        0.0,
        null);
  }

  @Test
  void inclinationBelowTheSiteLatitude_isRefused() {
    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class, () -> spec(400_000.0, 400_000.0, 0.0, KOUROU_LAT));

    assertTrue(
        failure.getMessage().contains("5.230"),
        () -> "the message must name the reachable minimum: " + failure.getMessage());
  }

  @Test
  void inclinationAboveTheRetrogradeBound_isRefused() {
    assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, 179.0, KOUROU_LAT));
  }

  @Test
  void inclinationOutsideZeroToOneEighty_isRefused() {
    assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, 200.0, KOUROU_LAT));
    assertThrows(OrbitlabException.class, () -> spec(400_000.0, 400_000.0, -5.0, KOUROU_LAT));
  }

  /** Implicit before MIS-7 — the pair was simply used in the order it came — now explicit. */
  @Test
  void apogeeBelowPerigee_isRefusedNamingBoth() {
    OrbitlabException failure =
        assertThrows(
            OrbitlabException.class, () -> spec(600_000.0, 400_000.0, KOUROU_LAT, KOUROU_LAT));

    assertTrue(
        failure.getMessage().contains("400000") && failure.getMessage().contains("600000"),
        () -> "the message must name both altitudes: " + failure.getMessage());
  }

  // ── What must keep passing ───────────────────────────────────────────────

  @Test
  void theSitesFreePlane_isAccepted() {
    assertDoesNotThrow(() -> spec(400_000.0, 400_000.0, KOUROU_LAT, KOUROU_LAT));
  }

  @Test
  void polarAndSunSynchronousTargets_areAccepted() {
    assertDoesNotThrow(() -> spec(400_000.0, 400_000.0, 90.0, KOUROU_LAT));
    assertDoesNotThrow(() -> spec(700_000.0, 700_000.0, 98.19, KOUROU_LAT));
  }

  @Test
  void anEllipticTarget_isAccepted() {
    assertDoesNotThrow(() -> spec(400_000.0, 600_000.0, KOUROU_LAT, KOUROU_LAT));
  }

  /** The historical factory must never trip its own validation, whatever the site. */
  @Test
  void theDueEastFactory_isAlwaysReachable() {
    for (double latitude : new double[] {0.0, 5.23, 28.5, -34.6, 67.9}) {
      double lat = latitude;
      assertDoesNotThrow(
          () ->
              MissionSpec.EarthOrbit.dueEast(
                  "T6", falconHeavy(), 400_000.0, 400_000.0, "site", lat, KOUROU_LON, 0.0, null),
          () -> "the free plane of latitude " + lat + "° must be reachable from it");
    }
  }
}
