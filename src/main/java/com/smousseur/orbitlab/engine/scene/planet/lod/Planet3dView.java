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

public class Planet3dView {
  private final Node modelBucket;
  private final PlanetDescriptor planetDescriptor;
  private static final Logger logger = LogManager.getLogger(Planet3dView.class);

  public Planet3dView(Node anchor3d, PlanetDescriptor planetDescriptor) {
    this.modelBucket = new Node("PlanetModelBucket-" + planetDescriptor.body().displayName());
    this.planetDescriptor = planetDescriptor;
    anchor3d.attachChild(modelBucket);
  }

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

  public void onModelLoaded(Spatial model3d) {
    logger.info("Loaded model for {}", planetDescriptor.body().displayName());
    OrbitLabApplication.app.enqueue(() -> modelBucket.attachChild(model3d));
  }

  public void setVisible(boolean visible) {
    modelBucket.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }
}
