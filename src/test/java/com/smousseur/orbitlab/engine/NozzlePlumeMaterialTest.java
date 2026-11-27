package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.smousseur.orbitlab.core.OrbitlabException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The plume writes premultiplied colour over the scene, hidden by what lies in front of it without
 * hiding anything itself; it marches the shared noise with its own tuning, and stays out of the
 * bloom.
 */
class NozzlePlumeMaterialTest {

  private static final NozzlePlumeConfig CONFIG = NozzlePlumeConfig.defaults();

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  /**
   * Depth tested, so the launcher and the ground hide what lies behind them; not written, so a
   * plume behind another stays visible through it. Back faces culled while the camera is outside
   * the hull, so each pixel is marched once, from where the ray enters it.
   */
  @Test
  void thePlumeBlendsPremultipliedOverTheSceneAndDrawsItsNearFaces() {
    RenderState state = AssetFactory.get().createNozzlePlume(CONFIG).getAdditionalRenderState();

    assertEquals(BlendMode.PremultAlpha, state.getBlendMode());
    assertTrue(state.isDepthTest());
    assertFalse(state.isDepthWrite());
    assertEquals(RenderState.FaceCullMode.Back, state.getFaceCullMode());
  }

  @Test
  void thePlumeTakesItsStepsAndExposureFromTheTuningAndStartsOut() {
    Material plume = AssetFactory.get().createNozzlePlume(CONFIG);

    assertEquals(CONFIG.maxSteps(), (Integer) plume.getParamValue("MaxSteps"));
    assertEquals(CONFIG.stepRadii(), (Float) plume.getParamValue("StepRadii"));
    assertEquals(CONFIG.exposure(), (Float) plume.getParamValue("Exposure"));
    assertEquals(0f, (Float) plume.getParamValue("Intensity"));
  }

  @Test
  void everyPlumeMarchesTheNoiseThePlasmaMarches() {
    Material plume = AssetFactory.get().createNozzlePlume(CONFIG);

    assertSame(AssetFactory.get().reentryNoiseTexture(), plume.getParamValue("NoiseTex"));
  }

  /**
   * No Glow technique. The bloom re-renders the scene through that technique, and the planets and
   * the launchers, which declare none, are skipped there and hide nothing: a plume that glowed in
   * that pass would shine through the ground and through its own launcher.
   */
  @Test
  void thePlumeStaysOutOfTheBloom() {
    Material plume = AssetFactory.get().createNozzlePlume(CONFIG);

    assertNull(plume.getMaterialDef().getTechniqueDefs("Glow"));
  }
}
