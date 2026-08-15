package com.smousseur.orbitlab.states.mission;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.ephemeris.PhaseRun;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * One point per phase transition of a mission, drawn on the trajectory line.
 *
 * <p><b>Why points in the 3D node and not GUI billboards.</b> A trajectory node passes behind the
 * central body constantly — it is the nominal case, not an edge case. A Lemur billboard lives in
 * the GUI overlay and is not depth-tested against the scene, so it would draw over the planet while
 * the line it belongs to correctly disappears behind it. A {@code Points} geometry attached beside
 * the line inherits the depth test for free. The load profile agrees: {@code BillboardIconView}
 * serves one icon per body, a dozen in all, where this is a dozen markers <em>per mission</em>.
 *
 * <p>Point size is honoured without a custom shader: {@code Unshaded.j3md} declares {@code
 * PointSize}, {@code Unshaded.vert} writes {@code gl_PointSize} from it, and JME's {@code
 * GLRenderer} enables {@code GL_VERTEX_PROGRAM_POINT_SIZE} unconditionally on desktop. Unlike
 * {@code glLineWidth}, this is not driver-dependent. The cost is that a point is a flat square:
 * {@code PointSprite} no longer exists in the 3.9 {@code Unshaded}, so there is no texture and no
 * rounded, antialiased dot.
 *
 * <p>Owned by {@link MissionTrajectoryRenderer}, which passes it the origin it has already computed
 * — the marker must be produced by the same conversion as the line, not by a second one.
 */
final class PhaseNodeMarkers {

  /** Screen pixels. Large enough to read against the 3,5 px ribbon, small enough not to hide it. */
  private static final float POINT_SIZE = 7f;

  private final RenderContext renderContext;

  private Geometry geometry;
  private TrajectoryPolyline boundTrail;

  PhaseNodeMarkers(RenderContext renderContext) {
    this.renderContext = Objects.requireNonNull(renderContext, "renderContext");
  }

  /**
   * Creates the point mesh and attaches it beside the trajectory line.
   *
   * @param parent the scene node for near-viewport orbit lines
   * @param missionId the owning mission, for the geometry name
   */
  void initialize(Node parent, MissionId missionId) {
    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.Points);

    Material mat = AssetFactory.get().material(ColorRGBA.White);
    mat.setBoolean("VertexColor", true);
    mat.setFloat("PointSize", POINT_SIZE);

    // Keyed on the id, not the name, for the same reason as the line geometry: duplicate mission
    // names must not produce colliding spatial names.
    geometry = new Geometry("MissionPhaseNodes-" + missionId, mesh);
    geometry.setMaterial(mat);
    parent.attachChild(geometry);
  }

  /**
   * Redraws the markers for the transitions reached at the current instant.
   *
   * @param trail the mission's display polyline
   * @param runColors one colour per run, from {@code MissionPhaseShading}
   * @param upTo index of the last vertex flown, from {@link TrajectoryPolyline#indexUpTo}
   * @param origin the position the line's vertices are expressed against, in GCRF meters
   * @param translation the local translation the line carries, so both land in the same place
   */
  void update(
      TrajectoryPolyline trail,
      ColorRGBA[] runColors,
      int upTo,
      Vector3D origin,
      Vector3f translation) {
    List<PhaseRun> runs = trail.runs();
    if (trail != boundTrail) {
      allocate(runs.size());
      boundTrail = trail;
    }

    Mesh mesh = geometry.getMesh();
    VertexBuffer pv = mesh.getBuffer(VertexBuffer.Type.Position);
    VertexBuffer cv = mesh.getBuffer(VertexBuffer.Type.Color);
    FloatBuffer pb = (FloatBuffer) pv.getData();
    FloatBuffer cb = (FloatBuffer) cv.getData();
    pb.clear();
    cb.clear();

    for (int r = 0; r < runs.size(); r++) {
      int vertex = runs.get(r).firstVertex();
      // Runs are ordered by firstVertex, so the first unreached one ends the drawn set. A marker
      // for
      // a phase the spacecraft has not started would contradict the trail semantics of the line.
      if (vertex > upTo) {
        break;
      }
      MissionTrajectoryRenderer.putVertex(
          pb, trail.positionAt(vertex).subtract(origin), renderContext);
      ColorRGBA c = runColors[r];
      cb.put(c.r).put(c.g).put(c.b).put(c.a);
    }
    pb.flip();
    cb.flip();

    geometry.setLocalTranslation(translation);
    mesh.updateCounts();
    mesh.updateBound();
    pv.setUpdateNeeded();
    cv.setUpdateNeeded();
  }

  /**
   * Sizes the buffers to the run count; the bind is rare, so this is not a per-frame allocation.
   */
  private void allocate(int runCount) {
    Mesh mesh = geometry.getMesh();
    mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(runCount * 3));
    mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(runCount * 4));
  }

  /**
   * Shows/hides the markers.
   *
   * @param visible whether to show or hide
   */
  void setVisible(boolean visible) {
    if (geometry != null) {
      geometry.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }
  }

  /** Detaches the marker geometry from the scene. */
  void cleanup() {
    if (geometry != null) {
      geometry.removeFromParent();
    }
  }
}
