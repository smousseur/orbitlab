package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.List;
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
