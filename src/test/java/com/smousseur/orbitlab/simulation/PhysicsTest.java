package com.smousseur.orbitlab.simulation;

import static org.junit.jupiter.api.Assertions.*;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

class PhysicsTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // --- computeBurnDuration ---

  @Test
  void computeBurnDuration_zeroDeltaV_returnsZero() {
    assertEquals(0.0, Physics.computeBurnDuration(0, 1000, 300, 8_400_000), 1e-10);
  }

  @Test
  void computeBurnDuration_matchesTsiolkovskyFormula() {
    double isp = 300, mass = 10_000, thrust = 8_400_000, dv = 200;
    double ve = isp * Constants.G0_STANDARD_GRAVITY;
    double expected = (mass * ve / thrust) * (1.0 - FastMath.exp(-dv / ve));
    assertEquals(expected, Physics.computeBurnDuration(dv, mass, isp, thrust), 1e-12);
  }

  @Test
  void computeBurnDuration_largerDeltaV_longerDuration() {
    double dt1 = Physics.computeBurnDuration(100, 10_000, 300, 8_400_000);
    double dt2 = Physics.computeBurnDuration(500, 10_000, 300, 8_400_000);
    assertTrue(dt2 > dt1);
  }

  // --- buildThrustDirectionTNW ---

  @Test
  void buildThrustDirectionTNW_zeroAngles_isPureTangential() {
    Vector3D dir = Physics.buildThrustDirectionTNW(0.0, 0.0);
    assertEquals(1.0, dir.getX(), 1e-12);
    assertEquals(0.0, dir.getY(), 1e-12);
    assertEquals(0.0, dir.getZ(), 1e-12);
  }

  @Test
  void buildThrustDirectionTNW_isUnitVector() {
    assertEquals(1.0, Physics.buildThrustDirectionTNW(0.3, 0.2).getNorm(), 1e-10);
    assertEquals(1.0, Physics.buildThrustDirectionTNW(-0.5, 0.4).getNorm(), 1e-10);
    assertEquals(1.0, Physics.buildThrustDirectionTNW(0.0, FastMath.PI / 4).getNorm(), 1e-10);
  }

  @Test
  void buildThrustDirectionTNW_pureOutOfPlane_returnsPlusW() {
    Vector3D dir = Physics.buildThrustDirectionTNW(0.0, FastMath.PI / 2);
    assertEquals(0.0, dir.getX(), 1e-10);
    assertEquals(0.0, dir.getY(), 1e-10);
    assertEquals(1.0, dir.getZ(), 1e-10);
  }

  // --- getLaunchAzimuth ---

  @Test
  void getLaunchAzimuth_noArgs_returnsPiOver2() {
    assertEquals(FastMath.PI / 2, Physics.getLaunchAzimuth(), 1e-12);
  }

  @Test
  void getLaunchAzimuth_bothZero_returnsPiOver2() {
    assertEquals(FastMath.PI / 2, Physics.getLaunchAzimuth(0, 0), 1e-12);
  }

  @Test
  void getLaunchAzimuth_45degLatSameInclination_returnsPiOver2() {
    // cos(45°) / cos(45°) = 1 → asin(1) = PI/2
    double lat = FastMath.toRadians(45.0);
    double incl = FastMath.toRadians(45.0);
    assertEquals(FastMath.PI / 2, Physics.getLaunchAzimuth(lat, incl), 1e-10);
  }

  @Test
  void getLaunchAzimuth_poleLatitude_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Physics.getLaunchAzimuth(FastMath.PI / 2, FastMath.toRadians(45.0)));
  }

  // --- computeRadialVelocity ---

  @Test
  void computeRadialVelocity_pureRadial_equalsVelocityMagnitude() {
    Frame gcrf = OrekitService.get().gcrf();
    PVCoordinates pv =
        new PVCoordinates(new Vector3D(7_000_000, 0, 0), new Vector3D(1000, 0, 0));
    SpacecraftState state =
        new SpacecraftState(new CartesianOrbit(pv, gcrf, AbsoluteDate.J2000_EPOCH, Constants.WGS84_EARTH_MU));
    assertEquals(1000.0, Physics.computeRadialVelocity(state), 1e-6);
  }

  @Test
  void computeRadialVelocity_pureTangential_returnsZero() {
    Frame gcrf = OrekitService.get().gcrf();
    PVCoordinates pv =
        new PVCoordinates(new Vector3D(7_000_000, 0, 0), new Vector3D(0, 7500, 0));
    SpacecraftState state =
        new SpacecraftState(new CartesianOrbit(pv, gcrf, AbsoluteDate.J2000_EPOCH, Constants.WGS84_EARTH_MU));
    assertEquals(0.0, Physics.computeRadialVelocity(state), 1e-6);
  }

  // --- applyPitchKick ---

  @Test
  void applyPitchKick_zeroAngle_velocityUnchanged() {
    Frame gcrf = OrekitService.get().gcrf();
    PVCoordinates pv =
        new PVCoordinates(new Vector3D(6_600_000, 0, 0), new Vector3D(0, 100, 200));
    SpacecraftState state =
        new SpacecraftState(
                new CartesianOrbit(pv, gcrf, AbsoluteDate.J2000_EPOCH, Constants.WGS84_EARTH_MU))
            .withMass(500_000);

    SpacecraftState result = Physics.applyPitchKick(state, 0.0, FastMath.PI / 2);

    Vector3D origVel = state.getPVCoordinates().getVelocity();
    Vector3D newVel = result.getPVCoordinates().getVelocity();
    assertEquals(origVel.getX(), newVel.getX(), 1e-6);
    assertEquals(origVel.getY(), newVel.getY(), 1e-6);
    assertEquals(origVel.getZ(), newVel.getZ(), 1e-6);
  }

  @Test
  void applyPitchKick_massPreserved() {
    Frame gcrf = OrekitService.get().gcrf();
    PVCoordinates pv =
        new PVCoordinates(new Vector3D(6_600_000, 0, 0), new Vector3D(0, 0, 300));
    SpacecraftState state =
        new SpacecraftState(
                new CartesianOrbit(pv, gcrf, AbsoluteDate.J2000_EPOCH, Constants.WGS84_EARTH_MU))
            .withMass(450_000);

    SpacecraftState result = Physics.applyPitchKick(state, 0.05, FastMath.PI / 2);
    assertEquals(450_000, result.getMass(), 1e-6);
  }

  @Test
  void applyPitchKick_positionUnchanged() {
    Frame gcrf = OrekitService.get().gcrf();
    PVCoordinates pv =
        new PVCoordinates(new Vector3D(6_600_000, 0, 0), new Vector3D(0, 0, 300));
    SpacecraftState state =
        new SpacecraftState(
                new CartesianOrbit(pv, gcrf, AbsoluteDate.J2000_EPOCH, Constants.WGS84_EARTH_MU))
            .withMass(450_000);

    SpacecraftState result = Physics.applyPitchKick(state, 0.1, FastMath.PI / 2);
    Vector3D origPos = state.getPVCoordinates().getPosition();
    Vector3D newPos = result.getPVCoordinates().getPosition();
    assertEquals(origPos.getX(), newPos.getX(), 1.0); // within 1m
    assertEquals(origPos.getY(), newPos.getY(), 1.0);
    assertEquals(origPos.getZ(), newPos.getZ(), 1.0);
  }
}
