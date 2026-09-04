package com.smousseur.orbitlab.engine.scene.body.lod;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import com.smousseur.orbitlab.engine.scene.body.ShellSpin;
import com.smousseur.orbitlab.engine.scene.mesh.ModelNodes;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the 3D model representation of a body (planet or spacecraft), including asynchronous
 * model loading, attachment to the scene graph, and visibility control. The model is loaded from a
 * GLTF asset and scaled according to the body's render configuration.
 */
public class Model3dView {
  private static final Logger logger = LogManager.getLogger(Model3dView.class);

  private final Node modelBucket;
  private final BodyRenderConfig config;
  private final Model3dAttacher model3dAttacher;
  private ShellSpin shellSpin;
  private List<Geometry> ringGeometries = List.of();

  /**
   * Creates a new 3D view for a body and attaches a model bucket node to the given anchor.
   *
   * @param anchor3d the parent anchor node in the scene graph
   * @param config the render configuration defining model path, radius, and scale
   */
  public Model3dView(Model3dAttacher model3dAttacher, Node anchor3d, BodyRenderConfig config) {
    this.model3dAttacher = model3dAttacher;
    this.modelBucket = new Node("ModelBucket-" + config.displayName());
    this.config = config;
    anchor3d.attachChild(modelBucket);
  }

  /**
   * Loads the body's 3D GLTF model from the asset manager, scaled to the body's physical radius in
   * render units. This method may be called from a background thread.
   *
   * @return the loaded and scaled spatial
   */
  public Spatial loadModel() {
    logger.info("Loading model for {}", config.displayName());
    return AssetFactory.get().loadModel(config.modelPath(), 2 * drawnRadiusUnits());
  }

  /**
   * Radius of this body's globe as it is actually drawn, in near-view units.
   *
   * <p><b>This is the model's scale, halved — the two must not be written twice.</b> {@link
   * #loadModel} sizes the asset by the diameter this returns, and {@link #setRingSunlight} hands
   * the same number to the shader as the radius of the sphere casting the ring's shadow. A shadow
   * band computed from any other radius would overhang or undercut the silhouette that casts it,
   * which is the one comparison the eye makes on this effect (`FX-5`).
   *
   * <p>Deliberately not {@code config.renderContext().unitsPerMeter()}: a planet's render config
   * carries the <em>solar</em> context, one unit to 10<sup>9</sup> m, while its model is drawn in
   * the near viewport at one unit to the kilometre. That expression is right where {@code
   * LodView.updateScreen} uses it, against the far camera, and wrong by a factor of a million here.
   */
  private float drawnRadiusUnits() {
    return (float) (config.radiusMeters() / RenderContext.PLANET_METERS_PER_UNIT);
  }

  /**
   * Callback invoked when the 3D model has been loaded. Enqueues attachment of the model to the
   * model bucket on the JME3 render thread.
   *
   * @param model3d the loaded 3D model spatial
   */
  public void onModelLoaded(Spatial model3d) {
    logger.info("Loaded model for {}", config.displayName());
    model3dAttacher.attach(modelBucket, model3d);
  }

  /**
   * Splices a pivot above the named shell of a freshly loaded model so it can be turned
   * independently of the model as a whole — Venus's cloud deck, which laps its ground every four
   * days (L4 of {@code docs/orientation-planetes/01-decoupage.md}).
   *
   * <p>Call from the loading thread, before {@link #onModelLoaded}: the axis conversion inside
   * needs the model to still be its own root. A model that carries no such shell is left alone and
   * {@link #setShellSpin} stays a no-op.
   *
   * @param model the loaded model, not yet attached
   * @param nodeNamePrefix prefix of the shell node's name in the asset
   * @param axisInModelAxes the spin axis in the model's own axes
   */
  public void isolateShell(Spatial model, String nodeNamePrefix, Vector3f axisInModelAxes) {
    shellSpin = ShellSpin.isolate(model, nodeNamePrefix, axisInModelAxes).orElse(null);
  }

  /**
   * Remembers the geometries of a freshly loaded model's ring system, so the planet's own shadow
   * can be cast on them and on nothing else (`FX-5`).
   *
   * <p>Call from the loading thread, before {@link #onModelLoaded}, alongside {@link
   * #isolateShell}. What is kept is the geometries and not their materials: {@code
   * AssetFactory.applyLambert} runs later in the same chain and replaces every material in the
   * model, so a material captured here would be silently orphaned.
   *
   * <p>A model that carries no such node is left alone and {@link #setRingSunlight} stays a no-op.
   *
   * @param model the loaded model, not yet attached
   * @param nodeNamePrefix prefix of the ring node's name in the asset
   */
  public void isolateRing(Spatial model, String nodeNamePrefix) {
    ringGeometries =
        ModelNodes.firstNamed(model, nodeNamePrefix)
            .map(ModelNodes::geometriesUnder)
            .orElseGet(List::of);
  }

  /**
   * Turns this model's independently spinning shell, if it has one.
   *
   * @param angleRad the angle in radians about the axis it was isolated on
   */
  public void setShellSpin(float angleRad) {
    if (shellSpin != null) {
      shellSpin.setAngle(angleRad);
    }
  }

  /**
   * Casts this body's own globe as the occulter of its ring system, for the frame the given Sun
   * direction describes (`FX-5`, {@code docs/roadmap/01-roadmap-v1.md} §4.2). A model with no ring
   * does nothing.
   *
   * <p><b>The caller supplies only what it alone knows.</b> Where the Sun is takes an ephemeris;
   * the other two uniforms do not leave this object. The occulter is the model bucket's own world
   * translation, which is what {@code vPosWorld} is measured against, so the pair is exact without
   * assuming the body's anchor sits on the origin. Its radius is {@link #drawnRadiusUnits()}, the
   * globe as drawn.
   *
   * @param sunDirectionWorld unit vector toward the Sun, in this body's world space
   * @param sunApparentRadiusRadians the Sun's angular radius as seen from this body, in radians
   */
  public void setRingSunlight(Vector3f sunDirectionWorld, float sunApparentRadiusRadians) {
    if (ringGeometries.isEmpty()) {
      return;
    }
    Vector3f globeCentreWorld = modelBucket.getWorldTranslation();
    float globeRadiusWorld = drawnRadiusUnits();
    for (Geometry ring : ringGeometries) {
      Material material = ring.getMaterial();
      material.setVector3("OccluderPosition", globeCentreWorld);
      material.setFloat("OccluderRadius", globeRadiusWorld);
      material.setVector3("SunDirection", sunDirectionWorld);
      material.setFloat("SunApparentRadius", sunApparentRadiusRadians);
    }
  }

  /**
   * Sets the visibility of the 3D model.
   *
   * @param visible {@code true} to show the model, {@code false} to hide it
   */
  public void setVisible(boolean visible) {
    modelBucket.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Returns the model bucket node that holds the loaded 3D model.
   *
   * @return the model bucket node
   */
  public Node getModelBucket() {
    return modelBucket;
  }

  /**
   * Pushes the current occulter onto every geometry's material, for the per-fragment eclipse test
   * in {@code WrapLighting.frag} (`docs/eclipses/01-decoupage.md`, L1). Walks the bucket every call
   * rather than caching materials: the bucket is empty until the async load completes, so the walk
   * is a no-op until then, and is otherwise a handful of geometries per body.
   *
   * <p><b>Ring geometries are skipped.</b> They carry their own occulter — their planet, pushed by
   * {@link #setRingSunlight} — and there is one set of occulter uniforms per material. No body in
   * this application is both eclipsed and ringed today, so nothing currently collides; the guard is
   * what keeps the two mechanisms from silently overwriting each other the day one does.
   *
   * @param occluderPositionWorld the occulting body's centre, in this body's world space
   * @param occluderRadiusWorld the occulting body's radius, in world units
   * @param sunDirectionWorld unit vector toward the Sun, in this body's world space
   * @param sunApparentRadiusRadians the Sun's angular radius as seen from this body, in radians
   */
  public void setOccluder(
      Vector3f occluderPositionWorld,
      float occluderRadiusWorld,
      Vector3f sunDirectionWorld,
      float sunApparentRadiusRadians) {
    modelBucket.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geom) {
            if (ringGeometries.contains(geom)) {
              return;
            }
            Material material = geom.getMaterial();
            material.setVector3("OccluderPosition", occluderPositionWorld);
            material.setFloat("OccluderRadius", occluderRadiusWorld);
            material.setVector3("SunDirection", sunDirectionWorld);
            material.setFloat("SunApparentRadius", sunApparentRadiusRadians);
          }
        });
  }
}
