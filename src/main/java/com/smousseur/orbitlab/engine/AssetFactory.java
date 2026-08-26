package com.smousseur.orbitlab.engine;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;

/**
 * Singleton factory for creating and managing JME3 assets such as 3D models and materials.
 *
 * <p>Provides a shared thread pool for asynchronous asset loading and convenience methods for
 * creating unshaded materials with solid or alpha-blended colors.
 *
 * <p>Must be initialized once via {@link #init(AssetManager)} before use. Access the singleton
 * instance via {@link #get()}.
 */
public class AssetFactory {
  private final AssetManager assetManager;

  private static final ExecutorService ASSET_LOADING_EXECUTOR =
      Executors.newFixedThreadPool(
          Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
          new ThreadFactory() {
            private final AtomicInteger idx = new AtomicInteger(1);

            @Override
            public Thread newThread(@NonNull Runnable r) {
              Thread t = new Thread(r, "asset-loading-" + idx.getAndIncrement());
              t.setDaemon(true);
              return t;
            }
          });

  private AssetFactory(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  /**
   * Loads a 3D model from the given asset path and applies a uniform scale.
   *
   * @param path the asset path to the model file
   * @param scale the uniform scale factor to apply to the loaded model
   * @return the loaded and scaled spatial
   */
  public Spatial loadModel(String path, float scale) {
    Spatial model = assetManager.loadModel(path);
    model.setLocalScale(scale);
    return model;
  }

  /**
   * Loads a texture from the specified path within the "textures" directory.
   *
   * @param path the relative path to the texture file, excluding the "textures/" prefix
   * @return the loaded texture, or null if the texture could not be loaded
   */
  public Texture loadTexture(String path) {
    return assetManager.loadTexture("textures/" + path);
  }

  public Spatial createSkybox(
      String westTex,
      String eastTex,
      String northTex,
      String southTex,
      String upTex,
      String downTex) {
    return SkyFactory.createSky(
        assetManager,
        loadTexture(westTex),
        loadTexture(eastTex),
        loadTexture(northTex),
        loadTexture(southTex),
        loadTexture(upTex),
        loadTexture(downTex));
  }

  public Spatial createSkybox(
      Texture west, Texture east, Texture north, Texture south, Texture up, Texture down) {
    return SkyFactory.createSky(assetManager, west, east, north, south, up, down);
  }

  /**
   * Replaces the material of every {@link Geometry} under the given spatial with a twilight Lambert
   * material ({@code MatDefs/Light/WrapLighting.j3md}).
   *
   * @param spatial the root spatial whose geometries should be re-materialized
   * @param fallOffFactor width of the twilight band on the lit side (0 = hard step, 0.1-0.3 = soft
   *     twilight)
   * @return the same spatial, for fluent chaining
   */
  public Spatial applyLambert(Spatial spatial, float fallOffFactor) {
    spatial.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geom) {
            Texture diffuse = extractDiffuseTexture(geom.getMaterial());
            Material lambert = new Material(assetManager, "MatDefs/Light/WrapLighting.j3md");
            lambert.setBoolean("UseMaterialColors", true);
            lambert.setColor("Ambient", new ColorRGBA(0.1f, 0.1f, 0.1f, 0.1f));
            lambert.setColor("Diffuse", ColorRGBA.White);
            lambert.setFloat("FallOffFactor", fallOffFactor);
            if (diffuse != null) {
              lambert.setTexture("DiffuseMap", diffuse);
            }
            geom.setMaterial(lambert);
          }
        });
    return spatial;
  }

  /**
   * Declares every {@link Geometry} under the given spatial as a bloom source, by writing the glow
   * colour into the material parameter its {@code Glow} technique reads.
   *
   * <p>This is what makes {@code BloomFilter} in {@code GlowMode.Objects} pick the spatial up: that
   * mode re-renders the scene through the {@code Glow} technique, and a material that declares
   * neither a glow colour nor a glow map outputs black there and contributes nothing. Which
   * parameter carries it depends on the material definition, so both spellings are handled: the PBR
   * definitions the GLTF loader assigns call it {@code Emissive}, {@code Unshaded} and {@code
   * Lighting} call it {@code GlowColor}. A material that has neither is left alone — it simply will
   * not bloom.
   *
   * <p>The colour multiplies the emissive/glow texture when the material has one, so white means
   * "bloom on the texture as it is". How far it carries is a property of the filter, not of the
   * material — and for a star that is only the inward bleed over the limb, the halo itself being
   * geometry: see {@code CoronaView} and {@code PostFxAppState}.
   *
   * <p><b>The disc is brought in line with its own halo.</b> On the PBR definitions the lit pass
   * and the glow pass do not agree out of the box: the lit surface computes {@code emissive.rgb *
   * pow(emissive.a, EmissivePower) * EmissiveIntensity} while {@code PBRGlow.frag} computes plain
   * {@code EmissiveMap * Emissive}, ignoring both extra factors. With the stock {@code
   * EmissiveIntensity} of 2 the disc therefore saturates a stop ahead of the halo it casts, and
   * lands visibly paler than it. Neutralising the factor makes both passes read the same product,
   * so one tint governs the whole appearance and the halo is the disc's own colour rather than a
   * darker version of it.
   *
   * @param spatial the root spatial whose geometries should glow
   * @param glowColor the colour fed to the glow pass
   * @return the same spatial, for fluent chaining
   */
  public Spatial applyGlow(Spatial spatial, ColorRGBA glowColor) {
    spatial.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geom) {
            Material material = geom.getMaterial();
            MaterialDef definition = material.getMaterialDef();
            if (definition.getMaterialParam("Emissive") != null) {
              material.setColor("Emissive", glowColor);
              if (definition.getMaterialParam("EmissiveIntensity") != null) {
                material.setFloat("EmissiveIntensity", 1f);
              }
            } else if (definition.getMaterialParam("GlowColor") != null) {
              material.setColor("GlowColor", glowColor);
            }
          }
        });
    return spatial;
  }

  private static Texture extractDiffuseTexture(Material source) {
    if (source == null) {
      return null;
    }
    MatParamTexture param = source.getTextureParam("BaseColorMap");
    if (param == null) {
      param = source.getTextureParam("DiffuseMap");
    }
    return param != null ? param.getTextureValue() : null;
  }

  /**
   * Creates an unshaded material with the specified solid color.
   *
   * @param color the color to apply to the material
   * @return a new unshaded material with the given color
   */
  public Material material(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor("Color", color);
    return material;
  }

  /**
   * Creates the material for a star's corona: a procedural radial glow, additive, that writes no
   * depth but still tests against it.
   *
   * <p>Keeping the depth <em>test</em> is what does the masking, for free. The quad is
   * camera-facing and centred on the star, so the near hemisphere of the star's own sphere lies in
   * front of the quad's plane: every fragment inside the limb fails the test and only the annulus
   * outside the silhouette is drawn. No stencil, no discard, no second pass — and anything that
   * passes in front of the star occludes its glow correctly as a consequence.
   *
   * @param color glow colour; its alpha is the overall strength
   * @param coreRatio where the star's limb falls as a fraction of the quad's half-width, i.e. the
   *     reciprocal of the multiple of the stellar radius the mesh was sized at
   * @param falloff exponent of the radial decay past the limb
   * @return the corona material
   */
  public Material createCorona(ColorRGBA color, float coreRatio, float falloff) {
    Material material = new Material(assetManager, "MatDefs/Fx/Corona.j3md");
    material.setColor("Color", color);
    material.setFloat("CoreRatio", coreRatio);
    material.setFloat("Falloff", falloff);
    material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
    material.getAdditionalRenderState().setDepthWrite(false);
    return material;
  }

  /**
   * Creates the material for a camera-facing ribbon: a line whose width is expressed in screen
   * pixels and whose edges fade over exactly one pixel (spec {@code
   * docs/graphics-effects/ribbon-lines.md} §7.3, §7.7).
   *
   * <p>The render state is the whole of the design that is not in the shader:
   *
   * <ul>
   *   <li><b>Face culling off</b> — the ribbon turns to face the camera in the vertex shader, so
   *       which way a triangle winds is decided at draw time and cannot be known here. Culling
   *       either face would make the line blink out at the orientations where the winding flips.
   *   <li><b>Depth test on</b> — a mission trajectory must keep disappearing behind the central
   *       body, which writes depth in the same viewport.
   *   <li><b>Depth write off</b> — two ribbons that cross blend instead of one erasing the other.
   *       On threads this is the readable behaviour, and it removes any dependence on JME's
   *       per-geometry sorting, which cannot order two interleaved curves anyway.
   * </ul>
   *
   * @param color the uniform colour; multiplied by the vertex colour when {@code vertexColor} is
   *     set
   * @param widthPx the ribbon's width in screen pixels
   * @param vertexColor whether the mesh carries a per-vertex colour buffer
   * @return the ribbon material
   */
  public Material createRibbon(ColorRGBA color, float widthPx, boolean vertexColor) {
    Material material = new Material(assetManager, "MatDefs/Fx/Ribbon.j3md");
    material.setColor("Color", color);
    material.setFloat("WidthPx", widthPx);
    material.setBoolean("VertexColor", vertexColor);
    RenderState state = material.getAdditionalRenderState();
    state.setBlendMode(RenderState.BlendMode.Alpha);
    state.setFaceCullMode(RenderState.FaceCullMode.Off);
    state.setDepthWrite(false);
    return material;
  }

  /**
   * Creates an unshaded material with alpha blending enabled and depth write disabled.
   *
   * @param color the color (with alpha channel) to apply to the material
   * @return a new alpha-blended unshaded material
   */
  public Material alphaMaterial(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor("Color", color);
    material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
    material.getAdditionalRenderState().setDepthWrite(false);
    return material;
  }

  /**
   * Returns the shared executor service used for asynchronous asset loading.
   *
   * @return the asset loading executor service
   */
  public ExecutorService assetLoadingExecutor() {
    return ASSET_LOADING_EXECUTOR;
  }

  /** Immediately shuts down the asset loading executor, cancelling any pending tasks. */
  public void shutdown() {
    ASSET_LOADING_EXECUTOR.shutdownNow();
  }

  private static class Holder {
    private static volatile AssetFactory INSTANCE;
  }

  /**
   * Initializes the singleton factory with the given asset manager. Must be called exactly once
   * before any calls to {@link #get()}.
   *
   * @param assetManager the JME3 asset manager to use for loading assets
   * @throws OrbitlabException if the factory has already been initialized
   */
  public static synchronized void init(AssetManager assetManager) {
    if (Holder.INSTANCE == null) {
      Holder.INSTANCE = new AssetFactory(assetManager);
    } else {
      throw new OrbitlabException("Asset factory already initialized");
    }
  }

  /**
   * Returns the singleton instance of the asset factory.
   *
   * @return the initialized asset factory instance
   * @throws OrbitlabException if the factory has not been initialized
   */
  public static AssetFactory get() {
    AssetFactory instance = Holder.INSTANCE;
    if (instance == null) {
      throw new OrbitlabException("Asset factory not initialized");
    }
    return instance;
  }
}
