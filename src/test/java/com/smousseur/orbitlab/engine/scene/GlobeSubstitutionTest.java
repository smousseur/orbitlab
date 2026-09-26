package com.smousseur.orbitlab.engine.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.mesh.EllipsoidGlobe;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/** The Earth asset's globe mesh is swapped for the generated WGS84 grid, and nothing else. */
class GlobeSubstitutionTest {

  /**
   * One manager for the class: it caches each asset and hands out clones — independent node and
   * geometry objects, so a substitution only touches its own copy — and the 8192 × 4096 maps, over
   * 100 MB of direct memory each, are read once instead of once per test.
   */
  private static final AssetManager ASSETS = new DesktopAssetManager(true);

  @Test
  void theEarthGlobeTakesTheGeneratedMeshAndKeepsItsTexture() {
    Spatial earth = load("earth");
    Geometry globe = geometries(earth).get(0);
    Material material = globe.getMaterial();

    assertTrue(GlobeSubstitution.apply(SolarSystemBody.EARTH, earth));

    assertEquals(65_339, globe.getMesh().getVertexCount());
    assertSame(material, globe.getMaterial());
    Texture texture = AssetFactory.extractDiffuseTexture(globe.getMaterial());
    assertNotNull(texture);
    assertEquals(8192, texture.getImage().getWidth());
    assertEquals(4096, texture.getImage().getHeight());
  }

  /** What is drawn is what the guard measures, and it still matches the committed Earth frame. */
  @Test
  void theSubstitutedEarthRaisesNoGuardDivergence() {
    Spatial earth = load("earth");
    GlobeSubstitution.apply(SolarSystemBody.EARTH, earth);

    assertTrue(MeshGuard.verify(SolarSystemBody.EARTH, earth).isEmpty());
  }

  /**
   * Scaled as {@code loadModel} scales a planet — by its diameter — the generated globe is drawn at
   * the ellipsoid GroundCorrection answers for, not at the asset's node scale: that one carries an
   * export noise of 1.5e-6, which put the drawn surface up to 10 m above a corrected ground point.
   */
  @Test
  void theGeneratedGlobeIsDrawnAtTheEllipsoidItModels() {
    double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    Spatial earth = load("earth");
    GlobeSubstitution.apply(SolarSystemBody.EARTH, earth);
    earth.setLocalScale((float) (2 * (a / 1000.0)));
    earth.updateGeometricState();
    Geometry globe = geometries(earth).get(0);
    EllipsoidGlobe grid = EllipsoidGlobe.earth();

    double[][] places = {{0.0, 0.0}, {0.0, -90.0}, {90.0, 0.0}, {45.0, 45.0}};
    for (double[] place : places) {
      Vector3D local = grid.surface(place[0], place[1]);
      Vector3f drawn =
          globe.localToWorld(
              new Vector3f((float) local.getX(), (float) local.getY(), (float) local.getZ()), null);
      double excessMeters = (drawn.length() - local.getNorm() * a / 1000.0) * 1000.0;
      assertEquals(0.0, excessMeters, 1.0, "at " + place[0] + "/" + place[1]);
    }
  }

  @Test
  void anyOtherBodyIsLeftAsAuthored() {
    Spatial moon = load("moon");
    Mesh before = geometries(moon).get(0).getMesh();

    assertFalse(GlobeSubstitution.apply(SolarSystemBody.MOON, moon));
    assertSame(before, geometries(moon).get(0).getMesh());
  }

  @Test
  void anEarthModelWithoutExactlyOneGeometryIsLeftAsAuthored() {
    Node twoGeometries = new Node("earth");
    Geometry first = new Geometry("a", new Box(1f, 1f, 1f));
    Geometry second = new Geometry("b", new Box(1f, 1f, 1f));
    twoGeometries.attachChild(first);
    twoGeometries.attachChild(second);
    Mesh before = first.getMesh();

    assertFalse(GlobeSubstitution.apply(SolarSystemBody.EARTH, twoGeometries));
    assertSame(before, first.getMesh());
  }

  private static Spatial load(String name) {
    return ASSETS.loadModel("models/planets/" + name + "/" + name + ".gltf");
  }

  private static List<Geometry> geometries(Spatial model) {
    List<Geometry> geometries = new ArrayList<>();
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            geometries.add(geometry);
          }
        });
    return geometries;
  }
}
