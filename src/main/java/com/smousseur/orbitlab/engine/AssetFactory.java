package com.smousseur.orbitlab.engine;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.core.OrbitlabException;

public class AssetFactory {
  private final AssetManager assetManager;

  private AssetFactory(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  public Material material(ColorRGBA color) {
    Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    material.setColor("Color", color);
    return material;
  }

  private static class Holder {
    private static AssetFactory INSTANCE;
  }

  public static void init(AssetManager assetManager) {
    if (Holder.INSTANCE == null) {
      Holder.INSTANCE = new AssetFactory(assetManager);
    } else {
      throw new OrbitlabException("Asset factory already initialized");
    }
  }

  public static AssetFactory get() {
    if (Holder.INSTANCE == null) {
      throw new OrbitlabException("Asset factory not initialized");
    }

    return Holder.INSTANCE;
  }

}
