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
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture3D;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The plasma volume writes premultiplied colour and stops its rays on the body itself, so it blends
 * as such without testing depth; it marches the shared noise with its own tuning, and stays out of
 * the bloom.
 */
class ReentryVolumeMaterialTest {

  private static final ReentryFxConfig CONFIG =
      new ReentryFxConfig(1e5, 1.1e7, new ReentryVolumeConfig(40, 2, 3f, 20f, 11f, 290f, 4f, 0.5f));

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  /**
   * Depth off: the volume is marched into its own target, where nothing has drawn a depth, and its
   * rays stop on the body they are told of. Front faces culled: each pixel the box covers is
   * marched once, from the far wall, even with the camera inside the box.
   */
  @Test
  void theVolumeBlendsPremultipliedIgnoresDepthAndDrawsItsFarFacesOnly() {
    RenderState state = AssetFactory.get().createReentryVolume(CONFIG).getAdditionalRenderState();

    assertEquals(BlendMode.PremultAlpha, state.getBlendMode());
    assertFalse(state.isDepthTest());
    assertFalse(state.isDepthWrite());
    assertEquals(RenderState.FaceCullMode.Front, state.getFaceCullMode());
  }

  @Test
  void theVolumeTakesItsStepsGainAndExposureFromTheTuningAndStartsOut() {
    Material volume = AssetFactory.get().createReentryVolume(CONFIG);

    assertEquals(40, (Integer) volume.getParamValue("Steps"));
    assertEquals(4f, (Float) volume.getParamValue("EmissionGain"));
    assertEquals(0.5f, (Float) volume.getParamValue("Exposure"));
    assertEquals(0f, (Float) volume.getParamValue("Intensity"));
  }

  @Test
  void everyVolumeMarchesTheOneSharedNoise() {
    Material first = AssetFactory.get().createReentryVolume(CONFIG);
    Material second = AssetFactory.get().createReentryVolume(CONFIG);

    Texture3D noise = AssetFactory.get().reentryNoiseTexture();
    assertSame(noise, first.getParamValue("NoiseTex"));
    assertSame(noise, second.getParamValue("NoiseTex"));
  }

  @Test
  void theVolumeHasNoGlowTechnique() {
    Material volume = AssetFactory.get().createReentryVolume(CONFIG);

    assertNull(volume.getMaterialDef().getTechniqueDefs("Glow"));
  }

  /**
   * 32 lattice cells sampled twice each, one byte per texel, tiling: the noise the prototype
   * computed by hashing, read instead. Twice and not four times: at 128 texels a side the reads
   * fell out of the texture cache, and the worst case cost 2.46 ms against a budget of 2, measured.
   */
  @Test
  void theNoiseIsA64CubedSingleChannelTilingTexture() {
    Texture3D noise = AssetFactory.get().reentryNoiseTexture();
    Image image = noise.getImage();

    assertEquals(64, image.getWidth());
    assertEquals(64, image.getHeight());
    assertEquals(64, image.getDepth());
    assertEquals(Image.Format.Luminance8, image.getFormat());
    assertEquals(64 * 64 * 64, image.getData(0).limit());
    assertEquals(Texture.WrapMode.Repeat, noise.getWrap(Texture.WrapAxis.S));
    assertEquals(Texture.WrapMode.Repeat, noise.getWrap(Texture.WrapAxis.T));
    assertEquals(Texture.WrapMode.Repeat, noise.getWrap(Texture.WrapAxis.R));
  }

  /**
   * Smooth at the texel scale, varied at the lattice scale: neighbouring texels, half a cell apart,
   * differ less than texels a whole cell apart — about 0.6 times as much on a smoothstep-faded
   * lattice, where the raw lattice, one texel per cell, would differ as much.
   */
  @Test
  void theNoiseIsSmoothBetweenTexelsAndVariedAcrossCells() {
    ByteBuffer data = AssetFactory.get().reentryNoiseTexture().getImage().getData(0);
    double texelStep = 0.0;
    double cellStep = 0.0;
    int count = 0;
    for (int x = 0; x + 2 < 64; x += 3) {
      int base = 32 * 64 * 64 + 32 * 64;
      int here = data.get(base + x) & 0xFF;
      texelStep += Math.abs((data.get(base + x + 1) & 0xFF) - here);
      cellStep += Math.abs((data.get(base + x + 2) & 0xFF) - here);
      count++;
    }
    double meanTexelStep = texelStep / count;
    double meanCellStep = cellStep / count;
    assertTrue(meanCellStep > 15.0, () -> "mean step across a cell " + meanCellStep);
    assertTrue(
        meanTexelStep < 0.75 * meanCellStep,
        () -> "mean step between texels " + meanTexelStep + " vs across a cell " + meanCellStep);
  }
}
