package com.smousseur.orbitlab.engine.scene.body.lod;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
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
            (float) (config.radiusMeters() / RenderContext.PLANET_METERS_PER_UNIT));
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
}
