package com.smousseur.orbitlab.engine.scene.body;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.engine.scene.body.lod.BillboardIconView;
import com.smousseur.orbitlab.engine.scene.body.lod.Model3dView;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Level-of-detail view for a body (planet or spacecraft) that switches between a 3D model and a 2D
 * icon based on the camera distance relative to the body's radius.
 *
 * <p>When the camera is close enough (within {@code radius * lodMultiplier}), the full 3D model is
 * displayed. When farther away, a simple colored icon with a label is shown instead.
 */
public final class LodView implements BodyView {
  private final Node farAnchor;
  private final Node anchor3d;
  private final BillboardIconView iconView;
  private final Model3dView model3dView;
  private final BodyRenderConfig config;
  private final Consumer<Boolean> onLodChanged;

  /**
   * Creates a new LOD view for a body, setting up both the 3D model view and the icon view.
   *
   * @param guiNode the GUI node for attaching the 2D icon overlay
   * @param config the render configuration for this body
   * @param onClick optional click handler for the icon; null for no click behavior
   * @param onLodChanged optional callback invoked when LOD state changes; receives {@code true}
   *     when the 3D model is shown, {@code false} when the icon is shown. May be null.
   */
  public LodView(
      Node guiNode, BodyRenderConfig config, Runnable onClick, Consumer<Boolean> onLodChanged) {
    Objects.requireNonNull(guiNode, "guiNode");
    Objects.requireNonNull(config, "config");
    this.config = config;
    this.onLodChanged = onLodChanged;
    this.farAnchor = new Node("Anchor-" + config.id());
    this.anchor3d = new Node("BodyAnchor-" + config.id());
    this.iconView = new BillboardIconView(guiNode, config, onClick);
    this.model3dView = new Model3dView(anchor3d, config);
  }

  @Override
  public Spatial spatial() {
    return farAnchor;
  }

  @Override
  public Spatial nearSpatial() {
    return anchor3d;
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
    model3dView.setVisible(visible);
    iconView.setVisible(visible);
  }

  /**
   * Returns the 3D model view component for this body.
   *
   * @return the body's 3D model view
   */
  public Model3dView getModel3dView() {
    return model3dView;
  }

  /**
   * Updates the LOD state by switching between the 3D model and icon views based on the camera's
   * distance to the body anchor.
   *
   * @param cam the active camera used for distance calculation and screen projection
   */
  @Override
  public void updateScreen(Camera cam) {
    float distance = cam.getLocation().distance(farAnchor.getWorldTranslation());
    double radius = config.radiusMeters() * config.renderContext().unitsPerMeter();
    double multiplier = config.lodMultiplier();
    boolean show3d = distance < radius * multiplier;

    if (show3d) {
      model3dView.setVisible(true);
      iconView.setVisible(false);
    } else {
      model3dView.setVisible(false);
      iconView.setVisible(true);
      iconView.updateScreenPosition(cam, farAnchor);
    }

    if (onLodChanged != null) {
      onLodChanged.accept(show3d);
    }
  }

  @Override
  public void detach() {
    iconView.detach();
  }
}
