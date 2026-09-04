package com.smousseur.orbitlab.engine.scene.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrameProbe;
import com.smousseur.orbitlab.engine.scene.mesh.ModelNodes;
import com.smousseur.orbitlab.engine.scene.mesh.ProbedGeometry;
import com.smousseur.orbitlab.engine.scene.mesh.RingAlignment;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code FX-5} hangs off a node name and off a capture that happens one step before the materials
 * are replaced. Both are invisible when they break: a renamed node leaves the ring simply
 * unshadowed, and a material captured too early leaves every write landing on an object the model
 * no longer holds. This ties the committed strings to the files on disk, and the capture to the
 * order the loader really uses.
 *
 * <p>It also keeps watch on what {@code BUG-20} left behind. A shadow is only worth casting on a
 * ring that lies in its globe's equator, and the defect that put Uranus's ring 20.25 deg out of it
 * came from a zero scale component still present in the source {@code .blend}: the next re-export
 * can bring it back, and nothing else in the build would notice.
 */
class RingShadowFixtureTest {

  private static final Vector3f SUN_DIRECTION = new Vector3f(1f, 0f, 0f);

  /** The Sun's angular radius from Saturn, in radians. Nothing here reads it back. */
  private static final float SUN_APPARENT_RADIUS = 4.86e-4f;

  /** Radius written by the eclipse path, chosen to be nothing the ring path could produce. */
  private static final float ECLIPSE_OCCLUDER_RADIUS = 999f;

  @Test
  void everyRingedBodysAssetCarriesTheNodeItsTableNames() {
    for (SolarSystemBody body : ringedBodies()) {
      String prefix = PlanetMeshCorrection.ringNodePrefixFor(body).orElseThrow();
      Spatial model = loadModel(body, new DesktopAssetManager(true));

      List<Geometry> ring = ringGeometriesOf(model, prefix);

      assertTrue(
          !ring.isEmpty(),
          "no node named "
              + prefix
              + " in "
              + modelPath(body)
              + ", which holds "
              + namesOf(ModelNodes.geometriesUnder(model)));
    }
  }

  /**
   * The globe must not be caught by the ring's prefix. It would receive its own centre as an
   * occulter at exactly its own radius, every lit fragment would compute itself eclipsed, and the
   * body would go black while the shadow stayed missing.
   */
  @Test
  void theRingsPrefixDoesNotCatchTheGlobe() {
    for (SolarSystemBody body : ringedBodies()) {
      String prefix = PlanetMeshCorrection.ringNodePrefixFor(body).orElseThrow();
      Spatial model = loadModel(body, new DesktopAssetManager(true));

      List<String> caught = namesOf(ringGeometriesOf(model, prefix));

      assertEquals(
          1, caught.size(), body.displayName() + ": prefix " + prefix + " caught " + caught);
    }
  }

  /** What {@code BUG-20} was about, kept measured so a re-export cannot undo it in silence. */
  @Test
  void theRingLiesInItsOwnGlobesEquator() {
    for (SolarSystemBody body : ringedBodies()) {
      Spatial model = loadModel(body, new DesktopAssetManager(true));
      List<ProbedGeometry> probed = MeshFrameProbe.probe(model);
      ProbedGeometry ring =
          probed.stream()
              .filter(ProbedGeometry::isRing)
              .findFirst()
              .orElseThrow(() -> new AssertionError(body + ": no flat geometry to measure"));

      RingAlignment alignment = RingAlignment.between(ring.ring(), globePole(probed));

      assertTrue(
          alignment.isAligned(),
          body.displayName()
              + ": ring is "
              + alignment.angleDeg()
              + " deg out of its globe's equator, past the "
              + RingAlignment.MAX_TILT_DEG
              + " deg a ring may carry. Re-run './gradlew meshProbe'.");
    }
  }

  /**
   * The whole wiring, in the order the loader really runs it: the ring is captured <em>before</em>
   * the materials are replaced. Capturing materials instead of geometries compiles and wires, and
   * shows up here as a ring whose occulter radius never left the material default.
   */
  @Test
  void lightingTheRingOccultsTheRingAndNothingElse() {
    for (SolarSystemBody body : ringedBodies()) {
      Model3dView view = loadedView(body);
      String prefix = PlanetMeshCorrection.ringNodePrefixFor(body).orElseThrow();
      float drawnRadius = drawnRadiusUnits(body);

      view.setRingSunlight(SUN_DIRECTION, SUN_APPARENT_RADIUS);

      Map<String, Float> radii = occluderRadii(view);
      assertEquals(
          2, radii.size(), body.displayName() + " should hold a globe and a ring: " + radii);
      radii.forEach(
          (name, radius) ->
              assertEquals(
                  name.startsWith(prefix) ? drawnRadius : 0f,
                  radius,
                  drawnRadius * 1e-6f,
                  body.displayName() + ", geometry " + name + ", of " + radii));
    }
  }

  /**
   * The two occulter mechanisms share one set of uniforms per material, and only the exclusion
   * inside {@code Model3dView.setOccluder} keeps an eclipse from overwriting a ring's own planet
   * every frame. No body in this application is both eclipsed and ringed, so nothing but this test
   * exercises the guard.
   */
  @Test
  void anEclipseOccluderDoesNotReachTheRing() {
    SolarSystemBody body = SolarSystemBody.SATURN;
    String prefix = PlanetMeshCorrection.ringNodePrefixFor(body).orElseThrow();
    float drawnRadius = drawnRadiusUnits(body);
    Model3dView view = loadedView(body);
    view.setRingSunlight(SUN_DIRECTION, SUN_APPARENT_RADIUS);

    view.setOccluder(
        new Vector3f(1f, 2f, 3f), ECLIPSE_OCCLUDER_RADIUS, SUN_DIRECTION, SUN_APPARENT_RADIUS);

    Map<String, Float> radii = occluderRadii(view);
    radii.forEach(
        (name, radius) ->
            assertEquals(
                name.startsWith(prefix) ? drawnRadius : ECLIPSE_OCCLUDER_RADIUS,
                radius,
                drawnRadius * 1e-6f,
                "geometry " + name + ", of " + radii));
  }

  /**
   * A view holding a loaded, re-materialised model, in the loader's own order: capture the ring
   * first ({@code PlanetPoseAppState.isolateRing}), replace the materials second ({@code
   * AssetFactory.applyLambert}), attach last.
   */
  private static Model3dView loadedView(SolarSystemBody body) {
    AssetManager assetManager = new DesktopAssetManager(true);
    Spatial model = loadModel(body, assetManager);
    BodyRenderConfig config =
        new BodyRenderConfig(
            body.name(),
            body.displayName(),
            ColorRGBA.White,
            PlanetRadius.radiusFor(body),
            modelPath(body),
            RenderContext.solar());
    Model3dView view = new Model3dView(Node::attachChild, new Node("anchor"), config);
    view.isolateRing(model, PlanetMeshCorrection.ringNodePrefixFor(body).orElseThrow());
    applyWrapLighting(assetManager, model);
    view.onModelLoaded(model);
    return view;
  }

  /** What {@code AssetFactory.applyLambert} does to the uniforms this test reads: replaces them. */
  private static void applyWrapLighting(AssetManager assetManager, Spatial model) {
    model.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            geometry.setMaterial(new Material(assetManager, "MatDefs/Light/WrapLighting.j3md"));
          }
        });
  }

  private static Map<String, Float> occluderRadii(Model3dView view) {
    Map<String, Float> radii = new LinkedHashMap<>();
    view.getModelBucket()
        .depthFirstTraversal(
            new SceneGraphVisitorAdapter() {
              @Override
              public void visit(Geometry geometry) {
                MatParam param = geometry.getMaterial().getParam("OccluderRadius");
                radii.put(geometry.getName(), param == null ? 0f : (Float) param.getValue());
              }
            });
    return radii;
  }

  private static float drawnRadiusUnits(SolarSystemBody body) {
    return (float) (PlanetRadius.radiusFor(body) / RenderContext.PLANET_METERS_PER_UNIT);
  }

  private static List<SolarSystemBody> ringedBodies() {
    List<SolarSystemBody> ringed =
        Arrays.stream(SolarSystemBody.values())
            .filter(body -> PlanetMeshCorrection.ringNodePrefixFor(body).isPresent())
            .toList();
    assertEquals(2, ringed.size(), "expected Saturn and Uranus, got " + ringed);
    return ringed;
  }

  private static List<Geometry> ringGeometriesOf(Spatial model, String prefix) {
    return ModelNodes.firstNamed(model, prefix)
        .map(ModelNodes::geometriesUnder)
        .orElseGet(List::of);
  }

  /** The model's globe pole: the frame of lowest residual, the rule {@code meshProbe} uses. */
  private static Vector3f globePole(List<ProbedGeometry> probed) {
    MeshFrame best = null;
    for (ProbedGeometry geometry : probed) {
      if (geometry.hasFrame()
          && (best == null
              || geometry.frame().equirectangularResidualDeg()
                  < best.equirectangularResidualDeg())) {
        best = geometry.frame();
      }
    }
    if (best == null) {
      throw new AssertionError("no measurable globe in the model");
    }
    return best.pole();
  }

  private static List<String> namesOf(List<Geometry> geometries) {
    return geometries.stream().map(Geometry::getName).toList();
  }

  private static Spatial loadModel(SolarSystemBody body, AssetManager assetManager) {
    return assetManager.loadModel(modelPath(body));
  }

  private static String modelPath(SolarSystemBody body) {
    String name = body.displayName().toLowerCase(Locale.ROOT);
    return "models/planets/" + name + "/" + name + ".gltf";
  }
}
