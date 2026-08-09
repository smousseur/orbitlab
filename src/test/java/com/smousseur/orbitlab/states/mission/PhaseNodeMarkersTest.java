package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * Transition markers (spec {@code specs/graphics-effects/mission-phase-encoding.md} §5.4).
 *
 * <p>The load-bearing rule is that a marker only appears once the spacecraft has reached it. The
 * line is a trail that grows behind the vehicle; a marker announcing a phase that has not started
 * would contradict it.
 */
class PhaseNodeMarkersTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;
  private static final ColorRGBA MISSION = new ColorRGBA(0.30f, 0.65f, 0.90f, 1f);
  private static final Vector3D BASE = new Vector3D(7_000_000.0, 0, 0);

  private final RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);

  private Node nearOrbitsNode;
  private MissionTrajectoryRenderer renderer;

  @BeforeAll
  static void initAssetFactory() {
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @BeforeEach
  void setUp() {
    nearOrbitsNode = new Node("nearOrbitsNode");
    renderer = new MissionTrajectoryRenderer(MissionId.newId(), ctx, MISSION);
    renderer.initialize(nearOrbitsNode);
  }

  /** Runs of 3, 3 and 3 samples: vertices 0, 3 and 6 are transitions. */
  private static TrajectoryPolyline trail() {
    String[] names = {
      "Ascent", "Ascent", "Ascent", "Coast", "Coast", "Coast", "Burn", "Burn", "Burn"
    };
    boolean[] burns = {true, true, true, false, false, false, true, true, true};
    List<MissionEphemerisPoint> points = new ArrayList<>();
    for (int i = 0; i < names.length; i++) {
      points.add(
          new MissionEphemerisPoint(
              T0.shiftedBy((double) i),
              BASE.add(new Vector3D(i * 1_000.0, 0, 0)),
              new Vector3D(0, 7_500, 0),
              names[i],
              burns[i],
              1_000.0,
              400_000.0));
    }
    return new MissionEphemeris(points).displayTrail();
  }

  private Geometry markers() {
    return (Geometry) nearOrbitsNode.getChild(1);
  }

  private Vector3f markerVertex(int index) {
    FloatBuffer fb =
        (FloatBuffer) markers().getMesh().getBuffer(VertexBuffer.Type.Position).getData();
    return new Vector3f(fb.get(index * 3), fb.get(index * 3 + 1), fb.get(index * 3 + 2));
  }

  @Test
  void theMarkerGeometryIsAPointMeshWithASetPointSize() {
    assertEquals(Mesh.Mode.Points, markers().getMesh().getMode());
    float size = (Float) markers().getMaterial().getParam("PointSize").getValue();
    assertTrue(size > 1f, "a 1 px marker is indistinguishable from the line");
  }

  @Test
  void onlyReachedTransitionsAreDrawn() {
    TrajectoryPolyline trail = trail();

    renderer.update(trail, 0, trail.positionAt(0));
    assertEquals(1, markers().getMesh().getVertexCount(), "only the launch node has been reached");

    renderer.update(trail, 4, trail.positionAt(4));
    assertEquals(2, markers().getMesh().getVertexCount(), "the second run has started");

    renderer.update(trail, 8, trail.positionAt(8));
    assertEquals(3, markers().getMesh().getVertexCount(), "all three runs have started");
  }

  @Test
  void aMarkerSitsExactlyOnItsVertexOfTheLine() {
    TrajectoryPolyline trail = trail();
    Vector3D tip = trail.positionAt(8);

    renderer.update(trail, 8, tip);

    Geometry line = (Geometry) nearOrbitsNode.getChild(0);
    FloatBuffer lb = (FloatBuffer) line.getMesh().getBuffer(VertexBuffer.Type.Position).getData();
    // Marker 1 is the transition at line vertex 3.
    Vector3f lineVertex = new Vector3f(lb.get(3 * 3), lb.get(3 * 3 + 1), lb.get(3 * 3 + 2));

    assertEquals(lineVertex, markerVertex(1), "one conversion path, or the marker drifts off");
    assertEquals(
        line.getLocalTranslation(),
        markers().getLocalTranslation(),
        "both geometries must carry the same origin");
  }

  @Test
  void markersDisappearWithTheTrajectory() {
    renderer.setVisible(false);
    assertEquals(Spatial.CullHint.Always, markers().getLocalCullHint());

    renderer.setVisible(true);
    assertEquals(Spatial.CullHint.Inherit, markers().getLocalCullHint());
  }
}
