package com.smousseur.orbitlab.engine.scene.body.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;

/** How far a loaded piece reaches below its axis once lying on the ground. */
class Model3dViewGroundClearanceTest {

  /**
   * Model +Z is the side a lying object turns to the ground; the booster's bound reaches 0.041 of
   * the stack unit on it, grid fins and legs included, read on the asset.
   */
  @Test
  void theBoosterReachesItsBoundOnModelPlusZ() {
    Spatial booster =
        new DesktopAssetManager(true)
            .loadModel("models/vehicles/heavy_falcon/heavy_falcon-booster.gltf");
    booster.setLocalScale(70f);

    assertEquals(0.04106017 * 70.0, Model3dView.groundClearanceUnits(booster), 1e-3);
  }
}
