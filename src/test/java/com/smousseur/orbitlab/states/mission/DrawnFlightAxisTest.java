package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * The axis an object is drawn along: the air's near the ground, the inertial velocity in space, a
 * smoothed blend between 100 and 120 km.
 *
 * <p>Earth states are built in the terrestrial frame and carried into GCRF by Orekit, so the
 * velocity they hold relative to the air is known by construction: zero for a point at rest on the
 * ground, straight up for a vertical climb.
 */
class DrawnFlightAxisTest {

  private static final double EARTH_RADIUS_M = 6_378_137.0;
  private static final double TOLERANCE = 1e-9;

  /** Kourou's latitude and longitude, near enough to matter: off the equator and off Greenwich. */
  private static final double LATITUDE = FastMath.toRadians(5.23);

  private static final double LONGITUDE = FastMath.toRadians(-52.77);

  private static AbsoluteDate epoch;
  private static Frame itrf;
  private static Frame gcrf;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    itrf = OrekitService.get().itrf();
    gcrf = FramesFactory.getGCRF();
  }

  @Test
  void onThePadTheAxisIsTheRadialVertical() {
    MissionEphemerisPoint pad = earthPoint(0.0, 0.0, 0.0);

    assertParallel(pad.position(), DrawnFlightAxis.of(pad));
  }

  @Test
  void aVerticalClimbBelowTheRampIsDrawnVertical() {
    MissionEphemerisPoint climbing = earthPoint(50_000.0, 50.0, 0.0);

    assertParallel(climbing.position(), DrawnFlightAxis.of(climbing));
  }

  @Test
  void aboveTheRampTheAxisIsTheInertialVelocity() {
    MissionEphemerisPoint orbiting = earthPoint(400_000.0, 0.0, 7_670.0);

    assertParallel(orbiting.velocity(), DrawnFlightAxis.of(orbiting));
  }

  /**
   * A vertical climb inside the ramp: its air-relative velocity is the radial, so the drawn axis is
   * the normalized blend of the radial and the inertial direction, weighted by the smoothstep of
   * the altitude — 1/2 at 110 km, and 3·(1/4)² − 2·(1/4)³ = 5/32 at 105 km.
   */
  @Test
  void insideTheRampTheAxisBlendsAirAndInertialBySmoothstep() {
    assertEquals(0.0, blendError(110_000.0, 0.5), TOLERANCE, "110 km");
    assertEquals(0.0, blendError(105_000.0, 5.0 / 32.0), TOLERANCE, "105 km");
  }

  @Test
  void onALunarArcTheAxisIsTheInertialVelocity() {
    TrajectoryArc moon = new TrajectoryArc(SolarSystemBody.MOON, gcrf);
    Vector3D velocity = new Vector3D(300.0, 1_600.0, -50.0);
    MissionEphemerisPoint point =
        new MissionEphemerisPoint(
            epoch,
            new Vector3D(1_787_000.0, 0.0, 0.0),
            velocity,
            "Insertion",
            true,
            1.0,
            50_000.0,
            moon);

    assertParallel(velocity, DrawnFlightAxis.of(point));
  }

  /** How far the drawn axis lies from the blend of radial and inertial at {@code weight}. */
  private static double blendError(double altitude, double weight) {
    MissionEphemerisPoint point = earthPoint(altitude, 500.0, 0.0);
    Vector3D radial = point.position().normalize();
    Vector3D inertial = point.velocity().normalize();
    Vector3D expected = new Vector3D(1.0 - weight, radial, weight, inertial).normalize();
    return Vector3D.angle(expected, DrawnFlightAxis.of(point));
  }

  /**
   * A state over Kourou at {@code altitude}, moving in the terrestrial frame at {@code climbRate}
   * straight up and {@code eastward} along the local east, carried into GCRF.
   */
  private static MissionEphemerisPoint earthPoint(
      double altitude, double climbRate, double eastward) {
    Vector3D up =
        new Vector3D(
            FastMath.cos(LATITUDE) * FastMath.cos(LONGITUDE),
            FastMath.cos(LATITUDE) * FastMath.sin(LONGITUDE),
            FastMath.sin(LATITUDE));
    Vector3D east = new Vector3D(-FastMath.sin(LONGITUDE), FastMath.cos(LONGITUDE), 0.0);
    PVCoordinates terrestrial =
        new PVCoordinates(
            up.scalarMultiply(EARTH_RADIUS_M + altitude),
            new Vector3D(climbRate, up, eastward, east));
    PVCoordinates celestial = itrf.getTransformTo(gcrf, epoch).transformPVCoordinates(terrestrial);
    return new MissionEphemerisPoint(
        epoch,
        celestial.getPosition(),
        celestial.getVelocity(),
        "Flight",
        true,
        1.0,
        altitude,
        TrajectoryArc.earth());
  }

  private static void assertParallel(Vector3D expected, Vector3D actual) {
    assertEquals(1.0, actual.getNorm(), TOLERANCE, "unit axis");
    assertEquals(0.0, Vector3D.angle(expected, actual), TOLERANCE, "direction");
  }
}
