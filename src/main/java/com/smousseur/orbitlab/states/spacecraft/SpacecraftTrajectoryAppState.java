package com.smousseur.orbitlab.states.spacecraft;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.engine.AssetFactory;
import java.nio.FloatBuffer;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Application state that renders the spacecraft's trajectory as a line strip in the near orbits
 * node. Maintains a circular buffer of GCRF positions and periodically updates the line geometry.
 */
public final class SpacecraftTrajectoryAppState extends BaseAppState {

  private static final int MAX_POINTS = 4096;
  private static final float LINE_WIDTH = 2f;

  private final ApplicationContext context;
  private final RenderContext renderContext;
  private final ColorRGBA color;

  private final Vector3D[] positions = new Vector3D[MAX_POINTS];
  private int head = 0;
  private int count = 0;

  private Geometry lineGeometry;
  private Node nearOrbitsNode;

  /**
   * Creates a new trajectory state.
   *
   * @param context the application context
   * @param renderContext the render context for coordinate scaling
   * @param color the color of the trajectory line
   */
  public SpacecraftTrajectoryAppState(
      ApplicationContext context, RenderContext renderContext, ColorRGBA color) {
    this.context = Objects.requireNonNull(context, "context");
    this.renderContext = Objects.requireNonNull(renderContext, "renderContext");
    this.color = Objects.requireNonNull(color, "color");
  }

  /**
   * Adds a new position to the trajectory buffer. Called by {@link SpacecraftDisplayAppState} each
   * frame.
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

  @Override
  protected void initialize(Application app) {
    nearOrbitsNode = context.sceneGraph().nearOrbitsNode();

    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.LineStrip);
    FloatBuffer pb = BufferUtils.createFloatBuffer(MAX_POINTS * 3);
    mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
    mesh.updateBound();
    mesh.updateCounts();

    Material mat = AssetFactory.get().material(color);
    mat.setColor("Color", color);
    mat.getAdditionalRenderState().setLineWidth(LINE_WIDTH);

    lineGeometry = new Geometry("SpacecraftTrajectory", mesh);
    lineGeometry.setMaterial(mat);
    nearOrbitsNode.attachChild(lineGeometry);
  }

  @Override
  public void update(float tpf) {
    if (count == 0) {
      return;
    }
    updateLineGeometry();
  }

  private void updateLineGeometry() {
    Mesh mesh = lineGeometry.getMesh();
    VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer fb = (FloatBuffer) vb.getData();
    fb.clear();

    int start = (count < MAX_POINTS) ? 0 : head;
    for (int i = 0; i < count; i++) {
      int idx = (start + i) % MAX_POINTS;
      Vector3D pos = positions[idx];
      // GCRF positions are already geocentric — only scale + axis conversion needed
      Vector3D scaled = RenderTransform.scaleMetersToUnits(pos, renderContext);
      Vector3D jme = renderContext.axisConvention().icrfToJme(scaled);
      fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
    }
    fb.flip();

    mesh.updateCounts();
    mesh.updateBound();
    vb.setUpdateNeeded();
  }

  @Override
  protected void cleanup(Application app) {
    if (lineGeometry != null) {
      lineGeometry.removeFromParent();
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
