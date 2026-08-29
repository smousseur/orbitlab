package com.smousseur.orbitlab.engine.scene.body.lod;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.smousseur.orbitlab.OrbitLabApplication;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
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

  /**
   * Creates a new 3D view for a body and attaches a model bucket node to the given anchor.
   *
   * @param anchor3d the parent anchor node in the scene graph
   * @param config the render configuration defining model path, radius, and scale
   */
  public Model3dView(Node anchor3d, BodyRenderConfig config) {
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
    return AssetFactory.get()
        .loadModel(
            config.modelPath(),
            2 * (float) (config.radiusMeters() / RenderContext.PLANET_METERS_PER_UNIT));
  }

  /**
   * Callback invoked when the 3D model has been loaded. Enqueues attachment of the model to the
   * model bucket on the JME3 render thread.
   *
   * @param model3d the loaded 3D model spatial
   */
  public void onModelLoaded(Spatial model3d) {
    logger.info("Loaded model for {}", config.displayName());
    OrbitLabApplication.app.enqueue(() -> modelBucket.attachChild(model3d));
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
            Material material = geom.getMaterial();
            material.setVector3("OccluderPosition", occluderPositionWorld);
            material.setFloat("OccluderRadius", occluderRadiusWorld);
            material.setVector3("SunDirection", sunDirectionWorld);
            material.setFloat("SunApparentRadius", sunApparentRadiusRadians);
          }
        });
  }
}
