package com.smousseur.orbitlab.engine.scene;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.debug.Arrow;

public class SceneUtils {
  public static Node createAxes(AssetManager assetManager, float length) {
    Node axes = new Node("Axes");

    // X – rouge
    axes.attachChild(makeArrow(assetManager, new Vector3f(length, 0, 0), ColorRGBA.Red));

    // Y – vert (UP)
    axes.attachChild(makeArrow(assetManager, new Vector3f(0, length, 0), ColorRGBA.Green));

    // Z – bleu
    axes.attachChild(makeArrow(assetManager, new Vector3f(0, 0, length), ColorRGBA.Blue));

    return axes;
  }

  private static Geometry makeArrow(AssetManager assetManager, Vector3f dir, ColorRGBA color) {
    Arrow arrow = new Arrow(dir);
    Geometry g = new Geometry("axis", arrow);

    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", color);
    g.setMaterial(mat);

    return g;
  }
}
