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
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.ui.mission.MissionPhaseShading;
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
  private TrajectoryPolyline boundTrail;
  private ColorRGBA[] runColors;
  private PhaseNodeMarkers markers;

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
    mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(MAX_VERTICES * 4));
    mesh.updateBound();
    mesh.updateCounts();

    // White, because Unshaded multiplies Color by the vertex colour: keeping the mission colour
    // here would turn the buffer into a multiplier, and a burn has to brighten *above* the mission
    // colour, which a multiplier cannot do.
    Material mat = AssetFactory.get().material(ColorRGBA.White);
    mat.setBoolean("VertexColor", true);
    mat.getAdditionalRenderState().setLineWidth(LINE_WIDTH);

    // Keyed on the id, not the name: duplicate names must not produce colliding geometry names.
    lineGeometry = new Geometry("MissionTrajectory-" + missionId, mesh);
    lineGeometry.setMaterial(mat);
    nearOrbitsNode.attachChild(lineGeometry);

    markers = new PhaseNodeMarkers(renderContext);
    markers.initialize(nearOrbitsNode, missionId);
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
    if (markers != null) {
      markers.setVisible(visible);
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
   * <p><b>Vertices are written relative to the spacecraft, and the geometry carries the spacecraft's
   * position.</b> Writing them in absolute GCRF units put ~6778 in a {@code float} — one {@code ulp}
   * is already half a metre there — while the near frame translated the geometry by {@code −p} of
   * the same magnitude, so the GPU cancelled two large operands and what survived was the rounding:
   * about a metre, redrawn <em>differently</em> on every frame because {@code p} moves ~130 m per
   * frame. Seen from 500 m away that is some four pixels of shimmer, and it is why the line danced
   * around a spacecraft model that was itself rock steady (spec {@code
   * specs/graphics-effects/spacecraft-view-artefacts.md} §4).
   *
   * <p>Subtracting the tip in {@code double} first bounds a vertex's error by its distance to the
   * spacecraft instead of by its distance to the geocentre, which is the standard camera-relative
   * property: the error a vertex subtends at the camera stays bounded wherever the mission flies.
   * The translation put back on the geometry goes through {@link
   * JmeVectorAdapter#toJmeBodyRelativePosition}, the same path {@code SpacecraftPresenter} and
   * {@code FloatingOriginAppState} use, so {@code nearFrame(−p) + line(+p)} is exactly zero rather
   * than nearly zero. It costs nothing — the whole buffer was already rewritten every frame — and it
   * needs no branch per view mode: in {@code PLANET} view the near frame sits at the origin and the
   * geometry's own translation carries the line to its absolute position.
   *
   * @param trail the display polyline, already bounded to the vertex budget
   * @param upTo index of the last vertex to draw, from {@link TrajectoryPolyline#indexUpTo}
   * @param tip the interpolated position at the current instant, drawn as the final vertex and used
   *     as the origin the vertices are expressed against
   */
  public void update(TrajectoryPolyline trail, int upTo, Vector3D tip) {
    if (trail == null || trail.size() == 0) return;

    if (trail != boundTrail) {
      bindColors(trail);
      boundTrail = trail;
    }

    Mesh mesh = lineGeometry.getMesh();
    VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
    FloatBuffer fb = (FloatBuffer) vb.getData();
    fb.clear();

    int last = Math.min(upTo, trail.size() - 1);
    // Fall back to the last drawn sample when there is no tip: the origin has to be a point of the
    // line, not the geocentre, or the precision this whole method buys is given straight back.
    Vector3D origin = tip != null ? tip : trail.positionAt(last);

    for (int i = 0; i <= last; i++) {
      putVertex(fb, trail.positionAt(i).subtract(origin), renderContext);
    }
    if (tip != null) {
      putVertex(fb, Vector3D.ZERO, renderContext); // the tip is the origin
    }
    fb.flip();

    lineGeometry.setLocalTranslation(
        JmeVectorAdapter.toJmeBodyRelativePosition(origin, renderContext));

    mesh.updateCounts();
    mesh.updateBound();
    vb.setUpdateNeeded();

    // The line's own local translation, not a second computation of it: reusing the value that was
    // just set is what makes it impossible for the two geometries to disagree about the origin.
    markers.update(trail, runColors, last, origin, lineGeometry.getLocalTranslation());
  }

  /**
   * Writes the whole colour buffer for a newly bound trail.
   *
   * <p>Once per trail, not once per frame. The colours depend only on the polyline, which is
   * immutable and handed out as one shared instance ({@code MissionEphemeris.displayTrail()}), so an
   * identity check is both sufficient and cheap — and it re-fires exactly when it should, on the new
   * ephemeris a wizard edit produces.
   *
   * <p>Slot {@code size()} is filled too. It is the slot the interpolated tip occupies once the
   * spacecraft reaches the end of the trail, and leaving it black would put one dark vertex at the
   * head of a completed mission's line.
   *
   * <p><b>The tip's own colour is an approximation, deliberately.</b> The tip sits between vertices
   * {@code last} and {@code last + 1} and is drawn with the colour written at slot {@code last + 1},
   * which is the colour of the <em>next</em> sample. Those differ only across a phase boundary, for
   * at most one sampling step, over the final two pixels of the line. Tracking it exactly would mean
   * re-uploading the buffer whenever the head advances, which is the per-frame cost this design
   * exists to avoid.
   */
  private void bindColors(TrajectoryPolyline trail) {
    runColors = MissionPhaseShading.shade(color, trail.runs());
    VertexBuffer vb = lineGeometry.getMesh().getBuffer(VertexBuffer.Type.Color);
    FloatBuffer cb = (FloatBuffer) vb.getData();
    cb.clear();
    for (int i = 0; i < trail.size(); i++) {
      putColor(cb, runColors[trail.runOf(i)]);
    }
    putColor(cb, runColors[trail.runOf(trail.size() - 1)]);
    // Limit to capacity rather than to the written length: Mesh.updateCounts() derives the vertex
    // count from the Position buffer, and an attribute buffer only has to be at least that long.
    cb.position(cb.capacity());
    cb.flip();
    vb.setUpdateNeeded();
  }

  private static void putColor(FloatBuffer cb, ColorRGBA c) {
    cb.put(c.r).put(c.g).put(c.b).put(c.a);
  }

  /**
   * Converts one origin-relative offset into render units and appends it to the buffer.
   *
   * <p>Shared with {@link PhaseNodeMarkers}: a marker has to be produced by the very same conversion
   * as the line it sits on, or it drifts off the trace by the difference between the two.
   */
  static void putVertex(FloatBuffer fb, Vector3D offsetFromOrigin, RenderContext renderContext) {
    Vector3D scaled = RenderTransform.scaleMetersToUnits(offsetFromOrigin, renderContext);
    Vector3D jme = renderContext.axisConvention().icrfToJme(scaled);
    fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
  }

  /** Detaches the trajectory geometry from the scene. */
  public void cleanup() {
    if (lineGeometry != null) {
      lineGeometry.removeFromParent();
    }
    if (markers != null) {
      markers.cleanup();
    }
  }
}
