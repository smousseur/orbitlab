package com.smousseur.orbitlab.simulation.orbit;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

class OrbitSnapshotTest {

  private static final AbsoluteDate CENTER = AbsoluteDate.J2000_EPOCH;
  private static final Vector3D[] TWO_POSITIONS = {Vector3D.ZERO, Vector3D.PLUS_I};

  @Test
  void validConstruction_succeeds() {
    OrbitSnapshot snap =
        new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, TWO_POSITIONS, 0);
    assertEquals(SolarSystemBody.EARTH, snap.body());
    assertEquals(CENTER, snap.centerDate());
    assertEquals(86400.0, snap.periodSeconds(), 1e-12);
    assertEquals(100.0, snap.stepSeconds(), 1e-12);
    assertEquals(0, snap.version());
  }

  @Test
  void nullBody_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new OrbitSnapshot(null, CENTER, 86400.0, 100.0, TWO_POSITIONS, 0));
  }

  @Test
  void nullCenterDate_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, null, 86400.0, 100.0, TWO_POSITIONS, 0));
  }

  @Test
  void nullPositions_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, null, 0));
  }

  @Test
  void zeroPeriodSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 0.0, 100.0, TWO_POSITIONS, 0));
  }

  @Test
  void negativePeriodSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, -1.0, 100.0, TWO_POSITIONS, 0));
  }

  @Test
  void nanPeriodSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, Double.NaN, 100.0, TWO_POSITIONS, 0));
  }

  @Test
  void zeroStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, 0.0, TWO_POSITIONS, 0));
  }

  @Test
  void negativeStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, -1.0, TWO_POSITIONS, 0));
  }

  @Test
  void nanStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitSnapshot(
                SolarSystemBody.EARTH, CENTER, 86400.0, Double.NaN, TWO_POSITIONS, 0));
  }

  @Test
  void singlePosition_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitSnapshot(
                SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, new Vector3D[] {Vector3D.ZERO}, 0));
  }

  @Test
  void emptyPositions_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, new Vector3D[0], 0));
  }

  @Test
  void negativeVersion_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, TWO_POSITIONS, -1));
  }

  @Test
  void zeroVersion_isValid() {
    assertDoesNotThrow(
        () -> new OrbitSnapshot(SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, TWO_POSITIONS, 0));
  }

  @Test
  void largeVersion_isValid() {
    assertDoesNotThrow(
        () ->
            new OrbitSnapshot(
                SolarSystemBody.EARTH, CENTER, 86400.0, 100.0, TWO_POSITIONS, Long.MAX_VALUE));
  }
}
