package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

class MeshConformanceTest {

  private static final Vector3f REFERENCE_POLE = Vector3f.UNIT_Z;
  private static final Vector3f REFERENCE_PRIME_MERIDIAN = Vector3f.UNIT_X.negate();

  @Test
  void acceptsTheReferenceFrameItself() {
    MeshFrame frame = frame(REFERENCE_POLE, REFERENCE_PRIME_MERIDIAN, 0f, -360f);

    assertInstanceOf(MeshConformance.Conforming.class, MeshConformance.of(frame));
  }

  /**
   * Jupiter's own case: pole in the reference family, but the texture's column 0 a quarter turn
   * away. The assertion is on the property, not on the representation — the reported rotation,
   * applied to the measured frame, must produce the reference frame — so an equivalent angle/axis
   * pair is accepted.
   */
  @Test
  void reportsTheRotationThatBringsAQuarterTurnedMeridianBack() {
    MeshFrame frame = frame(REFERENCE_POLE, new Vector3f(0f, -1f, 0f), 0f, -360f);

    MeshConformance.NeedsRotation verdict =
        assertInstanceOf(MeshConformance.NeedsRotation.class, MeshConformance.of(frame));

    Quaternion correction =
        new Quaternion()
            .fromAngleAxis(verdict.angleDeg() * com.jme3.math.FastMath.DEG_TO_RAD, verdict.axis());
    assertEquals(90.0, verdict.angleDeg(), 0.1, "shortest turn");
    assertAligned(REFERENCE_POLE, correction.mult(frame.pole()), "corrected pole");
    assertAligned(
        REFERENCE_PRIME_MERIDIAN,
        correction.mult(frame.primeMeridian()),
        "corrected primeMeridian");
  }

  @Test
  void refusesAMapThatIsNotEquirectangular() {
    MeshFrame frame = frame(REFERENCE_POLE, REFERENCE_PRIME_MERIDIAN, 49.3f, -360f);

    assertInstanceOf(MeshConformance.NotALatLongMap.class, MeshConformance.of(frame));
  }

  @Test
  void refusesAMirroredMapBecauseNoRotationCanFixIt() {
    MeshFrame frame = frame(REFERENCE_POLE, REFERENCE_PRIME_MERIDIAN, 0f, 360f);

    assertInstanceOf(MeshConformance.Mirrored.class, MeshConformance.of(frame));
  }

  private static MeshFrame frame(
      Vector3f pole, Vector3f primeMeridian, float residualDeg, float azimuthDegreesPerU) {
    return new MeshFrame(pole.clone(), primeMeridian.clone(), residualDeg, azimuthDegreesPerU);
  }

  private static void assertAligned(Vector3f expected, Vector3f actual, String what) {
    assertEquals(1.0, expected.dot(actual.normalize()), 1e-4, what + " = " + actual);
  }
}
