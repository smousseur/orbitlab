package com.smousseur.orbitlab.engine.scene.graph;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the 3D scene graph hierarchy for the dual-viewport rendering system.
 *
 * <p>The scene graph is split into two root nodes:
 * <ul>
 *   <li><strong>Far root</strong>: Contains solar system-scale elements (orbit lines, distant bodies)
 *       rendered with a far frustum.</li>
 *   <li><strong>Near root</strong>: Contains planet/spacecraft-scale elements rendered with a near frustum.</li>
 * </ul>
 *
 * <p>Each root has a frame node for floating-origin offset, plus dedicated nodes for orbits and bodies.
 */
public final class SceneGraph {
  public static final String ORBIT_PREFIX = "Orbit-";
  public static String PLANETS_BUCKET = "PlanetsBucket";
  public static String PLANET_ANCHOR_PREFIX = "PlanetAnchor-";
  public static String NEAR_PLANET_ANCHOR_PREFIX = "NearPlanetAnchor-";

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

  /**
   * Attaches both the far and near root nodes to the specified JME3 root node.
   *
   * @param rootNode the JME3 root node to attach to
   */
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

  /**
   * Detaches both the far and near root nodes from their parent node.
   */
  public void detachFromParent() {
    farRoot.removeFromParent();
    nearRoot.removeFromParent();
  }

  /**
   * Returns the spatial (anchor node) for the given solar system body in the far bodies node.
   *
   * @param body the solar system body to look up
   * @return the spatial representing the body's anchor in the scene graph
   */
  public Spatial getBodySpatial(SolarSystemBody body) {
    return ((Node) farBodiesNode.getChild(PLANETS_BUCKET))
        .getChild(PLANET_ANCHOR_PREFIX + body.name());
  }

  /**
   * Sets the visibility of the orbit line for the given solar system body.
   *
   * @param body    the solar system body whose orbit visibility to control
   * @param visible {@code true} to show the orbit, {@code false} to hide it
   */
  public void setOrbitVisible(SolarSystemBody body, boolean visible) {
    farOrbitLayer
        .orbitNode(body)
        .setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Returns the far orbit layer used for managing orbit line nodes.
   *
   * @return the far orbit layer
   */
  public OrbitLayer orbits() {
    return farOrbitLayer;
  }

  /**
   * Returns the far bodies node where planet and celestial body spatials are attached.
   *
   * @return the far bodies node
   */
  public Node bodiesNode() {
    return farBodiesNode;
  }

  /**
   * Returns the near frame node, used for applying the km-scale coordinate transform
   * in the planet/spacecraft viewport.
   *
   * @return the near frame node
   */
  public Node nearFrame() {
    return nearFrame;
  }

  /**
   * Returns the near bodies node where planet near-scale spatials are attached.
   *
   * @return the near bodies node
   */
  public Node nearBodiesNode() {
    return nearBodiesNode;
  }

  /**
   * Returns the far root node (solar system scale viewport).
   *
   * @return the far root node
   */
  public Node getFarRoot() {
    return farRoot;
  }

  /**
   * Returns the far frame node, used for applying floating-origin offsets at solar system scale.
   *
   * @return the far frame node
   */
  public Node getFarFrame() {
    return farFrame;
  }

  /**
   * Returns the near root node (planet/spacecraft scale viewport).
   *
   * @return the near root node
   */
  public Node getNearRoot() {
    return nearRoot;
  }

  /**
   * Returns the JME3 root node that this scene graph is attached to.
   *
   * @return the JME3 root node, or {@code null} if not yet attached
   */
  public Node getRootNode() {
    return rootNode;
  }

  /**
   * Sets the visibility of the entire far (solar system scale) scene.
   *
   * @param visible {@code true} to show, {@code false} to hide
   */
  public void setSolarVisible(boolean visible) {
    farRoot.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Sets the visibility of the entire near (planet/spacecraft scale) scene.
   *
   * @param visible {@code true} to show, {@code false} to hide
   */
  public void setPlanetVisible(boolean visible) {
    nearRoot.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
  }

  /**
   * Manages per-body orbit line nodes within a parent orbit container node.
   * Creates and caches individual nodes for each solar system body on demand.
   */
  public static final class OrbitLayer {
    private final Node parent;
    private final Map<SolarSystemBody, Node> perBodyNodes = new EnumMap<>(SolarSystemBody.class);

    OrbitLayer(Node parent) {
      this.parent = Objects.requireNonNull(parent, "parent");
    }

    /**
     * Sets the visibility of the entire orbit layer.
     *
     * @param visible {@code true} to show all orbits, {@code false} to hide them
     */
    public void setVisible(boolean visible) {
      parent.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    /**
     * Returns (or creates) the dedicated orbit node for the given solar system body.
     * The node is automatically attached to the parent orbit container.
     *
     * @param body the solar system body
     * @return the orbit node for the body
     */
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
