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
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.nio.FloatBuffer;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Renders a mission's trajectory as a line strip. Receives the pre-computed {@link
 * TrajectoryPolyline} and a prefix bound, and flushes that prefix to a JME mesh each frame. This is
 * a plain object managed by {@link MissionRenderer}, not an AppState.
 */
public final class MissionTrajectoryRenderer {

  /**
   * Vertex capacity: the polyline's budget plus one slot for the interpolated tip at {@code now}.
   * Sized from {@link TrajectoryPolyline#MAX_POINTS} rather than from a constant of its own, so the
   * producer and the buffer cannot drift apart.
   */
  private static final int MAX_VERTICES = TrajectoryPolyline.MAX_POINTS + 1;

  private static final float LINE_WIDTH = 2f;

  private final MissionId missionId;
  private final RenderContext renderContext;
  private final ColorRGBA color;

  private Geometry lineGeometry;

  public MissionTrajectoryRenderer(
      MissionId missionId, RenderContext renderContext, ColorRGBA color) {
    this.missionId = Objects.requireNonNull(missionId, "missionId");
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
    FloatBuffer pb = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
    mesh.updateBound();
    mesh.updateCounts();

    Material mat = AssetFactory.get().material(color);
    mat.setColor("Color", color);
    mat.getAdditionalRenderState().setLineWidth(LINE_WIDTH);

    // Keyed on the id, not the name: duplicate names must not produce colliding geometry names.
    lineGeometry = new Geometry("MissionTrajectory-" + missionId, mesh);
    lineGeometry.setMaterial(mat);
    nearOrbitsNode.attachChild(lineGeometry);
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

  /**
   * Flushes the flown prefix of {@code trail} to the mesh vertex buffer.
   *
   * <p>Walks <b>forward</b> from the first vertex — the trajectory is drawn from launch to the
   * current instant, which is what makes {@code upTo} a prefix bound at all. The previous
   * implementation walked backwards from the end and stopped after the budget, so on a mission
   * longer than the budget the ascent silently disappeared from the line.
   *
   * @param trail the display polyline, already bounded to the vertex budget
   * @param upTo index of the last vertex to draw, from {@link TrajectoryPolyline#indexUpTo}
   * @param tip the interpolated position at the current instant, drawn as the final vertex
   */
  public void update(TrajectoryPolyline trail, int upTo, Vector3D tip) {
    if (trail == null || trail.size() == 0) return;

    Mesh mesh = lineGeometry.getMesh();
    VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer fb = (FloatBuffer) vb.getData();
    fb.clear();

    int last = Math.min(upTo, trail.size() - 1);
    for (int i = 0; i <= last; i++) {
      putVertex(fb, trail.positionAt(i));
    }
    if (tip != null) {
      putVertex(fb, tip);
    }
    fb.flip();

    mesh.updateCounts();
    mesh.updateBound();
    vb.setUpdateNeeded();
  }

  /** Converts one GCRF position into render units and appends it to the vertex buffer. */
  private void putVertex(FloatBuffer fb, Vector3D positionGcrf) {
    Vector3D scaled = RenderTransform.scaleMetersToUnits(positionGcrf, renderContext);
    Vector3D jme = renderContext.axisConvention().icrfToJme(scaled);
    fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
  }

  /** Detaches the trajectory geometry from the scene. */
  public void cleanup() {
    if (lineGeometry != null) {
      lineGeometry.removeFromParent();
    }
  }
}
