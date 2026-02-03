package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.lod.Planet3dView;
import com.smousseur.orbitlab.engine.scene.planet.lod.PlanetIconView;
import com.smousseur.orbitlab.engine.scene.planet.lod.PlanetLodTuning;

import java.util.Objects;

public final class PlanetLodView implements PlanetView {
  private static final float LOD_DISTANCE = 0.01f;

  private final Node anchor3d;

  private final PlanetIconView iconView;
  private final Planet3dView model3dView;
  private final SceneGraph sceneGraph;
  private final SolarSystemBody body;

  public PlanetLodView(Node guiNode, SceneGraph sceneGraph, PlanetDescriptor planetDescriptor) {
    Objects.requireNonNull(guiNode, "guiNode");
    Objects.requireNonNull(planetDescriptor, "planetDescriptor");
    Objects.requireNonNull(sceneGraph, "sceneGraph");

    this.sceneGraph = sceneGraph;
    this.anchor3d = new Node(SceneGraph.PLANET_ANCHOR_PREFIX + planetDescriptor.body().name());
    this.iconView = new PlanetIconView(guiNode, planetDescriptor);
    this.model3dView = new Planet3dView(anchor3d, planetDescriptor);
    this.body = planetDescriptor.body();
  }

  @Override
  public Spatial spatial() {
    return anchor3d;
  }

  @Override
  public void setPositionWorld(Vector3f worldJmeUnits) {
    anchor3d.setLocalTranslation(worldJmeUnits);
  }

  @Override
  public void setVisible(boolean visible) {
    iconView.setVisible(visible);
  }

  public Node getAnchor3d() {
    return anchor3d;
  }

  public PlanetIconView getIconView() {
    return iconView;
  }

  public Planet3dView getModel3dView() {
    return model3dView;
  }

  public void updateScreen(Camera cam) {
    float distance = cam.getLocation().distance(anchor3d.getWorldTranslation());
    double radius = PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();

    double multiplier = PlanetLodTuning.lodDistanceRatio(body);
    boolean show3d = distance < radius * multiplier;

    if (show3d) {
      model3dView.setVisible(true);
      iconView.setVisible(false);
    } else {
      model3dView.setVisible(false);
      iconView.setVisible(true);
      iconView.updateScreenPosition(cam, anchor3d);
    }
    sceneGraph.setOrbitVisible(body, !show3d);
  }

  public void detach() {
    iconView.detach();
  }
}
