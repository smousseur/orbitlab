package com.smousseur.orbitlab.engine;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.OrbitlabException;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton factory for creating and managing JME3 assets such as 3D models and materials.
 *
 * <p>Provides a shared thread pool for asynchronous asset loading and convenience methods
 * for creating unshaded materials with solid or alpha-blended colors.
 *
 * <p>Must be initialized once via {@link #init(AssetManager)} before use.
 * Access the singleton instance via {@link #get()}.
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
   * @param path  the asset path to the model file
   * @param scale the uniform scale factor to apply to the loaded model
   * @return the loaded and scaled spatial
   */
  public Spatial loadModel(String path, float scale) {
    Spatial model = assetManager.loadModel(path);
    model.setLocalScale(scale);
    return model;
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

  /**
   * Immediately shuts down the asset loading executor, cancelling any pending tasks.
   */
  public void shutdown() {
    ASSET_LOADING_EXECUTOR.shutdownNow();
  }

  private static class Holder {
    private static volatile AssetFactory INSTANCE;
  }

  /**
   * Initializes the singleton factory with the given asset manager.
   * Must be called exactly once before any calls to {@link #get()}.
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
