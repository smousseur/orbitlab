package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

public interface PlanetView {
  Spatial spatial(); // noeud 3D ancré dans le monde

  void setPositionWorld(Vector3f position);

  void setRotationWorld(Quaternion rotation);

  void updateScreen(Camera cam);

  void setVisible(boolean visible);

  void detach();

  // Prévu pour LOD (non implémenté ici)
  default void updateLodHint(float distanceToCamera, float screenSizePx) {}
}
