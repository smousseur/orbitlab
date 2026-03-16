package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.FocusView;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import com.smousseur.orbitlab.engine.scene.planet.lod.Planet3dView;
import com.smousseur.orbitlab.engine.scene.planet.lod.PlanetIconView;
import com.smousseur.orbitlab.engine.scene.planet.lod.PlanetLodTuning;
import java.util.Objects;

/**
 * Level-of-detail view for a planet that switches between a 3D model and a 2D icon based on the
 * camera distance relative to the planet's radius.
 *
 * <p>When the camera is close enough (within a body-specific LOD distance ratio multiplied by the
 * planet radius), the full 3D model is displayed. When farther away, a simple colored icon with a
 * label is shown instead.
 */
public final class PlanetLodView implements PlanetView {
  private final Node farAnchor;
  private final Node nearAnchor;
  private final PlanetIconView iconView;
  private final Planet3dView model3dView;
  private final SceneGraph sceneGraph;
  private final SolarSystemBody body;
  private final FocusView focusView;

  /**
   * Creates a new LOD view for a planet, setting up both the 3D model view and the icon view.
   *
   * <p>The far anchor is positioned in heliocentric solar-scale coordinates (farView). The near
   * anchor lives at the origin of the km-scale nearFrame and holds the 3D model, which is scaled in
   * km units. The LOD decision uses the far anchor distance so the switch threshold is expressed in
   * consistent solar-scale world units.
   *
   * @param guiNode the GUI node for attaching the 2D icon overlay
   * @param context the application context providing the scene graph and focus view
   * @param planetDescriptor the descriptor defining the planet's identity and visual properties
   */
  public PlanetLodView(
      Node guiNode, ApplicationContext context, PlanetDescriptor planetDescriptor) {
    Objects.requireNonNull(guiNode, "guiNode");
    Objects.requireNonNull(planetDescriptor, "planetDescriptor");
    this.sceneGraph = Objects.requireNonNull(context.sceneGraph(), "sceneGraph");
    this.farAnchor = new Node(SceneGraph.PLANET_ANCHOR_PREFIX + planetDescriptor.body().name());
    this.nearAnchor =
        new Node(SceneGraph.NEAR_PLANET_ANCHOR_PREFIX + planetDescriptor.body().name());
    this.iconView = new PlanetIconView(guiNode, context.focusView(), planetDescriptor);
    this.model3dView = new Planet3dView(nearAnchor, planetDescriptor);
    this.body = planetDescriptor.body();
    this.focusView = context.focusView();
  }

  @Override
  public Spatial spatial() {
    return farAnchor;
  }

  /**
   * Returns the near-scale anchor node (km coordinate space) that holds the 3D model. This node
   * should be attached to the near bodies bucket in the nearRoot scene graph.
   *
   * @return the near anchor node
   */
  public Spatial nearSpatial() {
    return nearAnchor;
  }

  @Override
  public void setPositionWorld(Vector3f position) {
    farAnchor.setLocalTranslation(position);
  }

  @Override
  public void setRotationWorld(Quaternion rotation) {
    model3dView.getModelBucket().setLocalRotation(rotation);
  }

  @Override
  public void setVisible(boolean visible) {
    iconView.setVisible(visible);
  }

  /**
   * Returns the 3D model view component for this planet.
   *
   * @return the planet's 3D view
   */
  public Planet3dView getModel3dView() {
    return model3dView;
  }

  /**
   * Updates the LOD state by switching between the 3D model and icon views based on the camera's
   * distance to the planet anchor. Also toggles orbit line visibility accordingly.
   *
   * @param cam the active camera used for distance calculation and screen projection
   */
  public void updateScreen(Camera cam) {
    float distance = cam.getLocation().distance(farAnchor.getWorldTranslation());
    double radius = PlanetRadius.radiusFor(body) * RenderContext.solar().unitsPerMeter();

    double multiplier = PlanetLodTuning.lodDistanceRatio(body);
    boolean show3d = distance < radius * multiplier;

    if (show3d) {
      model3dView.setVisible(true);
      iconView.setVisible(false);
    } else {
      model3dView.setVisible(false);
      iconView.setVisible(true);
      iconView.updateScreenPosition(cam, farAnchor);
    }
    // Only hide the orbit for the planet currently focused in planet view.
    // Other planets always keep their orbit visible regardless of LOD state.
    sceneGraph.setOrbitVisible(body, !(focusView.isFocused(body) && show3d));
  }

  /** Detaches the icon view from the GUI, cleaning up its screen-space elements. */
  public void detach() {
    iconView.detach();
  }
}
