package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The colour of a launcher is carried by its materials' base colour factor and practically never by
 * a map: of the 133 750 triangles of {@code ariane_64.gltf}, 32 carry a base colour texture. {@code
 * applyLambert} forcing {@code Diffuse} to white therefore drew the whole vehicle in one flat tone,
 * and that is what these pin — together with the one part of the factor that must <em>not</em> be
 * carried.
 */
class LambertBaseColorTest {

  private static AssetManager assetManager;

  @BeforeAll
  static void initAssetFactory() {
    assetManager = new DesktopAssetManager(true);
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void carriesTheSourceBaseColourIntoDiffuse() {
    Spatial model = geometryWithBaseColor(new ColorRGBA(0.2f, 0.3f, 0.4f, 1f));

    AssetFactory.get().applyLambert(model, 0.3f);

    assertEquals(new ColorRGBA(0.2f, 0.3f, 0.4f, 1f), diffuseOf(model));
  }

  /**
   * A base colour factor may carry an alpha — Venus's atmosphere shell does, at 0.722 — and {@code
   * WrapLighting} multiplies it into the fragment's own alpha. Carrying it would turn a shell that
   * is opaque today translucent, on a model whose transparency is decided by its render state.
   */
  @Test
  void leavesTheAlphaAtOne() {
    Spatial model = geometryWithBaseColor(new ColorRGBA(1f, 1f, 1f, 0.7218486f));

    AssetFactory.get().applyLambert(model, 0.3f);

    assertEquals(1f, diffuseOf(model).a, 0f);
  }

  /** GLTF's own default for a material that declares no factor, and what was drawn before. */
  @Test
  void fallsBackToWhiteWhenTheSourceDeclaresNoBaseColour() {
    Geometry geometry = new Geometry("plain", new Box(1, 1, 1));
    geometry.setMaterial(new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md"));
    Node model = new Node("model");
    model.attachChild(geometry);

    AssetFactory.get().applyLambert(model, 0.3f);

    assertEquals(ColorRGBA.White, diffuseOf(model));
  }

  /**
   * The asset half of the same guarantee. A booster carries no base colour map at all, so every
   * tone it has is a factor: five materials, five distinct tones — black, two greys, one blue, and
   * the white a material that declares no factor at all falls back to. All five collapsed to that
   * single white before the carry-over.
   */
  @Test
  void aBoosterOfTheAriane64LotKeepsItsDistinctTones() {
    Spatial booster = assetManager.loadModel("models/vehicles/ariane_64/ariane_64-booster1.gltf");

    AssetFactory.get().applyLambert(booster, 0.3f);

    Set<ColorRGBA> tones = new LinkedHashSet<>();
    booster.depthFirstTraversal(
        new SceneGraphVisitorAdapter() {
          @Override
          public void visit(Geometry geometry) {
            tones.add((ColorRGBA) geometry.getMaterial().getParam("Diffuse").getValue());
          }
        });
    assertEquals(5, tones.size(), "distinct Diffuse tones on the booster: " + tones);
    assertTrue(
        tones.contains(new ColorRGBA(0f, 0.00218718f, 0.42922500f, 1f)),
        "the blue of Material.099 is gone: " + tones);
  }

  private Spatial geometryWithBaseColor(ColorRGBA baseColor) {
    Material source = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
    source.setColor("BaseColor", baseColor);
    Geometry geometry = new Geometry("shell", new Box(1, 1, 1));
    geometry.setMaterial(source);
    Node model = new Node("model");
    model.attachChild(geometry);
    return model;
  }

  private static ColorRGBA diffuseOf(Spatial model) {
    Geometry geometry = (Geometry) ((Node) model).getChild(0);
    return (ColorRGBA) geometry.getMaterial().getParam("Diffuse").getValue();
  }
}
