package com.smousseur.orbitlab.engine.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.MeshConformance;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

class PlanetMeshCorrectionTest {

  private static AbsoluteDate date;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    date = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

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
   * Checked a year apart as well, since L4 made the correction a function of the date: a reference
   * body shows its own solid surface and must not acquire a drift.
   */
  @Test
  void theReferenceBodiesKeepTheIdentity() {
    assertIdentity(SolarSystemBody.EARTH);
    assertIdentity(SolarSystemBody.MOON);
  }

  /**
   * The hole the alignment checks cannot see, and which the old form of {@link
   * #assertCorrectionConforms} left open: they neutralise λ0 on purpose, so a chain that dropped a
   * measured λ0 entirely would still pass every one of them. The invariant here is what {@code
   * correctionFor} promises — the longitude term is a turn about the reference pole, of exactly the
   * offset between the body's λ0 and the conventional origin.
   */
  @Test
  void aMeasuredLambda0TurnsThePrimeMeridianByItsOwnOffset() {
    int checked = 0;
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (PlanetMeshCorrection.calibrationFor(body).isEmpty()) {
        continue;
      }
      PlanetMeshCalibration calibration = PlanetMeshCorrection.calibrationFor(body).orElseThrow();
      double turnDeg =
          Math.IEEEremainder(
              PlanetMeshCorrection.CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG
                  - calibration.lambda0Deg(),
              360.0);
      if (Math.abs(turnDeg) < 1e-3) {
        continue;
      }
      Vector3f meridian = calibration.measured().primeMeridian();
      Vector3f aligned =
          PlanetMeshCorrection.correctionFor(
                  calibration, PlanetMeshCorrection.CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG)
              .mult(meridian)
              .normalize();
      Vector3f turned =
          PlanetMeshCorrection.correctionFor(calibration, calibration.lambda0Deg())
              .mult(meridian)
              .normalize();
      double angleDeg = Math.toDegrees(Math.acos(Math.clamp(aligned.dot(turned), -1.0f, 1.0f)));
      assertEquals(Math.abs(turnDeg), angleDeg, 1e-2, body + " must turn by its own λ0 offset");
      checked++;
    }
    assertTrue(checked > 0, "no body carries a measured λ0 any more; this test has gone vacuous");
  }

  private static void assertCorrectionConforms(SolarSystemBody body) {
    PlanetMeshCalibration calibration = PlanetMeshCorrection.calibrationFor(body).orElseThrow();
    MeshFrame measured = calibration.measured();
    // The alignment alone: λ0 is a longitude offset and is meant to turn the frame away from the
    // reference, so a correction carrying one has nothing to say about whether the axis is right.
    // Neutralising it means passing the conventional origin, which is the value correctionFor turns
    // by zero — not the body's own λ0, which only neutralised it back when every body still carried
    // the house value.
    Quaternion correction =
        PlanetMeshCorrection.correctionFor(
            calibration, PlanetMeshCorrection.CONVENTIONAL_COLUMN_ZERO_LONGITUDE_DEG);

    assertAligned(MeshConformance.REFERENCE_POLE, correction.mult(measured.pole()), body + " pole");
    assertAligned(
        MeshConformance.REFERENCE_PRIME_MERIDIAN,
        correction.mult(measured.primeMeridian()),
        body + " primeMeridian");
  }

  private static void assertIdentity(SolarSystemBody body) {
    for (AbsoluteDate at : new AbsoluteDate[] {date, date.shiftedBy(365 * 86400.0)}) {
      Quaternion correction = PlanetMeshCorrection.correctionFor(body, at);
      assertEquals(
          0.0, correction.toAngleAxis(new Vector3f()), 1e-4, body + " must keep the identity");
    }
  }

  private static void assertAligned(Vector3f expected, Vector3f actual, String what) {
    assertEquals(1.0, expected.dot(actual.normalize()), 1e-2, what + " = " + actual);
  }
}
