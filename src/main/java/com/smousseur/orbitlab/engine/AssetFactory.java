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

  public Spatial loadModel(String path, float scale) {
    Spatial model = assetManager.loadModel(path);
    model.setLocalScale(scale);
    return model;
  }

  public Material material(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor("Color", color);
    return material;
  }

  public Material alphaMaterial(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor("Color", color);
    material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
    material.getAdditionalRenderState().setDepthWrite(false);
    return material;
  }

  public ExecutorService assetLoadingExecutor() {
    return ASSET_LOADING_EXECUTOR;
  }

  public void shutdown() {
    ASSET_LOADING_EXECUTOR.shutdownNow();
  }

  private static class Holder {
    private static volatile AssetFactory INSTANCE;
  }

  public static synchronized void init(AssetManager assetManager) {
    if (Holder.INSTANCE == null) {
      Holder.INSTANCE = new AssetFactory(assetManager);
    } else {
      throw new OrbitlabException("Asset factory already initialized");
    }
  }

  public static AssetFactory get() {
    AssetFactory instance = Holder.INSTANCE;
    if (instance == null) {
      throw new OrbitlabException("Asset factory not initialized");
    }
    return instance;
  }
}
