package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import com.smousseur.orbitlab.ui.mission.MissionPhaseShading;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * Per-vertex phase colours on the drawn trajectory (spec {@code
 * specs/graphics-effects/mission-phase-encoding.md} §5.3).
 *
 * <p>No JME application: {@link Node} and {@link Geometry} are pure math, and the asset manager
 * only ever builds an unshaded material — the same arrangement {@link MissionTrajectoryOriginTest}
 * uses.
 */
class MissionTrajectoryColorTest {

  private static final AbsoluteDate T0 = AbsoluteDate.J2000_EPOCH;
  private static final ColorRGBA MISSION = new ColorRGBA(0.30f, 0.65f, 0.90f, 1f);

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

  /** Three burn samples then three coast samples, a kilometre apart along +X. */
  private static TrajectoryPolyline trail() {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      points.add(
          new MissionEphemerisPoint(
              T0.shiftedBy((double) i),
              new Vector3D(7_000_000.0 + i * 1_000.0, 0, 0),
              new Vector3D(0, 7_500, 0),
              i < 3 ? "Ascent" : "Coasting",
              i < 3,
              1_000.0,
              400_000.0));
    }
    return new MissionEphemeris(points).displayTrail();
  }

  private Geometry line() {
    return (Geometry) nearOrbitsNode.getChild(0);
  }

  private ColorRGBA vertexColor(int index) {
    FloatBuffer cb = (FloatBuffer) line().getMesh().getBuffer(VertexBuffer.Type.Color).getData();
    return new ColorRGBA(
        cb.get(index * 4), cb.get(index * 4 + 1), cb.get(index * 4 + 2), cb.get(index * 4 + 3));
  }

  @Test
  void theMaterialMultipliesByWhiteSoTheBufferCarriesAbsoluteColours() {
    ColorRGBA base = (ColorRGBA) line().getMaterial().getParam("Color").getValue();

    assertEquals(1f, base.r, "Color must be white or the buffer becomes a multiplier");
    assertEquals(1f, base.g);
    assertEquals(1f, base.b);
    assertEquals(
        Boolean.TRUE,
        line().getMaterial().getParam("VertexColor").getValue(),
        "vertex colours are inert unless the define is on");
  }

  @Test
  void eachVertexCarriesTheColourOfItsRun() {
    TrajectoryPolyline trail = trail();
    ColorRGBA[] expected = MissionPhaseShading.shade(MISSION, trail.runs());

    renderer.update(trail, trail.size() - 1, new Vector3D(7_005_000.0, 0, 0));

    for (int i = 0; i < trail.size(); i++) {
      ColorRGBA want = expected[trail.runOf(i)];
      int index = i;
      assertEquals(want.r, vertexColor(index).r, 1e-6f, () -> "vertex " + index + " red");
      assertEquals(want.g, vertexColor(index).g, 1e-6f, () -> "vertex " + index + " green");
      assertEquals(want.b, vertexColor(index).b, 1e-6f, () -> "vertex " + index + " blue");
    }
  }

  @Test
  void theBurnPrefixIsBrighterThanTheCoastSuffix() {
    TrajectoryPolyline trail = trail();

    renderer.update(trail, trail.size() - 1, new Vector3D(7_005_000.0, 0, 0));

    assertTrue(
        vertexColor(0).r > vertexColor(5).r,
        "the ascent must read hotter than the coast that follows it");
    assertNotEquals(vertexColor(0).r, vertexColor(5).r, "the phases must not share a colour");
  }

  /**
   * The buffer is written once per trail, not per frame — that is what keeps the per-frame cost at
   * what it was before phases were coloured at all.
   */
  @Test
  void reboundingTheSameTrailDoesNotRewriteTheBuffer() {
    TrajectoryPolyline trail = trail();
    Vector3D tip = new Vector3D(7_005_000.0, 0, 0);

    renderer.update(trail, trail.size() - 1, tip);
    VertexBuffer colors = line().getMesh().getBuffer(VertexBuffer.Type.Color);
    colors.clearUpdateNeeded();

    renderer.update(trail, trail.size() - 1, tip);

    assertFalse(
        colors.isUpdateNeeded(),
        "the colour buffer must not be re-uploaded while the trail is unchanged");
  }
}
