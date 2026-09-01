package com.smousseur.orbitlab.engine.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.MeshConformance;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import org.junit.jupiter.api.Test;

class PlanetMeshCorrectionTest {

  /**
   * The defect that opened the chantier: Jupiter's pole is in the reference family but its texture
   * column 0 sits a quarter turn away, and it carried the identity all the same.
   */
  @Test
  void jupiterCorrectionBringsItsMeasuredFrameOntoTheReference() {
    assertCorrectionConforms(SolarSystemBody.JUPITER);
  }

  /**
   * The whole table at once, as an invariant rather than a list of expected values: whatever a body
   * was measured to carry, its correction must land it on the reference. A value copied wrong from
   * the report cannot pass this.
   */
  @Test
  void everyCalibratedBodyCorrectionLandsOnTheReference() {
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (PlanetMeshCorrection.calibrationFor(body).isPresent()) {
        assertCorrectionConforms(body);
      }
    }
  }

  /**
   * The closure of L1. These two are the references and are declared frozen; a chain that produces
   * anything but the identity for them is wrong, and this says so without looking at the screen.
   */
  @Test
  void theReferenceBodiesKeepTheIdentity() {
    assertIdentity(SolarSystemBody.EARTH);
    assertIdentity(SolarSystemBody.MOON);
  }

  private static void assertCorrectionConforms(SolarSystemBody body) {
    PlanetMeshCalibration calibration = PlanetMeshCorrection.calibrationFor(body).orElseThrow();
    MeshFrame measured = calibration.measured();
    Quaternion correction = PlanetMeshCorrection.correctionFor(body);

    assertAligned(MeshConformance.REFERENCE_POLE, correction.mult(measured.pole()), body + " pole");
    assertAligned(
        MeshConformance.REFERENCE_PRIME_MERIDIAN,
        correction.mult(measured.primeMeridian()),
        body + " primeMeridian");
  }

  private static void assertIdentity(SolarSystemBody body) {
    Quaternion correction = PlanetMeshCorrection.correctionFor(body);
    assertEquals(
        0.0, correction.toAngleAxis(new Vector3f()), 1e-4, body + " must keep the identity");
  }

  private static void assertAligned(Vector3f expected, Vector3f actual, String what) {
    assertEquals(1.0, expected.dot(actual.normalize()), 1e-2, what + " = " + actual);
  }
}
