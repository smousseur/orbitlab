package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * PHY-4 / L6 §7.1 — the geometry of the translunar injection, with no propagation at all.
 *
 * <p>Everything asserted here is closed form: the transfer plane, the parking orbit derived from it,
 * and the Lambert seed. What the flight costs is in {@code LunarTransferFlightTest}; this class runs
 * in milliseconds and is what says <em>why</em> the flight can work before anything is flown.
 */
class TranslunarInjectionPlanTest {
  private static final Logger logger = LogManager.getLogger(TranslunarInjectionPlanTest.class);

  private static AbsoluteDate epoch;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("The parking plane contains the Moon's direction at arrival, at the declared inclination")
  void parkingPlaneContainsTheMoonAtArrival() {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, 1_000.0);
    AbsoluteDate arrival = epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    Vector3D moonDirection = moonPosition(arrival).normalize();

    Vector3D normal =
        Vector3D.crossProduct(parking.getPosition(), parking.getPVCoordinates().getVelocity())
            .normalize();

    // The plane contains the arrival direction: that is the whole reason no launch window is needed,
    // and it is what keeps the transfer in-plane and the delta-v at its Hohmann value.
    assertEquals(
        0.0,
        normal.dotProduct(moonDirection),
        1.0e-12,
        "the parking plane must contain the Moon's direction at arrival");

    // And it is inclined at the declared value, measured off the flown state rather than read back
    // off the constant that built it.
    double inclination = Vector3D.angle(normal, Vector3D.PLUS_K);
    assertEquals(
        TranslunarInjectionPlan.PARKING_INCLINATION,
        inclination,
        1.0e-9,
        "the parking inclination must be the declared one");
  }

  @Test
  @DisplayName("The injection point sits the declared transfer angle short of the arrival direction")
  void injectionPointSitsTheTransferAngleShortOfTheMoon() {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, 1_000.0);
    AbsoluteDate arrival = epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);

    double travelled = Vector3D.angle(parking.getPosition(), moonPosition(arrival));
    assertEquals(
        TranslunarInjectionPlan.TRANSFER_ANGLE,
        travelled,
        1.0e-9,
        "the transfer must span the declared angle");

    // Clear of the half-revolution degeneracy, which is the reason the angle is not 180 degrees.
    assertTrue(
        FastMath.PI - travelled > FastMath.toRadians(1.0),
        "the transfer must stay clear of the 180 degree Lambert singularity");

    // The parking orbit is circular at the declared altitude, prograde in its plane.
    double radius =
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + TranslunarInjectionPlan.PARKING_ALTITUDE;
    assertEquals(radius, parking.getPosition().getNorm(), 1.0);
    assertEquals(
        FastMath.sqrt(Constants.WGS84_EARTH_MU / radius),
        parking.getPVCoordinates().getVelocity().getNorm(),
        1.0e-6);
    assertEquals(
        0.0,
        parking.getPosition().dotProduct(parking.getPVCoordinates().getVelocity()) / (radius * 7_800.0),
        1.0e-12,
        "a circular orbit has no radial velocity");
  }

  @Test
  @DisplayName("The Lambert seed costs what vis-viva says a near-Hohmann translunar injection costs")
  void lambertSeedCostsTheViscVivaDeltaV() {
    SpacecraftState parking = TranslunarInjectionPlan.parkingState(epoch, 1_000.0);
    AbsoluteDate arrival = epoch.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);

    // Aim at a hundred-kilometre perilune, the target the flight test flies.
    double offset = 1_737_400.0 + 100_000.0;
    Vector3D seed =
        TranslunarInjectionPlan.keplerianSeedVelocity(
            parking,
            TranslunarInjectionPlan.boundaryConditions(
                parking, arrival, TranslunarInjectionPlan.aimPointFor(parking, arrival, offset)));
    double deltaV = seed.subtract(parking.getPVCoordinates().getVelocity()).getNorm();

    // The reference is derived here and not recorded from a previous run: vis-viva on an ellipse from
    // the parking radius to the Moon's distance. Asserting against the implementation's own output
    // would only prove it reproduces yesterday (the discipline L4 §7.2 set).
    double mu = Constants.WGS84_EARTH_MU;
    double rp = parking.getPosition().getNorm();
    double ra = moonPosition(arrival).getNorm();
    double semiMajorAxis = 0.5 * (rp + ra);
    double expected =
        FastMath.sqrt(mu * (2.0 / rp - 1.0 / semiMajorAxis)) - FastMath.sqrt(mu / rp);

    logger.info(
        "Lambert seed delta-v {} m/s against the vis-viva reference {} m/s", deltaV, expected);

    // The band covers the transfer being 170 degrees rather than 180 and four days rather than the
    // Hohmann 5.02, both of which cost a little more than the reference ellipse.
    assertEquals(expected, deltaV, 150.0);
  }

  @Test
  @DisplayName("A lunar declination beyond the parking inclination is refused, not approximated")
  void aDeclinationBeyondTheInclinationThrows() {
    // Fabricated, because the real Moon never gets there: measured [-28.40, +28.32] degrees over 400
    // days from 2026-03-01, and 28.6 degrees is the ceiling of the whole 18.6-year cycle. The guard
    // exists BECAUSE the margin to 30 degrees is 1.4 degrees, not because the case is reachable.
    double declination = FastMath.toRadians(35.0);
    Vector3D beyond =
        new Vector3D(FastMath.cos(declination), 0.0, FastMath.sin(declination));

    OrbitlabException thrown =
        assertThrows(
            OrbitlabException.class, () -> TranslunarInjectionPlan.transferPlaneNormal(beyond));
    assertTrue(thrown.getMessage().contains("declination"));

    // And just inside the inclination it resolves, so the guard is a boundary and not a blanket.
    double inside = TranslunarInjectionPlan.PARKING_INCLINATION - FastMath.toRadians(0.5);
    Vector3D within = new Vector3D(FastMath.cos(inside), 0.0, FastMath.sin(inside));
    Vector3D normal = TranslunarInjectionPlan.transferPlaneNormal(within);
    assertEquals(0.0, normal.dotProduct(within), 1.0e-12);
    assertEquals(
        TranslunarInjectionPlan.PARKING_INCLINATION,
        Vector3D.angle(normal, Vector3D.PLUS_K),
        1.0e-9);
  }

  @Test
  @DisplayName("The geometry resolves at every epoch of a lunar month, with no window search")
  void theGeometryResolvesAtEveryEpoch() {
    // The property decision 4 of the design buys: the parking orbit is built to fit the Moon, so
    // there is nothing to wait for. Twenty-eight daily epochs cover a full declination cycle.
    for (int day = 0; day < 28; day++) {
      AbsoluteDate date = epoch.shiftedBy(day * 86_400.0);
      SpacecraftState parking = TranslunarInjectionPlan.parkingState(date, 1_000.0);
      Vector3D normal =
          Vector3D.crossProduct(parking.getPosition(), parking.getPVCoordinates().getVelocity())
              .normalize();
      Vector3D moonDirection =
          moonPosition(date.shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS)).normalize();
      assertEquals(
          0.0, normal.dotProduct(moonDirection), 1.0e-12, "day " + day + " left the plane behind");
    }
  }

  private static Vector3D moonPosition(AbsoluteDate date) {
    Frame gcrf = OrekitService.get().gcrf();
    return OrekitService.get().body(SolarSystemBody.MOON).getPosition(date, gcrf);
  }
}
