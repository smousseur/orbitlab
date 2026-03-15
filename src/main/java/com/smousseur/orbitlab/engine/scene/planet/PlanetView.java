package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

/**
 * Interface for rendering a planet in the 3D scene. Implementations manage the planet's
 * spatial node, position, rotation, screen-space updates, and visibility.
 */
public interface PlanetView {
  /**
   * Returns the root spatial node anchored in the 3D world that represents this planet.
   *
   * @return the planet's spatial node
   */
  Spatial spatial();

  /**
   * Sets the planet's position in world coordinates.
   *
   * @param position the world position to apply
   */
  void setPositionWorld(Vector3f position);

  /**
   * Sets the planet's rotation in world coordinates.
   *
   * @param rotation the world rotation to apply
   */
  void setRotationWorld(Quaternion rotation);

  /**
   * Updates screen-space dependent state such as LOD switching and billboard positioning
   * based on the current camera.
   *
   * @param cam the active camera
   */
  void updateScreen(Camera cam);

  /**
   * Sets the visibility of this planet view.
   *
   * @param visible {@code true} to show the planet, {@code false} to hide it
   */
  void setVisible(boolean visible);

  /**
   * Detaches this planet view from the scene graph, cleaning up any GUI or 3D nodes.
   */
  void detach();

  /**
   * Provides a hint for level-of-detail adjustments based on the camera distance
   * and screen-space size. Default implementation is a no-op.
   *
   * @param distanceToCamera the distance from the camera to the planet in world units
   * @param screenSizePx     the approximate screen size of the planet in pixels
   */
  default void updateLodHint(float distanceToCamera, float screenSizePx) {}
}
