package com.smousseur.orbitlab.engine.scene.graph;

import com.jme3.scene.Node;

import java.util.Objects;

/**
 * Manages the GUI scene graph hierarchy for overlay elements such as the timeline widget
 * and planet billboard icons. This graph is attached to the JME3 GUI root node and renders
 * in screen-space coordinates.
 */
public class GuiGraph {
  private final Node guiRoot = new Node("guiRoot");
  private final Node guiFrame = new Node("guiFrame");
  private final Node timelineNode = new Node("timelineNode");
  private final Node planetBillboardsNode = new Node("planetBillboardsNode");
  private final Node telemetryNode = new Node("telemetryNode");

  public GuiGraph() {
    guiRoot.attachChild(guiFrame);
    guiFrame.attachChild(timelineNode);
    guiFrame.attachChild(planetBillboardsNode);
    guiFrame.attachChild(telemetryNode);
  }

  /**
   * Attaches this GUI graph to the specified root node if not already attached.
   *
   * @param rootNode the JME3 GUI root node to attach to
   */
  public void attachTo(Node rootNode) {
    Objects.requireNonNull(rootNode, "rootNode");
    if (guiRoot.getParent() == null) {
      rootNode.attachChild(guiRoot);
    }
  }

  /**
   * Returns the main GUI frame node that contains all GUI child nodes.
   *
   * @return the GUI frame node
   */
  public Node getGuiFrame() {
    return guiFrame;
  }

  /**
   * Returns the node designated for the timeline widget.
   *
   * @return the timeline node
   */
  public Node getTimelineNode() {
    return timelineNode;
  }

  /**
   * Returns the node designated for planet billboard icons displayed in the GUI overlay.
   *
   * @return the planet billboards node
   */
  public Node getPlanetBillboardsNode() {
    return planetBillboardsNode;
  }

  /**
   * Returns the node designated for the mission telemetry widget.
   *
   * @return the telemetry node
   */
  public Node getTelemetryNode() {
    return telemetryNode;
  }
}
