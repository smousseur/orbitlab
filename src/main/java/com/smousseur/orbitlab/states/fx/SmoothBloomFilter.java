package com.smousseur.orbitlab.states.fx;

import com.jme3.asset.AssetManager;
import com.jme3.post.filters.BloomFilter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

/**
 * {@link BloomFilter} with its render targets put back to bilinear filtering, which is the
 * difference between a halo and a mosaic.
 *
 * <p>The bloom is blurred at a fraction of the screen resolution and magnified back up at the
 * composite, so the magnification filter of that small buffer decides whether the result reads as a
 * gradient or as a grid of flat squares the size of one texel. It should be bilinear, and the
 * default JME field value for a texture is exactly that — but render targets never see it:
 *
 * <pre>
 * public Texture2D(Image img) {
 *     setImage(img);
 *     if (img.getData(0) == null) {          // every offscreen render target
 *         setMagFilter(MagFilter.Nearest);
 *         setMinFilter(MinFilter.NearestNoMipMaps);
 *     }
 * }
 * </pre>
 *
 * <p>{@code Texture2D(width, height, format)}, the constructor {@code Filter.Pass.init} uses,
 * delegates to that one with null data. So the guard catches every pass texture in the engine's
 * filter framework, not just the depth ones it reads as if it were written for. {@code
 * SoftBloomFilter} sets both filters back explicitly on its pyramid textures; {@code BloomFilter}
 * does not, and its output is therefore point-magnified by whatever its down-sampling factor is set
 * to. This class re-applies the same correction to the passes {@code BloomFilter} exposes.
 *
 * <p>One pass stays out of reach: {@code preGlowPass}, the full-resolution buffer the scene's
 * {@code Glow} technique renders into, is private and not part of {@link #getPostRenderPasses()}.
 * It is only ever <em>minified</em> — the extract pass samples it down to the blur resolution — so
 * it costs an aliased silhouette at the limb rather than visible blocks, and the blur that follows
 * hides most of it. Nothing short of reflection or a full reimplementation of {@code initFilter}
 * would reach it, and neither is worth an artefact this small.
 */
final class SmoothBloomFilter extends BloomFilter {

  SmoothBloomFilter(GlowMode glowMode) {
    super(glowMode);
  }

  @Override
  protected void initFilter(
      AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
    super.initFilter(manager, renderManager, vp, w, h);
    // Also covers re-initialisation: reInitFilter() routes back through here, so a later change of
    // down-sampling factor cannot quietly restore the point filtering.
    if (getPostRenderPasses() == null) {
      return;
    }
    for (Pass pass : getPostRenderPasses()) {
      Texture2D texture = pass.getRenderedTexture();
      if (texture != null) {
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
      }
    }
  }
}
