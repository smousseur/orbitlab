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
 * The wake writes premultiplied colour like the sheath, and blends as such; it draws its tube's far
 * wall only, and it stays out of the bloom.
 */
class ReentryWakeMaterialTest {

  private static final ReentryFxConfig CONFIG =
      new ReentryFxConfig(
          1e5,
          1.1e7,
          new ColorRGBA(0.9f, 0.4f, 0.2f, 1f),
          new ColorRGBA(1f, 1f, 0.8f, 1f),
          0.25f,
          0.5f,
          2.5f,
          1.6f,
          new ReentryWakeConfig(0.3f, 3f, 45f, 6_500f));

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  /**
   * Front faces culled: only the tube's far wall is drawn, which the body hides where it stands in
   * front of it, and which still surrounds a camera inside the wake.
   */
  @Test
  void theWakeBlendsPremultipliedTestsDepthWritesNoneAndDrawsItsFarWallOnly() {
    RenderState state = AssetFactory.get().createReentryWake(CONFIG).getAdditionalRenderState();

    assertEquals(BlendMode.PremultAlpha, state.getBlendMode());
    assertTrue(state.isDepthTest());
    assertFalse(state.isDepthWrite());
    assertEquals(RenderState.FaceCullMode.Front, state.getFaceCullMode());
  }

  @Test
  void theWakeTakesItsColourAndFloorsFromTheTuningAndStartsOut() {
    Material wake = AssetFactory.get().createReentryWake(CONFIG);

    assertEquals(new ColorRGBA(0.9f, 0.4f, 0.2f, 1f), wake.getParamValue("RimColor"));
    assertEquals(3f, (Float) wake.getParamValue("MinWidthPx"));
    assertEquals(45f, (Float) wake.getParamValue("MinLengthPx"));
    assertEquals(0f, (Float) wake.getParamValue("Intensity"));
  }

  /**
   * The sheath's apex is the hottest point of the envelope: the wake streams back from its rim in
   * the rim's colour, and has no whitening colour to outshine the apex with.
   */
  @Test
  void theWakeHasNoHotCoreColour() {
    Material wake = AssetFactory.get().createReentryWake(CONFIG);

    assertNull(wake.getMaterialDef().getMaterialParam("HotCoreColor"));
  }

  /**
   * The wake only shows past the icon switch, as a line a few pixels wide: it hides what it crosses
   * as much as it shines, so the rim's orange reads over a bright sky and over the trace it runs
   * along. Under the sheath's coverage it mostly added its light to them, which saturated to white.
   */
  @Test
  void theWakeHasNoCoverageOfItsOwn() {
    Material wake = AssetFactory.get().createReentryWake(CONFIG);

    assertNull(wake.getMaterialDef().getMaterialParam("Coverage"));
  }

  @Test
  void theWakeHasNoGlowTechnique() {
    Material wake = AssetFactory.get().createReentryWake(CONFIG);

    assertNull(wake.getMaterialDef().getTechniqueDefs("Glow"));
  }
}
