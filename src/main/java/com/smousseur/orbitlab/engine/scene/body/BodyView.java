package com.smousseur.orbitlab.engine.scene.body;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

/**
 * Interface for rendering a body (planet or spacecraft) in the 3D scene. Implementations manage the
 * body's spatial node, position, rotation, screen-space updates, and visibility.
 */
public interface BodyView {

  /**
   * Returns the root spatial node anchored in the 3D world that represents this body.
   *
   * @return the body's spatial node
   */
  Spatial spatial();

  /**
   * Near spatial spatial.
   *
   * @return the spatial
   */
  Spatial nearSpatial();

  /**
   * Sets the body's position in world coordinates.
   *
   * @param position the world position to apply
   */
  void setPositionWorld(Vector3f position);

  /**
   * Sets the body's rotation in world coordinates.
   *
   * @param rotation the world rotation to apply
   */
  void setRotationWorld(Quaternion rotation);

  /**
   * Updates screen-space dependent state such as LOD switching and billboard positioning based on
   * the current camera.
   *
   * @param cam the active camera
   */
  void updateScreen(Camera cam);

  /**
   * Sets the visibility of this body view.
   *
   * @param visible {@code true} to show, {@code false} to hide
   */
  void setVisible(boolean visible);

  /** Detaches this body view from the scene graph, cleaning up any GUI or 3D nodes. */
  void detach();

  /**
   * Provides a hint for level-of-detail adjustments based on the camera distance and screen-space
   * size. Default implementation is a no-op.
   *
   * @param distanceToCamera the distance from the camera to the body in world units
   * @param screenSizePx the approximate screen size of the body in pixels
   */
  default void updateLodHint(float distanceToCamera, float screenSizePx) {}
}
