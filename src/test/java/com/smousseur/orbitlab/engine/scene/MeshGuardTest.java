package com.smousseur.orbitlab.engine.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import org.junit.jupiter.api.Test;

class MeshGuardTest {

  private static final SolarSystemBody BODY = SolarSystemBody.JUPITER;

  @Test
  void staysSilentWhileTheAssetStillMatchesWhatWasCommitted() {
    PlanetMeshCalibration committed = committed();

    assertTrue(
        MeshGuard.check(
                BODY, committed.measured(), committed.textureWidth(), committed.textureHeight())
            .isEmpty());
  }

  @Test
  void reportsTheAngleWhenTheMeshHasTurned() {
    PlanetMeshCalibration committed = committed();
    Quaternion turn = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X);
    MeshFrame turned =
        new MeshFrame(
            turn.mult(committed.measured().pole()),
            turn.mult(committed.measured().primeMeridian()),
            committed.measured().equirectangularResidualDeg(),
            committed.measured().azimuthDegreesPerU());

    MeshDivergence divergence =
        MeshGuard.check(BODY, turned, committed.textureWidth(), committed.textureHeight())
            .orElseThrow();

    assertEquals(90.0, divergence.frameDeviationDeg(), 0.1);
    assertFalse(divergence.textureChanged());
  }

  /**
   * The case that justifies checking the texture at all: a re-export that keeps the mesh and swaps
   * the texture leaves the frame identical, and λ0 silently wrong. The mesh check alone stays
   * quiet.
   */
  @Test
  void reportsAChangedTextureEvenWhenTheMeshIsUntouched() {
    PlanetMeshCalibration committed = committed();

    MeshDivergence divergence =
        MeshGuard.check(BODY, committed.measured(), 2048, 1024).orElseThrow();

    assertTrue(divergence.textureChanged());
    assertEquals(0.0, divergence.frameDeviationDeg(), 0.1);
  }

  /**
   * Uranus carries nothing measurable, so there is nothing to compare and nothing to shout about.
   */
  @Test
  void staysSilentForABodyWithNoCommittedCalibration() {
    MeshFrame anything =
        new MeshFrame(new Vector3f(0f, 1f, 0f), new Vector3f(1f, 0f, 0f), 49.4f, -547.7f);

    assertTrue(MeshGuard.check(SolarSystemBody.URANUS, anything, 2048, 2048).isEmpty());
  }

  private static PlanetMeshCalibration committed() {
    return PlanetMeshCorrection.calibrationFor(BODY).orElseThrow();
  }
}
