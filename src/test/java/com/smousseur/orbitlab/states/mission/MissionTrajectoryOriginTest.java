package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.engine.view.JmeVectorAdapter;
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
 * Precision of the drawn trajectory in spacecraft view (spec {@code
 * specs/graphics-effects/spacecraft-view-artefacts.md} §4, §9.2).
 *
 * <p>The line used to be written in absolute GCRF units — ~6778 in a {@code float}, where one
 * {@code ulp} is already half a metre — while its world matrix carried the near frame's {@code −p}
 * of the same magnitude. What survives that cancellation is the rounding of the vertex to the
 * {@code float} lattice, about a metre, redrawn <em>differently</em> every frame because {@code p}
 * moves: some four pixels of shimmer at a 500 m viewing distance. The spacecraft model does not
 * shake, because its own vertices are tiny and its world translation is exactly zero — the line was
 * the only object combining huge coordinates with a camera a few hundred metres away.
 *
 * <p>These tests pin the camera-relative form that removes it: vertices stored relative to the
 * spacecraft with the subtraction done in {@code double}, and the geometry carrying {@code +p}
 * through the same conversion the anchor uses, so it cancels the near frame's {@code −p} exactly —
 * the property {@code NearFrameOriginTest} pins for the spacecraft itself.
 *
 * <p>No JME application: {@link Node} and {@link Geometry} are pure math here, and the asset manager
 * only ever builds an unshaded material.
 */
class MissionTrajectoryOriginTest {

  /** A LEO position, ~6778 km from the geocentre — where {@code float} spacing is ~0.49 m. */
  private static final Vector3D LEO_TIP = new Vector3D(4_512_758.3, -3_981_204.7, 2_874_611.9);

  /**
   * A sample about 1.5 km from the tip — the scale at which the line is looked at in spacecraft
   * view. Deliberately not a round number: at LEO magnitudes the {@code float} lattice step is
   * exactly 2⁻¹¹ unit, so an offset of exactly 1 km falls on it and its quantization error cancels
   * to zero by luck. Real samples are not aligned on that lattice, and neither is this one.
   */
  private static final Vector3D OFF_LATTICE_NEIGHBOUR =
      LEO_TIP.add(new Vector3D(1_234.567, -890.123, 456.789));

  /** Sub-millimetre, in km units: what camera-relative vertices hold at this distance. */
  private static final float ONE_MILLIMETRE = 1e-6f;

  /** How far back from the tip the built trail reaches, in km units. */
  private static final float TRAIL_EXTENT_UNITS = 500f;

  private final RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);

  private Node nearFrame;
  private Node nearOrbitsNode;
  private MissionTrajectoryRenderer renderer;

  @BeforeAll
  static void initAssetFactory() {
    // The factory is a process-wide singleton owned by the application; in a test JVM the first
    // class to need it initializes it, and a later attempt must be a no-op rather than a failure.
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @BeforeEach
  void setUp() {
    // nearRoot → nearFrame → nearOrbitsNode → line, as assembled in SceneGraph and MissionRenderer.
    nearFrame = new Node("nearFrame");
    nearOrbitsNode = new Node("nearOrbitsNode");
    new Node("nearRoot").attachChild(nearFrame);
    nearFrame.attachChild(nearOrbitsNode);

    renderer = new MissionTrajectoryRenderer(MissionId.newId(), ctx, ColorRGBA.Cyan);
    renderer.initialize(nearOrbitsNode);
  }

  /** A trail reaching 500 km back, whose last two samples are the neighbour then the tip. */
  private static TrajectoryPolyline trail() {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    points.add(point(0, LEO_TIP.subtract(new Vector3D(500_000.0, 0, 0))));
    points.add(point(1, OFF_LATTICE_NEIGHBOUR));
    points.add(point(2, LEO_TIP));
    return new MissionEphemeris(points).displayTrail();
  }

  private static MissionEphemerisPoint point(int second, Vector3D position) {
    return new MissionEphemerisPoint(
        AbsoluteDate.J2000_EPOCH.shiftedBy((double) second),
        position,
        new Vector3D(0, 7_500, 0),
        "test",
        false,
        1_000.0,
        400_000.0);
  }

  /** What {@code FloatingOriginAppState.nearFrameOffset} writes on the near frame. */
  private void placeNearOriginOnSpacecraft(Vector3D positionGcrf) {
    nearFrame.setLocalTranslation(
        JmeVectorAdapter.toJmeBodyRelativePosition(positionGcrf, ctx).negateLocal());
  }

  private Geometry line() {
    return (Geometry) nearOrbitsNode.getChild(0);
  }

  /** Vertex {@code index} as stored in the mesh. */
  private Vector3f vertex(int index) {
    FloatBuffer fb = (FloatBuffer) line().getMesh().getBuffer(VertexBuffer.Type.Position).getData();
    return new Vector3f(fb.get(index * 3), fb.get(index * 3 + 1), fb.get(index * 3 + 2));
  }

  /** Where vertex {@code index} lands relative to the near-view origin, as the GPU computes it. */
  private Vector3f drawnPosition(int index) {
    return line().getWorldTranslation().add(vertex(index));
  }

  @Test
  void verticesAreStoredRelativeToTheSpacecraft() {
    TrajectoryPolyline trail = trail();

    renderer.update(trail, trail.size() - 1, LEO_TIP);

    // This is the whole fix in one assertion: no coordinate in the buffer may carry the geocentric
    // magnitude any more. It is what bounds a vertex's error by its distance to the spacecraft
    // instead of by its distance to the Earth's centre.
    for (int i = 0; i <= trail.size(); i++) {
      Vector3f v = vertex(i);
      int index = i;
      assertTrue(
          v.length() <= TRAIL_EXTENT_UNITS,
          () -> "vertex " + index + " is stored in absolute coordinates: " + v);
    }
  }

  @Test
  void aNearbyVertexIsDrawnToTheMillimetre() {
    TrajectoryPolyline trail = trail();
    placeNearOriginOnSpacecraft(LEO_TIP);

    renderer.update(trail, trail.size() - 1, LEO_TIP);

    Vector3f expected =
        JmeVectorAdapter.toVector3f(
            ctx.axisConvention()
                .icrfToJme(new Vector3D(1.234567, -0.890123, 0.456789))); // the offset, in km units
    float error = drawnPosition(1).subtract(expected).length();

    assertTrue(
        error < ONE_MILLIMETRE,
        () ->
            "a vertex 1.5 km from the spacecraft is misplaced by "
                + (error * 1000f)
                + " m; absolute vertices quantize to ~0.5 m at this magnitude, and the error is"
                + " redrawn differently every frame — that is the shimmer");
  }

  @Test
  void theTipVertexLandsExactlyOnTheSpacecraft() {
    TrajectoryPolyline trail = trail();
    placeNearOriginOnSpacecraft(LEO_TIP);

    renderer.update(trail, trail.size() - 1, LEO_TIP);

    // The tip is the spacecraft's own position, so the line must end on the near origin exactly, or
    // it detaches from the vehicle it is drawn for. This holds only as long as the geometry's
    // translation is produced by the very same conversion as the near-frame offset it must cancel.
    Vector3f drawn = drawnPosition(trail.size()); // the interpolated tip, appended after the trail
    assertEquals(0f, drawn.x, "exact cancellation, no tolerance");
    assertEquals(0f, drawn.y, "exact cancellation, no tolerance");
    assertEquals(0f, drawn.z, "exact cancellation, no tolerance");
  }
}
