package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.OrbitlabException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The sheath writes premultiplied colour, so it must blend as such; it adds light without veiling
 * the body behind it nor counting its own shell twice, and it stays out of the bloom.
 */
class ReentrySheathMaterialTest {

  private static final ReentryFxConfig CONFIG =
      new ReentryFxConfig(
          1e5,
          1.1e7,
          new ColorRGBA(0.9f, 0.4f, 0.2f, 1f),
          new ColorRGBA(1f, 1f, 0.8f, 1f),
          0.25f,
          0.5f,
          2.5f,
          1.6f);

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void theSheathBlendsPremultipliedTestsDepthButDoesNotWriteIt() {
    RenderState state = AssetFactory.get().createReentrySheath(CONFIG).getAdditionalRenderState();

    assertEquals(BlendMode.PremultAlpha, state.getBlendMode());
    assertTrue(state.isDepthTest());
    assertFalse(state.isDepthWrite());
    assertEquals(RenderState.FaceCullMode.Back, state.getFaceCullMode());
  }

  @Test
  void theSheathTakesItsColoursAndCoverageFromTheTuningAndStartsOut() {
    Material sheath = AssetFactory.get().createReentrySheath(CONFIG);

    assertEquals(new ColorRGBA(0.9f, 0.4f, 0.2f, 1f), sheath.getParamValue("RimColor"));
    assertEquals(new ColorRGBA(1f, 1f, 0.8f, 1f), sheath.getParamValue("HotCoreColor"));
    assertEquals(0.25f, (Float) sheath.getParamValue("Coverage"));
    assertEquals(0f, (Float) sheath.getParamValue("Intensity"));
  }

  @Test
  void theSheathHasNoGlowTechnique() {
    Material sheath = AssetFactory.get().createReentrySheath(CONFIG);

    assertNull(sheath.getMaterialDef().getTechniqueDefs("Glow"));
  }
}
