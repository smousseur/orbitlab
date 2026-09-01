package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.MeshGuard;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The Earth and the Moon are the reference assets: their frame is what the export convention says
 * (see {@code docs/orientation-planetes/01-decoupage.md} §4.2), and they are declared frozen while
 * the other nine models are provisional. They are therefore the only fixture in this chantier that
 * a test can lean on without being rewritten at every asset swap.
 *
 * <p>The Moon carries the longitude half of the reference on its own: being in synchronous
 * rotation, a correct near side cannot coexist with a shifted prime meridian.
 */
class PlanetMeshFrameFixtureTest {

  @Test
  void theEarthAssetCarriesTheReferenceFrame() {
    assertReferenceFrame(loadPlanet("earth"));
  }

  @Test
  void theMoonAssetCarriesTheReferenceFrame() {
    assertReferenceFrame(loadPlanet("moon"));
  }

  /**
   * The closure of L1, and the only check that the report was transcribed into
   * {@code PlanetMeshCorrection} without a slip: for every calibrated body, the asset on disk still
   * carries what was committed for it. A digit wrong in a direction, a texture size off, and this
   * goes red.
   *
   * <p>It is also the guard itself, run against every asset at once — so a silent asset swap is
   * caught here as well as at startup.
   */
  @Test
  void everyCommittedCalibrationStillMatchesItsAsset() {
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (PlanetMeshCorrection.calibrationFor(body).isEmpty()) {
        continue;
      }
      Spatial model = loadPlanet(body.displayName().toLowerCase(Locale.ROOT));
      assertTrue(
          MeshGuard.verify(body, model).isEmpty(),
          () -> body + " diverges from its committed calibration: " + MeshGuard.verify(body, model));
    }
  }

  private static void assertReferenceFrame(Spatial planet) {
    List<ProbedGeometry> probed = MeshFrameProbe.probe(planet);

    assertEquals(1, probed.size(), "expected a single spherical geometry, got " + probed);
    MeshFrame frame = probed.get(0).frame();
    assertEquals(0.0, frame.equirectangularResidualDeg(), 0.1, "residual");
    assertEquals(1.0, Vector3f.UNIT_Z.dot(frame.pole()), 1e-3, "pole = " + frame.pole());
    assertEquals(
        1.0,
        Vector3f.UNIT_X.negate().dot(frame.primeMeridian()),
        1e-3,
        "primeMeridian = " + frame.primeMeridian());
    assertEquals(-360.0, frame.azimuthDegreesPerU(), 1.0, "chirality");
  }

  private static Spatial loadPlanet(String name) {
    AssetManager assetManager = new DesktopAssetManager(true);
    return assetManager.loadModel("models/planets/" + name + "/" + name + ".gltf");
  }
}
