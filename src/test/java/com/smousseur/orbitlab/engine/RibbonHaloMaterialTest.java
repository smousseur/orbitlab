package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.OrbitlabException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The ribbon's halo variant writes premultiplied colour, so it must never be compiled without the
 * blend that reads it, and never reach a ribbon that was not switched to it — a mission trajectory
 * keeps the shader and the blend it had.
 */
class RibbonHaloMaterialTest {

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void aTrajectoryRibbonHasNoHaloAndBlendsAsAlpha() {
    Material trajectory = AssetFactory.get().createRibbon(ColorRGBA.White, 2f, true);

    assertNull(trajectory.getParam("HaloPx"));
    assertEquals(BlendMode.Alpha, trajectory.getAdditionalRenderState().getBlendMode());
  }

  @Test
  void aRibbonSwitchedToItsHaloBlendsPremultiplied() {
    Material orbit = AssetFactory.get().createRibbon(ColorRGBA.Blue, 2.5f, false);

    AssetFactory.get().enableRibbonHalo(orbit, 18f);

    assertEquals(Float.valueOf(18f), orbit.getParamValue("HaloPx"));
    assertEquals(BlendMode.PremultAlpha, orbit.getAdditionalRenderState().getBlendMode());
  }

  @Test
  void theHaloVariantIsSwitchedOnByTheHaloWidth() {
    Material ribbon = AssetFactory.get().createRibbon(ColorRGBA.Blue, 2.5f, false);

    TechniqueDef technique =
        ribbon.getMaterialDef().getTechniqueDefs(TechniqueDef.DEFAULT_TECHNIQUE_NAME).get(0);

    assertEquals("HAS_HALO", technique.getShaderParamDefine("HaloPx"));
  }
}
