package com.smousseur.orbitlab.states.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.ReentryFxConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * How large the target the plasma volumes are marched into is, against the view it covers, and what
 * each volume is told of the scene it is marched over.
 */
class ReentryVolumeFilterTest {

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  /**
   * Every queued volume is handed the scene's depth and the size of the reduced target: its rays
   * stop on whatever the scene drew, read where each reduced texel is centred.
   */
  @Test
  void everyQueuedVolumeIsHandedTheSceneDepthAndTheTargetSize() {
    Material first = AssetFactory.get().createReentryVolume(ReentryFxConfig.defaults());
    Material second = AssetFactory.get().createReentryVolume(ReentryFxConfig.defaults());
    Texture2D depth = new Texture2D(1280, 720, Image.Format.Depth);

    ReentryVolumeFilter.bindSceneDepth(List.of(volume(first), volume(second)), depth, 640, 360);

    for (Material material : List.of(first, second)) {
      assertSame(depth, material.getParamValue("DepthTexture"));
      assertEquals(new Vector2f(640f, 360f), material.getParamValue("TargetSize"));
      assertNull(material.getParam("NumSamplesDepth"));
    }
  }

  /** A multisampled depth tells the shader how many samples it holds, as the composite is told. */
  @Test
  void aMultisampledDepthGivesItsSampleCount() {
    Material material = AssetFactory.get().createReentryVolume(ReentryFxConfig.defaults());
    Texture2D depth = new Texture2D(1280, 720, 4, Image.Format.Depth);

    ReentryVolumeFilter.bindSceneDepth(List.of(volume(material)), depth, 640, 360);

    assertEquals(4, (Integer) material.getParamValue("NumSamplesDepth"));
  }

  private static Geometry volume(Material material) {
    Geometry volume = new Geometry("volume", new Box(1f, 1f, 1f));
    volume.setMaterial(material);
    return volume;
  }

  /** The application's 1280x720 window, marched at half resolution: the 640x360 measured. */
  @Test
  void theTargetIsTheViewOverTheDivisor() {
    assertEquals(640, ReentryVolumeFilter.reducedSize(1280, 2));
    assertEquals(360, ReentryVolumeFilter.reducedSize(720, 2));
    assertEquals(1280, ReentryVolumeFilter.reducedSize(1280, 1));
  }

  /** An odd size rounds up, so the reduced target still covers the view's last row of pixels. */
  @Test
  void anOddSizeRoundsUp() {
    assertEquals(361, ReentryVolumeFilter.reducedSize(721, 2));
  }

  /**
   * The composite chooses, at a body's edge, the reduced texel whose depth matches the pixel's: it
   * needs the scene's depth, which the processor only renders for a filter that asks for it.
   */
  @Test
  void theCompositeReadsTheSceneDepth() {
    assertTrue(new ReentryVolumeFilter(2).isRequiresDepthTexture());
  }

  /** A view collapsed to a pixel, as during a minimised window, still gets a target. */
  @Test
  void aViewSmallerThanTheDivisorGetsOnePixel() {
    assertEquals(1, ReentryVolumeFilter.reducedSize(1, 4));
    assertEquals(1, ReentryVolumeFilter.reducedSize(0, 2));
  }
}
