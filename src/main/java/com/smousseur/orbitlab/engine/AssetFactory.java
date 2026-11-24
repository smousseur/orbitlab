package com.smousseur.orbitlab.engine;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture3D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.util.SkyFactory;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
  private static final String COLOR = "Color";
  private final AssetManager assetManager;

  /** The plasma volume's noise lattice: cells along each side of the repeating texture. */
  static final int NOISE_CELLS = 32;

  /**
   * Texels per lattice cell along each side of the noise texture. Two, not more: at four, 128
   * texels a side, the six reads each sample makes fell out of the texture cache, and the worst
   * case cost 2.46 ms at the 95th percentile against 1.78 ms at two, measured.
   */
  static final int NOISE_SAMPLES_PER_CELL = 2;

  /** Fixed, so every run draws the same turbulence. */
  private static final long NOISE_SEED = 42L;

  /** The shared plasma noise; null until first asked for. */
  private Texture3D noiseTexture;

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
   * Loads a model and scales it so its largest dimension spans {@code targetSizeUnits}, by
   * measuring its own bounding box rather than assuming the one-unit-tall convention the launcher
   * assets follow. The payload meshes are third-party assets with no shared normalization, so their
   * intrinsic scale has to be measured to draw them at a controlled size. May be called from a
   * background thread — the model is detached, so {@link Spatial#updateGeometricState()} touches no
   * shared state.
   *
   * @param path the asset path to the model file
   * @param targetSizeUnits the size, in render units, the model's largest extent should span
   * @return the loaded model, scaled to that size
   */
  public Spatial loadModelNormalized(String path, float targetSizeUnits) {
    Spatial model = assetManager.loadModel(path);
    model.updateGeometricState();
    float extent = largestExtent(model.getWorldBound());
    model.setLocalScale(extent > 0f ? targetSizeUnits / extent : 1f);
    return model;
  }

  private static float largestExtent(BoundingVolume bound) {
    if (bound instanceof BoundingBox box) {
      return 2f * Math.max(box.getXExtent(), Math.max(box.getYExtent(), box.getZExtent()));
    }
    if (bound instanceof BoundingSphere sphere) {
      return 2f * sphere.getRadius();
    }
    return 0f;
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
   * <p>Two things are carried across from the material being replaced, and only two: its base
   * colour map and its base colour factor. Everything else the GLTF loader built — the metallic and
   * roughness factors and their map, the normals, the occlusion, the emissive, the render state —
   * is dropped with the material that held it.
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
            ColorRGBA baseColor = extractBaseColor(geom.getMaterial());
            Material lambert = new Material(assetManager, "MatDefs/Light/WrapLighting.j3md");
            lambert.setBoolean("UseMaterialColors", true);
            lambert.setColor("Ambient", new ColorRGBA(0.1f, 0.1f, 0.1f, 0.1f));
            lambert.setColor("Diffuse", baseColor);
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

  /**
   * The base colour texture a material carries, whichever spelling its definition uses: the PBR
   * definitions the GLTF loader assigns call it {@code BaseColorMap}, {@code WrapLighting} and the
   * stock lighting definitions call it {@code DiffuseMap}.
   *
   * <p>Public because {@code MeshGuard} needs the same lookup to read a texture's dimensions at
   * load time, and which parameter holds the diffuse is exactly the fragile knowledge that must not
   * exist twice.
   *
   * @param source the material to inspect, may be null
   * @return its base colour texture, or null when it has none
   */
  public static Texture extractDiffuseTexture(Material source) {
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
   * The base colour a material carries, under whichever name its definition uses: the PBR
   * definitions the GLTF loader assigns call it {@code BaseColor}, {@code WrapLighting} and the
   * stock lighting definitions call it {@code Diffuse}.
   *
   * <p>Companion of {@link #extractDiffuseTexture}, and it exists for what the launcher assets are:
   * their colour is in this factor and practically never in a map. Of the 133 750 triangles of
   * {@code ariane_64.gltf}, 32 carry a base colour texture — 0.02 % — and its twenty-eight greys
   * and five tints all live here. Writing a fixed white instead drew the whole vehicle in one flat
   * tone.
   *
   * <p><b>Only the RGB is read.</b> A GLTF base colour factor may carry an alpha — Venus's
   * atmosphere shell does, at 0.722 — and {@code WrapLighting} multiplies it into the fragment's
   * own alpha. Carrying it would turn a shell that is opaque today translucent, on models whose
   * transparency is decided by their render state rather than by this factor.
   *
   * @param source the material to inspect, may be null
   * @return its base colour with alpha 1, or opaque white when it declares none — which is also
   *     GLTF's own default for a material without a factor
   */
  private static ColorRGBA extractBaseColor(Material source) {
    if (source == null) {
      return ColorRGBA.White.clone();
    }
    MatParam param = source.getParam("BaseColor");
    if (param == null) {
      param = source.getParam("Diffuse");
    }
    if (param == null || !(param.getValue() instanceof ColorRGBA color)) {
      return ColorRGBA.White.clone();
    }
    return new ColorRGBA(color.r, color.g, color.b, 1f);
  }

  /**
   * Creates an unshaded material with the specified solid color.
   *
   * @param color the color to apply to the material
   * @return a new unshaded material with the given color
   */
  public Material material(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor(COLOR, color);
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
    material.setColor(COLOR, color);
    material.setFloat("CoreRatio", coreRatio);
    material.setFloat("Falloff", falloff);
    material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
    material.getAdditionalRenderState().setDepthWrite(false);
    return material;
  }

  /**
   * Creates the material for a camera-facing ribbon: a line whose width is expressed in screen
   * pixels and whose edges fade over exactly one pixel.
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
    material.setColor(COLOR, color);
    material.setFloat("WidthPx", widthPx);
    material.setBoolean("VertexColor", vertexColor);
    RenderState state = material.getAdditionalRenderState();
    state.setBlendMode(RenderState.BlendMode.Alpha);
    state.setFaceCullMode(RenderState.FaceCullMode.Off);
    state.setDepthWrite(false);
    return material;
  }

  /**
   * Switches a ribbon material to its halo variant, which can draw a soft halo around the ribbon
   * once given an opacity and blend its core toward white.
   *
   * <p>The width and the blend go together and are only ever set here: the halo variant writes
   * premultiplied colour, which only {@link RenderState.BlendMode#PremultAlpha} composes correctly
   * — the halo then adds light to what lies under it instead of veiling it. With no halo showing,
   * the premultiplied output under that blend is the ribbon as {@link #createRibbon} draws it.
   *
   * @param ribbon a material made by {@link #createRibbon}
   * @param haloPx the full width of the halo, in screen pixels (must be positive)
   */
  public void enableRibbonHalo(Material ribbon, float haloPx) {
    ribbon.setFloat("HaloPx", haloPx);
    ribbon.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.PremultAlpha);
  }

  /**
   * Creates the material of one plasma volume: the box a hot piece is wrapped in up close, in which
   * the plasma is ray-marched, put out until its intensity is set.
   *
   * <p>The render state follows from where and how the box is marched:
   *
   * <ul>
   *   <li><b>{@link RenderState.BlendMode#PremultAlpha}</b> — the shader writes the light the
   *       plasma adds and, as alpha, the share of what lies behind it hides. Several pieces' boxes
   *       blend into one another this way in the reduced-resolution target, which is then laid over
   *       the scene the same way.
   *   <li><b>Depth test and write off</b> — the box is drawn into the volume's own target, where no
   *       depth was written, and each ray stops on the body it is told of rather than on a depth.
   *   <li><b>Front faces culled</b> — only the box's far wall is drawn, so each pixel it covers is
   *       marched once, from the camera to that wall, even with the camera inside the box.
   * </ul>
   *
   * <p>One material per volume, since its intensity, its shock and its box are per piece; the noise
   * texture is shared.
   *
   * @param config the volume's steps, gain and exposure
   * @return the volume material, intensity zero
   */
  public Material createReentryVolume(ReentryFxConfig config) {
    ReentryVolumeConfig volume = config.volume();
    Material material = new Material(assetManager, "MatDefs/Fx/ReentryVolume.j3md");
    material.setInt("Steps", volume.steps());
    material.setFloat("EmissionGain", volume.emissionGain());
    material.setFloat("Exposure", volume.exposure());
    material.setFloat("Intensity", 0f);
    material.setTexture("NoiseTex", reentryNoiseTexture());
    RenderState state = material.getAdditionalRenderState();
    state.setBlendMode(RenderState.BlendMode.PremultAlpha);
    state.setDepthTest(false);
    state.setDepthWrite(false);
    state.setFaceCullMode(RenderState.FaceCullMode.Front);
    return material;
  }

  /**
   * The value noise every plasma volume's turbulence is read from, built once and shared.
   *
   * <p>The prototype hashed eight lattice corners per noise lookup, 48 per sample, and that hashing
   * was most of its cost: reading the noise from a texture made a sample 2.6 times cheaper,
   * measured. The texture holds {@value #NOISE_CELLS} lattice cells along each side, repeating, and
   * samples each cell {@value #NOISE_SAMPLES_PER_CELL} times with the prototype's smoothstep fade
   * between corners, so the hardware's linear filtering between texels comes closer to the
   * prototype's smooth noise than the raw lattice would. One byte per texel.
   *
   * @return the shared noise texture
   */
  public synchronized Texture3D reentryNoiseTexture() {
    if (noiseTexture == null) {
      noiseTexture = buildReentryNoise();
    }
    return noiseTexture;
  }

  private static Texture3D buildReentryNoise() {
    Random random = new Random(NOISE_SEED);
    float[] lattice = new float[NOISE_CELLS * NOISE_CELLS * NOISE_CELLS];
    for (int i = 0; i < lattice.length; i++) {
      lattice[i] = random.nextFloat();
    }
    int size = NOISE_CELLS * NOISE_SAMPLES_PER_CELL;
    ByteBuffer data = BufferUtils.createByteBuffer(size * size * size);
    for (int z = 0; z < size; z++) {
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          float value =
              smoothLattice(
                  lattice,
                  (x + 0.5f) / NOISE_SAMPLES_PER_CELL,
                  (y + 0.5f) / NOISE_SAMPLES_PER_CELL,
                  (z + 0.5f) / NOISE_SAMPLES_PER_CELL);
          data.put((byte) Math.round(value * 255f));
        }
      }
    }
    data.flip();
    Texture3D texture =
        new Texture3D(
            new Image(
                Image.Format.Luminance8,
                size,
                size,
                size,
                new ArrayList<>(List.of(data)),
                ColorSpace.Linear));
    texture.setWrap(Texture.WrapMode.Repeat);
    texture.setMagFilter(Texture.MagFilter.Bilinear);
    texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
    return texture;
  }

  /** The lattice values around a point, blended with a smoothstep fade, the lattice repeating. */
  private static float smoothLattice(float[] lattice, float x, float y, float z) {
    int x0 = (int) Math.floor(x);
    int y0 = (int) Math.floor(y);
    int z0 = (int) Math.floor(z);
    float fx = fade(x - x0);
    float fy = fade(y - y0);
    float fz = fade(z - z0);
    float c00 = lerp(latticeAt(lattice, x0, y0, z0), latticeAt(lattice, x0 + 1, y0, z0), fx);
    float c10 =
        lerp(latticeAt(lattice, x0, y0 + 1, z0), latticeAt(lattice, x0 + 1, y0 + 1, z0), fx);
    float c01 =
        lerp(latticeAt(lattice, x0, y0, z0 + 1), latticeAt(lattice, x0 + 1, y0, z0 + 1), fx);
    float c11 =
        lerp(
            latticeAt(lattice, x0, y0 + 1, z0 + 1), latticeAt(lattice, x0 + 1, y0 + 1, z0 + 1), fx);
    return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
  }

  private static float latticeAt(float[] lattice, int x, int y, int z) {
    int wx = Math.floorMod(x, NOISE_CELLS);
    int wy = Math.floorMod(y, NOISE_CELLS);
    int wz = Math.floorMod(z, NOISE_CELLS);
    return lattice[(wz * NOISE_CELLS + wy) * NOISE_CELLS + wx];
  }

  private static float fade(float t) {
    return t * t * (3f - 2f * t);
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  /**
   * Creates an unshaded material with alpha blending enabled and depth write disabled.
   *
   * @param color the color (with alpha channel) to apply to the material
   * @return a new alpha-blended unshaded material
   */
  public Material alphaMaterial(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor(COLOR, color);
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
