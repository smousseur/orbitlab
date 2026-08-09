package com.smousseur.orbitlab.engine;

import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.renderer.Limits;
import com.jme3.renderer.Renderer;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Read-only reporting of the texture sampling state, so that anisotropic filtering can be decided
 * on measurements rather than on assumptions.
 *
 * <p>Anisotropic filtering is not a setting that either "works" or "does not": it is silently
 * inert under two independent conditions, and the two failure modes look identical on screen —
 * nothing changes. This class separates them.
 *
 * <ul>
 *   <li><b>The driver cap.</b> A requested level is clamped to {@link Limits#TextureAnisotropy}
 *       without any error. Asking for 16 on a context that caps at 8 yields 8, so an A/B between
 *       8 and 16 that shows no difference proves nothing until the cap is known.
 *   <li><b>The minification filter.</b> Anisotropic filtering only ever refines the choice of
 *       mipmap sample. On a texture whose {@link Texture.MinFilter} does not use mipmap levels
 *       there is no such choice to refine, and the level is ignored outright.
 * </ul>
 *
 * <p><b>Why the filter, and not the image, is the authority on mipmaps.</b> The obvious check —
 * {@link Image#hasMipmaps()} — answers a narrower question than it appears to: whether the mip
 * chain came down from the asset file. It is false for an ordinary PNG whose mips the renderer
 * generates at upload time, which is the normal case here and is perfectly fine for anisotropy.
 * The decisive signal is {@link Texture.MinFilter#usesMipMapLevels()}: when it holds, {@link
 * Texture#setMinFilter} has already flagged the image as needing generated mipmaps, so the chain
 * is guaranteed to exist by the time the texture is sampled. {@code Image.isMipmapsGenerated()}
 * is deliberately not consulted — it is the renderer's own upload bookkeeping and is still false
 * at the point this runs.
 *
 * <p>Reporting only, by design. Nothing here changes a filter, a level, or a texture: the point
 * of the exercise is to learn what the asset pipeline actually produces before deciding where a
 * setting belongs.
 */
public final class TextureDiagnostics {
  private static final Logger logger = LogManager.getLogger(TextureDiagnostics.class);

  private TextureDiagnostics() {}

  /**
   * Logs the context's anisotropy ceiling and the renderer-wide default level.
   *
   * <p>The two numbers answer different questions. The cap is a property of the driver and bounds
   * anything that can be requested. The default is JME's own fallback, applied to every texture
   * that does not carry a level of its own — it is 1 (i.e. no anisotropy) unless {@link
   * Renderer#setDefaultAnisotropicFilter} has been called, which makes this line the direct
   * confirmation that the "one line at boot" option is or is not in effect.
   *
   * <p>Must be called on the render thread, once the renderer exists.
   *
   * @param renderer the application's renderer
   */
  public static void logRendererCaps(Renderer renderer) {
    Integer cap = renderer.getLimits().get(Limits.TextureAnisotropy);
    logger.info(
        "Texture anisotropy — driver cap: {}, renderer default level: {}",
        cap == null ? "unreported (treat as 1)" : cap,
        renderer.getDefaultAnisotropicFilter());
  }

  /**
   * Logs the sampling state of every texture reachable from the given spatial, one line each.
   *
   * <p>Call this <em>after</em> any re-materialisation, not before. {@link
   * AssetFactory#applyLambert} replaces the GLTF material wholesale and carries a single map
   * across, so a report taken before the swap describes textures that are no longer bound at draw
   * time.
   *
   * <p>Safe to call from an asset-loading thread, which is where models are built: the traversal
   * only reads, and the spatial is not yet attached to the scene graph. Textures are reported once
   * per spatial by identity, so a body that logs twice has two genuinely distinct textures — the
   * ringed planets are the case in point, their rings being a separate geometry.
   *
   * <p>Logged at DEBUG. This survey has already been run and settled the question it was written
   * for (every planetary texture is {@code Trilinear}, none carries a level of its own, hence the
   * single renderer-wide default set at boot); it stays available for the day an asset changes or
   * a new body arrives, without adding a dozen lines to every normal startup.
   *
   * @param label how to name this model in the log, typically the body's display name
   * @param spatial the root of the loaded model
   */
  public static void logTextures(String label, Spatial spatial) {
    if (!logger.isDebugEnabled()) {
      return;
    }
    Set<Texture> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    spatial.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geom) {
            Material material = geom.getMaterial();
            if (material == null) {
              return;
            }
            for (MatParam param : material.getParams()) {
              if (param instanceof MatParamTexture textureParam) {
                Texture texture = textureParam.getTextureValue();
                if (texture != null && seen.add(texture)) {
                  logger.debug("Texture state [{}] {}", label, describe(param.getName(), texture));
                }
              }
            }
          }
        });
    if (seen.isEmpty()) {
      logger.debug("Texture state [{}] no textures bound", label);
    }
  }

  private static String describe(String paramName, Texture texture) {
    Texture.MinFilter minFilter = texture.getMinFilter();
    boolean mipmapped = minFilter.usesMipMapLevels();
    Image image = texture.getImage();
    return String.format(
        "%s: minFilter=%s (mipmapped=%s), magFilter=%s, mipsInAsset=%s, mipsGeneratedOnUpload=%s, "
            + "textureLevel=%s -> anisotropy %s",
        paramName,
        minFilter,
        mipmapped,
        texture.getMagFilter(),
        image != null && image.hasMipmaps(),
        image != null && image.isGeneratedMipmapsRequired(),
        texture.getAnisotropicFilter() == 0 ? "0 (renderer default)" : texture.getAnisotropicFilter(),
        mipmapped ? "EFFECTIVE" : "INERT (min filter uses no mipmap levels)");
  }
}
