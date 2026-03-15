package com.smousseur.orbitlab.engine.scene;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.debug.Arrow;

/**
 * Utility class for creating common scene elements such as coordinate axis indicators.
 */
public class SceneUtils {
  /**
   * Creates a node containing three colored arrows representing the X (red), Y (green),
   * and Z (blue) coordinate axes.
   *
   * @param assetManager the JME3 asset manager used to create arrow materials
   * @param length       the length of each axis arrow in world units
   * @return a node containing the three axis arrows
   */
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
