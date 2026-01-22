package com.smousseur.orbitlab.engine.scene.graph;

import com.jme3.scene.Node;

import java.util.Objects;

public class GuiGraph {
  private final Node guiRoot = new Node("guiRoot");
  private final Node guiFrame = new Node("guiFrame");
  private final Node timelineNode = new Node("timelineNode");
  private final Node planetBillboardsNode = new Node("planetBillboardsNode");

  public GuiGraph() {
    guiRoot.attachChild(guiFrame);
    guiFrame.attachChild(timelineNode);
    guiFrame.attachChild(planetBillboardsNode);
  }

  public void attachTo(Node rootNode) {
    Objects.requireNonNull(rootNode, "rootNode");
    if (guiRoot.getParent() == null) {
      rootNode.attachChild(guiRoot);
    }
  }

  public Node getGuiFrame() {
    return guiFrame;
  }

  public Node getTimelineNode() {
    return timelineNode;
  }

  public Node getPlanetBillboardsNode() {
    return planetBillboardsNode;
  }
}
