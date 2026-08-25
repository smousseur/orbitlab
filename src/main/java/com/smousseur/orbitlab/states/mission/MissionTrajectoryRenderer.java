package com.smousseur.orbitlab.states.mission;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.app.view.RenderTransform;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.scene.RibbonMeshBuilder;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.ui.mission.MissionPhaseShading;
import java.nio.FloatBuffer;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Renders a mission's trajectory as a camera-facing ribbon. Receives the pre-computed {@link
 * TrajectoryPolyline} and a prefix bound, and flushes that prefix to a JME mesh each frame. This is
 * a plain object managed by {@link MissionRenderer}, not an AppState.
 *
 * <p>The primitive is a {@code TriangleStrip} expanded by {@code MatDefs/Fx/Ribbon.j3md} rather
 * than a {@code LineStrip} (spec {@code docs/graphics-effects/ribbon-lines.md}). The architecture
 * below is unchanged — one allocation, a prefix written each frame, an identity guard on the
 * polyline for the colours — and what changes is that two vertices are written per point instead of
 * one, with a tangent, and that the width finally means something: {@code glLineWidth(2)} was
 * clamped back to 1 px by the core profile, which also made the per-run shading of {@code RND-3}
 * nearly unreadable on a trace one pixel wide.
 */
public final class MissionTrajectoryRenderer {

  /**
   * Point capacity: the polyline's budget plus one slot for the interpolated tip at {@code now}.
   * Sized from {@link TrajectoryPolyline#MAX_POINTS} rather than from a constant of its own, so the
   * producer and the buffer cannot drift apart.
   */
  private static final int MAX_POINTS = TrajectoryPolyline.MAX_POINTS + 1;

  /**
   * Width of a mission trace, in screen pixels (§7.4). Wider than a planetary orbit: it is the
   * object of attention, it carries the phase colours, and it is drawn against a planet rather than
   * against the sky.
   */
  private static final float TRAJECTORY_WIDTH_PX = 1.5f;

  private final MissionId missionId;
  private final ColorRGBA color;

  /**
   * Staging for one frame's points, in render units, before the builder expands them into pairs.
   *
   * <p>It exists because a tangent needs its two neighbours: the conversion can no longer stream
   * straight into the vertex buffer one point at a time. Allocated once — the whole point of this
   * class is that the per-frame path allocates nothing but the {@code Vector3D} the conversion
   * already produced.
   */
  private final float[] points = new float[MAX_POINTS * 3];

  private Geometry lineGeometry;
  private TrajectoryPolyline boundTrail;
  private ColorRGBA[] runColors;
  private PhaseNodeMarkers markers;

  public MissionTrajectoryRenderer(MissionId missionId, ColorRGBA color) {
    this.missionId = Objects.requireNonNull(missionId, "missionId");
    this.color = Objects.requireNonNull(color, "color");
  }

  /**
   * Creates the ribbon mesh, material, and geometry, and attaches them to the given node.
   *
   * @param nearOrbitsNode the scene node for near-viewport orbit lines
   */
  public void initialize(Node nearOrbitsNode) {
    Mesh mesh = RibbonMeshBuilder.allocate(MAX_POINTS, false, true);

    // White, because the ribbon shader multiplies Color by the vertex colour just as Unshaded did:
    // keeping the mission colour here would turn the buffer into a multiplier, and a burn has to
    // brighten *above* the mission colour, which a multiplier cannot do.
    Material mat = AssetFactory.get().createRibbon(ColorRGBA.White, TRAJECTORY_WIDTH_PX, true);

    // Keyed on the id, not the name: duplicate names must not produce colliding geometry names.
    lineGeometry = new Geometry("MissionTrajectory-" + missionId, mesh);
    lineGeometry.setMaterial(mat);
    // The edge fade is an alpha ramp, so the trace belongs in the transparent bucket — where it is
    // still depth-tested, and therefore still disappears behind the central body.
    lineGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
    nearOrbitsNode.attachChild(lineGeometry);

    markers = new PhaseNodeMarkers();
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
   * <p><b>Vertices are written relative to the spacecraft, and the geometry carries the
   * spacecraft's position.</b> Writing them in absolute GCRF units put ~6778 in a {@code float} —
   * one {@code ulp} is already half a metre there — while the near frame translated the geometry by
   * {@code −p} of the same magnitude, so the GPU cancelled two large operands and what survived was
   * the rounding: about a metre, redrawn <em>differently</em> on every frame because {@code p}
   * moves ~130 m per frame. Seen from 500 m away that is some four pixels of shimmer, and it is why
   * the line danced around a spacecraft model that was itself rock steady (spec {@code
   * docs/graphics-effects/spacecraft-view-artefacts.md} §4).
   *
   * <p>Subtracting the tip in {@code double} first bounds a vertex's error by its distance to the
   * spacecraft instead of by its distance to the geocentre, which is the standard camera-relative
   * property: the error a vertex subtends at the camera stays bounded wherever the mission flies.
   * The translation put back on the geometry goes through {@link
   * JmeVectorAdapter#toJmeBodyRelativePosition}, the same path {@code SpacecraftPresenter} and
   * {@code FloatingOriginAppState} use, so {@code nearFrame(−p) + line(+p)} is exactly zero rather
   * than nearly zero. It costs nothing — the whole buffer was already rewritten every frame — and
   * it needs no branch per view mode: in {@code PLANET} view the near frame sits at the origin and
   * the geometry's own translation carries the line to its absolute position.
   *
   * @param trail the display polyline, already bounded to the vertex budget
   * @param upTo index of the last vertex to draw, from {@link TrajectoryPolyline#indexUpTo}
   * @param tip the interpolated position at the current instant, drawn as the final vertex and used
   *     as the origin the vertices are expressed against
   * @param renderContext the context of the sample being drawn, derived from its arc by {@code
   *     MissionRenderer.renderContextFor} — a parameter and no longer a field of this class, so
   *     that the line and the near-frame offset cannot be built from two different contexts (spec
   *     {@code docs/multi-corps/05-conception-L3.md} §3.2)
   */
  public void update(
      TrajectoryPolyline trail, int upTo, Vector3D tip, RenderContext renderContext) {
    if (trail == null || trail.size() == 0) return;

    if (trail != boundTrail) {
      bindColors(trail);
      boundTrail = trail;
    }

    // The body every coordinate of this frame is expressed about — the vertices, the origin they
    // are subtracted from, and the translation the geometry carries. Derived once and passed down,
    // so nothing below can read a different table than the one the origin came from.
    SolarSystemBody renderBody =
        renderContext
            .targetBody()
            .orElseThrow(() -> new IllegalStateException("a mission is drawn at planet scale"));

    int last = Math.min(upTo, trail.size() - 1);
    // Fall back to the last drawn sample when there is no tip: the origin has to be a point of the
    // line, not the geocentre, or the precision this whole method buys is given straight back.
    Vector3D origin = tip != null ? tip : trail.positionAt(last, renderBody);

    int count = 0;
    for (int i = 0; i <= last; i++) {
      putPoint(points, count++, trail.positionAt(i, renderBody).subtract(origin), renderContext);
    }
    if (tip != null) {
      putPoint(points, count++, Vector3D.ZERO, renderContext); // the tip is the origin
    }
    if (count == 1) {
      // One point is not a ribbon. Repeating it gives a band of zero area — nothing rasterises,
      // which is what a single-vertex LineStrip did too, and the builder's degenerate-tangent
      // guard keeps it free of NaN.
      System.arraycopy(points, 0, points, 3, 3);
      count = 2;
    }

    Mesh mesh = lineGeometry.getMesh();
    RibbonMeshBuilder.write(mesh, points, count, false);

    lineGeometry.setLocalTranslation(
        JmeVectorAdapter.toJmeBodyRelativePosition(origin, renderContext));

    // The line's own local translation, not a second computation of it: reusing the value that was
    // just set is what makes it impossible for the two geometries to disagree about the origin.
    markers.update(
        trail,
        runColors,
        last,
        origin,
        lineGeometry.getLocalTranslation(),
        renderContext,
        renderBody);
  }

  /**
   * Writes the whole colour buffer for a newly bound trail.
   *
   * <p>Once per trail, not once per frame. The colours depend only on the polyline, which is
   * immutable and handed out as one shared instance ({@code MissionEphemeris.displayTrail()}), so
   * an identity check is both sufficient and cheap — and it re-fires exactly when it should, on the
   * new ephemeris a wizard edit produces.
   *
   * <p>Slot {@code size()} is filled too. It is the slot the interpolated tip occupies once the
   * spacecraft reaches the end of the trail, and leaving it black would put one dark vertex at the
   * head of a completed mission's line. Each colour lands in the buffer twice, once per edge of the
   * ribbon, which {@link RibbonMeshBuilder#writeColors} does; the tail of the buffer is padded with
   * the last colour rather than left at zero, for the same reason.
   *
   * <p><b>The tip's own colour is an approximation, deliberately.</b> The tip sits between vertices
   * {@code last} and {@code last + 1} and is drawn with the colour written at slot {@code last +
   * 1}, which is the colour of the <em>next</em> sample. Those differ only across a phase boundary,
   * for at most one sampling step, over the final two pixels of the line. Tracking it exactly would
   * mean re-uploading the buffer whenever the head advances, which is the per-frame cost this
   * design exists to avoid.
   */
  private void bindColors(TrajectoryPolyline trail) {
    runColors = MissionPhaseShading.shade(color, trail.runs());
    int count = trail.size() + 1;
    float[] rgba = new float[count * 4];
    for (int i = 0; i < trail.size(); i++) {
      putColor(rgba, i, runColors[trail.runOf(i)]);
    }
    putColor(rgba, trail.size(), runColors[trail.runOf(trail.size() - 1)]);
    RibbonMeshBuilder.writeColors(lineGeometry.getMesh(), rgba, count);
  }

  private static void putColor(float[] rgba, int i, ColorRGBA c) {
    rgba[i * 4] = c.r;
    rgba[i * 4 + 1] = c.g;
    rgba[i * 4 + 2] = c.b;
    rgba[i * 4 + 3] = c.a;
  }

  /**
   * Converts one origin-relative offset into render units and stores it at slot {@code i}.
   *
   * <p>Shares its conversion with {@link PhaseNodeMarkers} through {@link #toRenderUnits}: a marker
   * has to be produced by the very same arithmetic as the ribbon it sits on, or it drifts off the
   * trace by the difference between the two.
   */
  private static void putPoint(
      float[] xyz, int i, Vector3D offsetFromOrigin, RenderContext renderContext) {
    Vector3D jme = toRenderUnits(offsetFromOrigin, renderContext);
    xyz[i * 3] = (float) jme.getX();
    xyz[i * 3 + 1] = (float) jme.getY();
    xyz[i * 3 + 2] = (float) jme.getZ();
  }

  /** Converts one origin-relative offset into render units and appends it to the buffer. */
  static void putVertex(FloatBuffer fb, Vector3D offsetFromOrigin, RenderContext renderContext) {
    Vector3D jme = toRenderUnits(offsetFromOrigin, renderContext);
    fb.put((float) jme.getX()).put((float) jme.getY()).put((float) jme.getZ());
  }

  private static Vector3D toRenderUnits(Vector3D offsetFromOrigin, RenderContext renderContext) {
    Vector3D scaled = RenderTransform.scaleMetersToUnits(offsetFromOrigin, renderContext);
    return renderContext.axisConvention().icrfToJme(scaled);
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
