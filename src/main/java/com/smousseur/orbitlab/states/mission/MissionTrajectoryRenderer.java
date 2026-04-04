package com.smousseur.orbitlab.states.mission;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.engine.AssetFactory;
import java.nio.FloatBuffer;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Renders a mission's trajectory as a line strip. Maintains a circular buffer of GCRF positions and
 * flushes them to a JME mesh each frame. This is a plain object managed by {@link
 * MissionRenderer}, not an AppState.
 */
public final class MissionTrajectoryRenderer {

  private static final int MAX_POINTS = 4096;
  private static final float LINE_WIDTH = 2f;

  private final String missionName;
  private final RenderContext renderContext;
  private final ColorRGBA color;

  private final Vector3D[] positions = new Vector3D[MAX_POINTS];
  private int head = 0;
  private int count = 0;
  private Geometry lineGeometry;

  /**
   * Creates a new trajectory renderer.
   *
   * @param missionName the mission name, used for the geometry's spatial name
   * @param renderContext the render context for coordinate scaling
   * @param color the trajectory line color
   */
  public MissionTrajectoryRenderer(
      String missionName, RenderContext renderContext, ColorRGBA color) {
    this.missionName = Objects.requireNonNull(missionName, "missionName");
    this.renderContext = Objects.requireNonNull(renderContext, "renderContext");
    this.color = Objects.requireNonNull(color, "color");
  }

  /**
   * Creates the line mesh, material, and geometry, and attaches them to the given node.
   *
   * @param nearOrbitsNode the scene node for near-viewport orbit lines
   */
  public void initialize(Node nearOrbitsNode) {
    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.LineStrip);
    FloatBuffer pb = BufferUtils.createFloatBuffer(MAX_POINTS * 3);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
    mesh.updateBound();
    mesh.updateCounts();

    Material mat = AssetFactory.get().material(color);
    mat.setColor("Color", color);
    mat.getAdditionalRenderState().setLineWidth(LINE_WIDTH);

    lineGeometry = new Geometry("MissionTrajectory-" + missionName, mesh);
    lineGeometry.setMaterial(mat);
    nearOrbitsNode.attachChild(lineGeometry);
  }

  /**
   * Appends a spacecraft position to the circular buffer.
   *
   * @param posGcrf the spacecraft position in GCRF meters
   */
  public void addPosition(Vector3D posGcrf) {
    positions[head] = posGcrf;
    head = (head + 1) % MAX_POINTS;
    if (count < MAX_POINTS) {
      count++;
    }
  }

  /** Flushes the position buffer to the line mesh vertex buffer. */
  public void update() {
    if (count == 0) {
      return;
    }

    Mesh mesh = lineGeometry.getMesh();
    VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer fb = (FloatBuffer) vb.getData();
    fb.clear();

    int start = (count < MAX_POINTS) ? 0 : head;
    for (int i = 0; i < count; i++) {
      int idx = (start + i) % MAX_POINTS;
      Vector3D pos = positions[idx];
      Vector3D scaled = RenderTransform.scaleMetersToUnits(pos, renderContext);
      Vector3D jme = renderContext.axisConvention().icrfToJme(scaled);
      fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
    }
    fb.flip();

    mesh.updateCounts();
    mesh.updateBound();
    vb.setUpdateNeeded();
  }

  /**
   * Shows or hides the trajectory line geometry.
   *
   * @param visible {@code true} to show, {@code false} to hide
   */
  public void setVisible(boolean visible) {
    if (lineGeometry != null) {
      lineGeometry.setCullHint(
          visible
              ? com.jme3.scene.Spatial.CullHint.Inherit
              : com.jme3.scene.Spatial.CullHint.Always);
    }
  }

  /** Detaches the trajectory geometry from the scene. */
  public void cleanup() {
    if (lineGeometry != null) {
      lineGeometry.removeFromParent();
    }
  }
}
