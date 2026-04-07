package com.smousseur.orbitlab.simulation.orbit;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

class OrbitPathTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;
  private static final AbsoluteDate T1 = T0.shiftedBy(3600.0);

  @Test
  void validConstruction_succeeds() {
    List<Vector3D> positions = List.of(Vector3D.ZERO, Vector3D.PLUS_I);
    OrbitPath path = new OrbitPath(SolarSystemBody.EARTH, T0, T1, 100.0, positions);
    assertEquals(SolarSystemBody.EARTH, path.body());
    assertEquals(T0, path.start());
    assertEquals(T1, path.end());
    assertEquals(100.0, path.stepSeconds(), 1e-12);
    assertSame(positions, path.positionsHelioMeters());
  }

  @Test
  void nullBody_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new OrbitPath(null, T0, T1, 100.0, List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }

  @Test
  void nullStart_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new OrbitPath(
                SolarSystemBody.EARTH, null, T1, 100.0, List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }

  @Test
  void nullEnd_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new OrbitPath(
                SolarSystemBody.EARTH, T0, null, 100.0, List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }

  @Test
  void nullPositions_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new OrbitPath(SolarSystemBody.EARTH, T0, T1, 100.0, null));
  }

  @Test
  void zeroStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitPath(
                SolarSystemBody.EARTH, T0, T1, 0.0, List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }

  @Test
  void negativeStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitPath(
                SolarSystemBody.EARTH, T0, T1, -1.0, List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }

  @Test
  void nanStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitPath(
                SolarSystemBody.EARTH,
                T0,
                T1,
                Double.NaN,
                List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }

  @Test
  void infiniteStepSeconds_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrbitPath(
                SolarSystemBody.EARTH,
                T0,
                T1,
                Double.POSITIVE_INFINITY,
                List.of(Vector3D.ZERO, Vector3D.PLUS_I)));
  }
}
