package com.smousseur.orbitlab.simulation.mission.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/** The lunar-surface switching function, evaluated from a geocentric frame. */
class LunarImpactDetectorTest {
  private static double lunarRadius;
  private static AbsoluteDate date;
  private static Vector3D moon;

  @BeforeAll
  static void setUp() {
    OrekitService.get().initialize();
    lunarRadius = GravitationalContext.moon().equatorialRadius();
    date = new AbsoluteDate(2026, 10, 10, 0, 0, 0.0, TimeScalesFactory.getUTC());
    moon =
        OrekitService.get()
            .body(SolarSystemBody.MOON)
            .getPosition(date, OrekitService.get().gcrf());
  }

  @Test
  void g_isTheAltitudeAboveTheLunarSphere() {
    LunarImpactDetector detector = new LunarImpactDetector(lunarRadius);
    Vector3D outwards = moon.normalize();

    assertEquals(
        100_000.0,
        detector.g(stateAt(moon.add(outwards.scalarMultiply(lunarRadius + 100_000.0)))),
        1.0e-3);
    assertTrue(detector.g(stateAt(moon.add(outwards.scalarMultiply(1_000_000.0)))) < 0.0);
  }

  /**
   * A trajectory cannot close on the surface faster than it moves, so the surface cannot be reached
   * before {@code g / maxSpeed}: from the Earth the detector is checked that rarely, near the Moon
   * every 10 s.
   */
  @Test
  void closingSpeed_checksAFarSurfaceRarely_andANearOneEveryTenSeconds() {
    LunarImpactDetector detector = new LunarImpactDetector(lunarRadius).withClosingSpeed(15_000.0);
    SpacecraftState nearEarth =
        stateAt(new Vector3D(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0, 0.0, 0.0));
    SpacecraftState nearMoon =
        stateAt(moon.add(moon.normalize().scalarMultiply(lunarRadius + 100_000.0)));

    assertEquals(
        detector.g(nearEarth) / 15_000.0,
        detector.getMaxCheckInterval().currentInterval(nearEarth, true),
        1.0e-9);
    assertEquals(10.0, detector.getMaxCheckInterval().currentInterval(nearMoon, true), 0.0);
  }

  private static SpacecraftState stateAt(Vector3D position) {
    return new SpacecraftState(
        new CartesianOrbit(
            new TimeStampedPVCoordinates(date, position, new Vector3D(0.0, 1_000.0, 0.0)),
            OrekitService.get().gcrf(),
            Constants.WGS84_EARTH_MU));
  }
}
