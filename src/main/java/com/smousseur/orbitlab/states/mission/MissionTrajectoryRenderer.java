package com.smousseur.orbitlab.states.mission;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.engine.AssetFactory;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Renders a mission's trajectory as a line strip. Receives complete position lists from
 * pre-computed ephemeris and flushes them to a JME mesh each frame. This is a plain object managed
 * by {@link MissionRenderer}, not an AppState.
 */
public final class MissionTrajectoryRenderer {

  private static final int MAX_POINTS = 8192;
  private static final float LINE_WIDTH = 2f;

  private final String missionName;
  private final RenderContext renderContext;
  private final ColorRGBA color;

  private List<Vector3D> currentPositions;
  private Geometry lineGeometry;

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
   * Replaces the entire trajectory with the given positions.
   *
   * @param positionsGcrf the positions in GCRF meters
   */
  public void setPositions(List<Vector3D> positionsGcrf) {
    this.currentPositions = positionsGcrf;
  }

  /**
   * Shows/hides the trajectory line.
   *
   * @param visible whether to show or hide
   */
  public void setVisible(boolean visible) {
    if (lineGeometry != null) {
      lineGeometry.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }
  }

  /** Flushes the current positions to the mesh vertex buffer. */
  public void update() {
    if (currentPositions == null || currentPositions.isEmpty()) return;

    Mesh mesh = lineGeometry.getMesh();
    VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer fb = (FloatBuffer) vb.getData();
    fb.clear();

    int count = Math.min(currentPositions.size(), MAX_POINTS);
    for (int i = 0; i < count; i++) {
      Vector3D pos = currentPositions.get(i);
      Vector3D scaled = RenderTransform.scaleMetersToUnits(pos, renderContext);
      Vector3D jme = renderContext.axisConvention().icrfToJme(scaled);
      fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
    }
    fb.flip();

    mesh.updateCounts();
    mesh.updateBound();
    vb.setUpdateNeeded();
  }

  /** Detaches the trajectory geometry from the scene. */
  public void cleanup() {
    if (lineGeometry != null) {
      lineGeometry.removeFromParent();
    }
  }
}
