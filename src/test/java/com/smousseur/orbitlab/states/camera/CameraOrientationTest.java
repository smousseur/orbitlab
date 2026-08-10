package com.smousseur.orbitlab.states.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Pins the inversion of the turntable model. This is the one place in the transition code where a
 * sign or an axis could be wrong without the reader noticing, so the round trip below is checked
 * against the very expression {@code OrbitCameraAppState.applyCameraPose} builds its offset with,
 * rather than against a restatement of it.
 */
class CameraOrientationTest {

  private static final float TOLERANCE = 1e-5f;

  /** The offset the orbit camera actually applies, composed the way that state composes it. */
  private static Vector3f cameraOffset(CameraOrientation orientation) {
    Quaternion yaw = new Quaternion().fromAngleAxis(orientation.yawRad(), Vector3f.UNIT_Y);
    Quaternion pitch = new Quaternion().fromAngleAxis(orientation.pitchRad(), Vector3f.UNIT_X);
    return yaw.mult(pitch).mult(new Vector3f(0f, 0f, 1f));
  }

  private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
    assertEquals(expected.x, actual.x, TOLERANCE, "x");
    assertEquals(expected.y, actual.y, TOLERANCE, "y");
    assertEquals(expected.z, actual.z, TOLERANCE, "z");
  }

  @Test
  void offsetDirectionMatchesWhatTheOrbitCameraApplies() {
    CameraOrientation[] samples = {
      CameraOrientation.defaults(),
      new CameraOrientation(0f, 0f),
      new CameraOrientation(1.2f, -0.4f),
      new CameraOrientation(-2.7f, 0.9f),
      new CameraOrientation(FastMath.PI, -1.2f)
    };

    for (CameraOrientation orientation : samples) {
      assertVectorEquals(cameraOffset(orientation), orientation.offsetDirection());
    }
  }

  @Test
  void theCameraSitsWhereTheOffsetDirectionAsksItTo() {
    Vector3f[] directions = {
      new Vector3f(0f, 0f, 1f),
      new Vector3f(1f, 0f, 0f),
      new Vector3f(0f, 0f, -1f),
      new Vector3f(-3f, 2f, 4f)
    };

    for (Vector3f direction : directions) {
      CameraOrientation orientation = CameraOrientation.fromOffsetDirection(direction);
      assertVectorEquals(direction.normalize(), orientation.offsetDirection());
    }
  }

  @Test
  void axesLandOnTheExpectedAngles() {
    // Straight down +Z is the identity pose: no yaw, no pitch.
    CameraOrientation z = CameraOrientation.fromOffsetDirection(new Vector3f(0f, 0f, 1f));
    assertEquals(0f, z.yawRad(), TOLERANCE);
    assertEquals(0f, z.pitchRad(), TOLERANCE);

    // A quarter turn puts the camera on +X.
    CameraOrientation x = CameraOrientation.fromOffsetDirection(new Vector3f(1f, 0f, 0f));
    assertEquals(FastMath.HALF_PI, x.yawRad(), TOLERANCE);
    assertEquals(0f, x.pitchRad(), TOLERANCE);

    // Overhead is a negative pitch, per the (−sin pitch) term on the vertical axis.
    CameraOrientation up = CameraOrientation.fromOffsetDirection(new Vector3f(0f, 1f, 0f));
    assertEquals(-FastMath.HALF_PI, up.pitchRad(), TOLERANCE);
  }

  @Test
  void facingAlongPutsTheCameraBehindTheTravel() {
    // Travelling towards +Z, the camera belongs on the −Z side so the destination is ahead of it.
    CameraOrientation orientation = CameraOrientation.facingAlong(new Vector3f(0f, 0f, 10f));
    assertVectorEquals(new Vector3f(0f, 0f, -1f), orientation.offsetDirection());
  }

  @Test
  void defaultsLookDownAtTheirPivot() {
    CameraOrientation defaults = CameraOrientation.defaults();

    assertEquals(0f, defaults.yawRad(), TOLERANCE);
    assertEquals((float) Math.toRadians(-20.0), defaults.pitchRad(), TOLERANCE);
    // Negative pitch puts the camera above the pivot: it looks down on it.
    assertVectorEquals(new Vector3f(0f, 0.342020f, 0.939693f), defaults.offsetDirection());
  }

  @Test
  void aDegenerateDirectionFallsBackToTheDefaults() {
    assertEquals(CameraOrientation.defaults(), CameraOrientation.fromOffsetDirection(null));
    assertEquals(
        CameraOrientation.defaults(), CameraOrientation.fromOffsetDirection(new Vector3f()));
    assertEquals(CameraOrientation.defaults(), CameraOrientation.facingAlong(null));
  }

  @Test
  void wrapToPiFoldsOntoTheShortestSignedAngle() {
    assertEquals(0f, CameraOrientation.wrapToPi(0f), TOLERANCE);
    assertEquals(1f, CameraOrientation.wrapToPi(1f), TOLERANCE);
    assertEquals(-1f, CameraOrientation.wrapToPi(-1f), TOLERANCE);
    assertEquals(0f, CameraOrientation.wrapToPi(FastMath.TWO_PI), TOLERANCE);
    assertEquals(-1f, CameraOrientation.wrapToPi(FastMath.TWO_PI - 1f), TOLERANCE);
    assertEquals(1f, CameraOrientation.wrapToPi(-FastMath.TWO_PI + 1f), TOLERANCE);
    assertEquals(0.5f, CameraOrientation.wrapToPi(6f * FastMath.PI + 0.5f), 1e-4f);
  }

  @Test
  void interpolationRestsOnItsEndpoints() {
    CameraOrientation from = new CameraOrientation(0.2f, -0.3f);
    CameraOrientation to = new CameraOrientation(1.1f, 0.4f);

    assertEquals(from, from.towards(to, 0f));

    CameraOrientation arrived = from.towards(to, 1f);
    assertEquals(to.yawRad(), arrived.yawRad(), TOLERANCE);
    assertEquals(to.pitchRad(), arrived.pitchRad(), TOLERANCE);
  }
}
