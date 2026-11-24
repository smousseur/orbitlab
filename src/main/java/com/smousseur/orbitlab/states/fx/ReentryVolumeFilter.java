package com.smousseur.orbitlab.states.fx;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
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
 * near view's main pass, and drawn here, in {@link #postFrame}, into a target {@code divisor} times
 * smaller in each direction — the way the bloom draws its glow pass at half resolution. The
 * composite then lays that target, filtered bilinearly, over the scene the processor holds.
 *
 * <h2>Why after the scene</h2>
 *
 * <p>The rays stop on what the scene drew, read from its depth: the piece's own mesh, whatever its
 * shape. An analytic body — a cylinder one body radius wide — fits a launcher piece but not a
 * payload: around a 3 m Landsat 8, seen on screen, it showed as a shell of its own. So the volumes
 * are marched once the scene is drawn, when its depth is valid.
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
  private FrameBuffer volumeBuffer;
  private Material material;

  /** The scene's depth, handed over by the processor; null until it has initialised the filter. */
  private Texture sceneDepth;

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

  /** Keeps the scene's depth the processor hands over, at start-up and at every resize. */
  @Override
  protected void setDepthTexture(Texture depthTexture) {
    super.setDepthTexture(depthTexture);
    sceneDepth = depthTexture;
  }

  /**
   * Marches the volumes, now that the scene is drawn and its depth holds the pieces' meshes. The
   * render manager runs the processors after the opaque buckets and empties the queue only after
   * them, so the {@code Translucent} bucket still holds this frame's volumes here; the filters
   * before this one leave their own camera set, so the view's is set back first, and binding the
   * reduced target then sizes the viewport to it.
   */
  @Override
  protected void postFrame(
      RenderManager renderManager,
      ViewPort viewPort,
      FrameBuffer prevFilterBuffer,
      FrameBuffer sceneBuffer) {
    Renderer renderer = renderManager.getRenderer();
    RenderQueue queue = viewPort.getQueue();
    renderManager.setCamera(viewPort.getCamera(), false);
    renderer.setFrameBuffer(volumeBuffer);
    renderer.setBackgroundColor(ColorRGBA.BlackNoAlpha);
    renderer.clearBuffers(true, false, false);
    if (sceneDepth != null && !queue.isQueueEmpty(RenderQueue.Bucket.Translucent)) {
      bindSceneDepth(
          queue.getList(RenderQueue.Bucket.Translucent),
          sceneDepth,
          volumeBuffer.getWidth(),
          volumeBuffer.getHeight());
      queue.renderQueue(RenderQueue.Bucket.Translucent, renderManager, viewPort.getCamera(), true);
    }
  }

  /**
   * Hands every volume about to be marched the scene's depth, which its rays stop on, and the size
   * of the reduced target, which places each of its texels on that depth.
   *
   * @param volumes the volumes queued this frame
   * @param depth the scene's depth, as the filter post-processor renders it
   * @param targetWidth the reduced target's width, in pixels
   * @param targetHeight the reduced target's height, in pixels
   */
  static void bindSceneDepth(
      Iterable<Geometry> volumes, Texture depth, int targetWidth, int targetHeight) {
    int samples = depth.getImage().getMultiSamples();
    Vector2f targetSize = new Vector2f(targetWidth, targetHeight);
    for (Geometry volume : volumes) {
      Material volumeMaterial = volume.getMaterial();
      volumeMaterial.setTexture("DepthTexture", depth);
      volumeMaterial.setVector2("TargetSize", targetSize);
      if (samples > 1) {
        volumeMaterial.setInt("NumSamplesDepth", samples);
      } else {
        volumeMaterial.clearParam("NumSamplesDepth");
      }
    }
  }

  @Override
  protected void cleanUpFilter(Renderer r) {
    renderManager.setHandleTranslucentBucket(true);
  }
}
