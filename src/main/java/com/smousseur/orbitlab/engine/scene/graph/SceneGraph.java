package com.smousseur.orbitlab.engine.scene.graph;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SceneGraph {
  public static final String ORBIT_PREFIX = "Orbit-";
  public static String PLANETS_BUCKET = "PlanetsBucket";
  public static String PLANET_ANCHOR_PREFIX = "PlanetAnchor-";

  private Node rootNode;

  private final Node farRoot = new Node("farRoot");
  private final Node farFrame = new Node("farFrame");
  private final Node farOrbitsNode = new Node("farOrbitsNode");
  private final Node farBodiesNode = new Node("farBodiesNode");

  private final Node nearRoot = new Node("nearRoot");
  private final Node nearFrame = new Node("nearFrame");

  private final Node nearOrbitsNode = new Node("nearOrbitsNode");
  private final Node nearBodiesNode = new Node("nearBodiesNode");

  private final OrbitLayer farOrbitLayer = new OrbitLayer(farOrbitsNode);
  private final OrbitLayer nearOrbitLayer = new OrbitLayer(nearOrbitsNode);

  public SceneGraph() {
    farRoot.attachChild(farFrame);
    farFrame.attachChild(farOrbitsNode);
    farFrame.attachChild(farBodiesNode);

    nearRoot.attachChild(nearFrame);
    nearFrame.attachChild(nearOrbitsNode);
    nearFrame.attachChild(nearBodiesNode);
  }

  public void attachTo(Node rootNode) {
    this.rootNode = rootNode;
    Objects.requireNonNull(rootNode, "rootNode");
    if (farRoot.getParent() == null) {
      rootNode.attachChild(farRoot);
    }
    if (nearRoot.getParent() == null) {
      rootNode.attachChild(nearRoot);
    }
  }

  public void detachFromParent() {
    farRoot.removeFromParent();
    nearRoot.removeFromParent();
  }

  public Spatial getBodySpatial(SolarSystemBody body) {
    return ((Node) farBodiesNode.getChild(PLANETS_BUCKET))
        .getChild(PLANET_ANCHOR_PREFIX + body.name());
  }

  public void setOrbitVisible(SolarSystemBody body, boolean visible) {
    farOrbitLayer
        .orbitNode(body)
        .setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  public OrbitLayer orbits() {
    return farOrbitLayer;
  }

  public Node bodiesNode() {
    return farBodiesNode;
  }

  public Node getFarRoot() {
    return farRoot;
  }

  public Node getFarFrame() {
    return farFrame;
  }

  public Node getNearRoot() {
    return nearRoot;
  }

  public Node getRootNode() {
    return rootNode;
  }

  public void setSolarVisible(boolean visible) {
    farRoot.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  public void setPlanetVisible(boolean visible) {
    nearRoot.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  public static final class OrbitLayer {
    private final Node parent;
    private final Map<SolarSystemBody, Node> perBodyNodes = new EnumMap<>(SolarSystemBody.class);

    OrbitLayer(Node parent) {
      this.parent = Objects.requireNonNull(parent, "parent");
    }

    public void setVisible(boolean visible) {
      parent.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    public Node orbitNode(SolarSystemBody body) {
      Objects.requireNonNull(body, "body");
      return perBodyNodes.computeIfAbsent(
          body,
          b -> {
            Node n = new Node(ORBIT_PREFIX + b.name());
            parent.attachChild(n);
            return n;
          });
    }
  }
}
