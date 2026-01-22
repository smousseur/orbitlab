package com.smousseur.orbitlab.engine.scene.graph;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a scene graph for visualizing a solar system. This class structures nodes for various
 * components such as the sun, orbits, celestial bodies, and optional debugging tools, allowing for
 * controlled visibility and hierarchical attachment in a 3D rendering context.
 *
 * <p>The root node, solarRoot, acts as the parent node for solar system elements, providing a
 * central point of attachment and visibility management.
 */
public final class SceneGraph {
  public static String PLANETS_BUCKET = "PlanetsBucket";
  public static String PLANET_ANCHOR_PREFIX = "PlanetAnchor-";

  private final Node solarRoot = new Node("SolarRoot");
  private final Node solarFrame = new Node("SolarFrame");
  private final Node orbitsNode = new Node("OrbitsNode");
  private final Node bodiesNode = new Node("BodiesNode");

  private final OrbitLayer orbitLayer = new OrbitLayer(orbitsNode);

  public SceneGraph() {
    solarRoot.attachChild(solarFrame);
    solarFrame.attachChild(orbitsNode);
    solarFrame.attachChild(bodiesNode);
  }

  public void attachTo(Node rootNode) {
    Objects.requireNonNull(rootNode, "rootNode");
    if (solarRoot.getParent() == null) {
      rootNode.attachChild(solarRoot);
    }
  }

  public void detachFromParent() {
    solarRoot.removeFromParent();
  }

  public Spatial getPlanetSpatial(SolarSystemBody body) {
    return ((Node) bodiesNode.getChild(PLANETS_BUCKET))
        .getChild(PLANET_ANCHOR_PREFIX + body.name());
  }

  public OrbitLayer orbits() {
    return orbitLayer;
  }

  public Node bodiesNode() {
    return bodiesNode;
  }

  public Node getSolarRoot() {
    return solarRoot;
  }

  public Node getSolarFrame() {
    return solarFrame;
  }

  public void setSolarVisible(boolean visible) {
    solarRoot.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
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
            Node n = new Node("Orbit-" + b.name());
            parent.attachChild(n);
            return n;
          });
    }
  }
}
