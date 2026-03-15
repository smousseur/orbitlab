package com.smousseur.orbitlab.engine.scene.planet.lod;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.OrbitLabApplication;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.planet.PlanetDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the 3D model representation of a planet, including asynchronous model loading,
 * attachment to the scene graph, and visibility control. The model is loaded from a GLTF
 * asset and scaled to match the planet's physical radius in render units.
 */
public class Planet3dView {
  private final Node modelBucket;
  private final PlanetDescriptor planetDescriptor;
  private static final Logger logger = LogManager.getLogger(Planet3dView.class);

  /**
   * Creates a new 3D view for a planet and attaches a model bucket node to the given anchor.
   *
   * @param anchor3d         the parent anchor node in the scene graph
   * @param planetDescriptor the descriptor defining the planet's identity and visual properties
   */
  public Planet3dView(Node anchor3d, PlanetDescriptor planetDescriptor) {
    this.modelBucket = new Node("PlanetModelBucket-" + planetDescriptor.body().displayName());
    this.planetDescriptor = planetDescriptor;
    anchor3d.attachChild(modelBucket);
  }

  /**
   * Loads the planet's 3D GLTF model from the asset manager, scaled to the planet's
   * physical radius in render units. This method may be called from a background thread.
   *
   * @return the loaded and scaled spatial
   */
  public Spatial loadModel() {
    logger.info("Loading model for {}", planetDescriptor.body().displayName());
    String name = planetDescriptor.displayName().toLowerCase();
    // TODO scale
    double planetRadius = PlanetRadius.radiusFor(planetDescriptor.body());
    return AssetFactory.get()
        .loadModel(
            "models/planets/" + name + "/" + name + ".gltf",
            (float) (planetRadius * RenderContext.solar().unitsPerMeter()));
  }

  /**
   * Callback invoked when the 3D model has been loaded. Enqueues attachment of the model
   * to the model bucket on the JME3 render thread.
   *
   * @param model3d the loaded 3D model spatial
   */
  public void onModelLoaded(Spatial model3d) {
    logger.info("Loaded model for {}", planetDescriptor.body().displayName());
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
