package com.smousseur.orbitlab.engine.scene.body.lod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.body.BodyRenderConfig;
import org.junit.jupiter.api.Test;

/** Whether the model is drawn this frame reads back what the LOD switch last decided. */
class Model3dViewVisibilityTest {

  @Test
  void itReadsBackTheLastVisibilityItWasGiven() {
    BodyRenderConfig config =
        new BodyRenderConfig(
            "piece",
            "piece",
            ColorRGBA.White,
            35.0,
            "models/vehicles/heavy_falcon/heavy_falcon-booster.gltf",
            RenderContext.planet(SolarSystemBody.EARTH));
    Model3dView model = new Model3dView((bucket, loaded) -> {}, new Node("anchor3d"), config);

    model.setVisible(false);
    assertFalse(model.isVisible());
    model.setVisible(true);
    assertTrue(model.isVisible());
  }
}
