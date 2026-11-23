package com.smousseur.orbitlab.states.fx;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

/**
 * Draws the plasma volumes of hot pieces at a reduced resolution and lays them over the near view.
 *
 * <h2>Why a filter, and not the main pass</h2>
 *
 * <p>Each volume is a box whose every pixel marches dozens of samples of the plasma. At the
 * application's 1280x720, with the camera inside a volume, that cost 17 ms at 56 samples on this
 * project's reference GPU, against a budget of 2 ms; marched at half that resolution and laid over
 * the scene, at most 1.83 ms at the median, the composite alone 0.08 ms. So the boxes are queued in
 * the {@link RenderQueue.Bucket#Translucent Translucent} bucket, which this filter takes out of the
 * near view's main pass, and drawn here, in {@link #postQueue}, into a target {@code divisor} times
 * smaller in each direction — the way the bloom draws its glow pass at half resolution. The
 * composite then lays that target, filtered bilinearly, over the scene the processor holds.
 *
 * <p>The {@code Translucent} bucket is therefore reserved for the plasma volumes: whatever else
 * were queued there would be drawn into the reduced target too, and the flag that keeps it out of
 * the main pass is the render manager's own, shared by every viewport.
 *
 * <h2>What the target holds</h2>
 *
 * <p>Premultiplied colour: the light the plasma adds, and as alpha the share of what lies behind it
 * hides. Cleared to nothing every frame, so a volume put out leaves no trace, and blended into
 * premultiplied, so several pieces' volumes compose. Laid over the scene, {@code scene × (1 − α) +
 * light}. The composite runs every frame, on an empty target when nothing is hot.
 */
final class ReentryVolumeFilter extends Filter {

  private final int resolutionDivisor;

  private RenderManager renderManager;
  private ViewPort viewPort;
  private FrameBuffer volumeBuffer;
  private Material material;

  /**
   * Creates the filter.
   *
   * @param resolutionDivisor how many times smaller than the view, in each direction, the volumes
   *     are marched (at least 1)
   */
  ReentryVolumeFilter(int resolutionDivisor) {
    super("ReentryVolumeFilter");
    if (resolutionDivisor < 1) {
      throw new IllegalArgumentException(
          "resolutionDivisor must be at least 1, got " + resolutionDivisor);
    }
    this.resolutionDivisor = resolutionDivisor;
  }

  /**
   * The size of the reduced target along one direction: the view's over the divisor, rounded up so
   * the target still covers the view's last row of pixels, and never under one pixel.
   *
   * @param fullSize the view's size along that direction, in pixels
   * @param divisor the resolution divisor
   * @return the target's size, in pixels
   */
  static int reducedSize(int fullSize, int divisor) {
    return Math.max(1, (fullSize + divisor - 1) / divisor);
  }

  @Override
  protected void initFilter(
      AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
    this.renderManager = renderManager;
    this.viewPort = vp;
    Texture2D volume =
        new Texture2D(
            reducedSize(w, resolutionDivisor),
            reducedSize(h, resolutionDivisor),
            Image.Format.RGBA16F);
    volume.setMagFilter(Texture.MagFilter.Bilinear);
    volume.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
    volumeBuffer = new FrameBuffer(volume.getImage().getWidth(), volume.getImage().getHeight(), 1);
    volumeBuffer.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(volume));
    material = new Material(manager, "MatDefs/Fx/ReentryVolumeComposite.j3md");
    material.setTexture("VolumeTex", volume);
    renderManager.setHandleTranslucentBucket(false);
  }

  @Override
  protected Material getMaterial() {
    return material;
  }

  /**
   * The composite reads the scene's depth: where a body's edge crosses a reduced texel, it takes
   * the texel whose depth matches the pixel's rather than blending across the edge, which drew the
   * edge in steps of the reduced texel.
   */
  @Override
  protected boolean isRequiresDepthTexture() {
    return true;
  }

  @Override
  protected void postQueue(RenderQueue queue) {
    Renderer renderer = renderManager.getRenderer();
    renderer.setFrameBuffer(volumeBuffer);
    renderer.setBackgroundColor(ColorRGBA.BlackNoAlpha);
    renderer.clearBuffers(true, false, false);
    if (!queue.isQueueEmpty(RenderQueue.Bucket.Translucent)) {
      queue.renderQueue(RenderQueue.Bucket.Translucent, renderManager, viewPort.getCamera(), true);
    }
    renderer.setFrameBuffer(viewPort.getOutputFrameBuffer());
  }

  @Override
  protected void cleanUpFilter(Renderer r) {
    renderManager.setHandleTranslucentBucket(true);
  }
}
