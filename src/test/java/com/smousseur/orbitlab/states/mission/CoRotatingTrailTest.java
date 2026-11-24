package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.AssetFactory;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The low part of a trail drawn as seen from the ground while its object is in the air: each low
 * vertex carried, with the Earth, from its own date to the current one.
 *
 * <p>Vertices are built fixed in the terrestrial frame over Kourou, so where the Earth has carried
 * them by any date is known by construction: the same terrestrial point, expressed at that date.
 */
class CoRotatingTrailTest {

  private static final double EARTH_RADIUS_M = 6_378_137.0;
  private static final double TOLERANCE_M = 1e-6;
  private static final double LATITUDE = FastMath.toRadians(5.23);
  private static final double LONGITUDE = FastMath.toRadians(-52.77);

  /** Ten minutes after the vertices were flown: the Earth has turned 2.5°, 280 km at the pad. */
  private static final double LATER_S = 600.0;

  private static AbsoluteDate epoch;
  private static Frame itrf;
  private static Frame gcrf;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    itrf = OrekitService.get().itrf();
    gcrf = FramesFactory.getGCRF();
    try {
      AssetFactory.init(new DesktopAssetManager(true));
    } catch (OrbitlabException alreadyInitialized) {
      // Another test class got there first: its asset manager serves just as well.
    }
  }

  @Test
  void whileTheObjectIsLowALowVertexIsDrawnWhereTheEarthHasCarriedIt() {
    TrajectoryPolyline trail = groundTrail(10_000.0, 10_000.0);
    AbsoluteDate now = epoch.shiftedBy(LATER_S);

    CoRotatingTrail.Frame frame = new CoRotatingTrail(trail).at(now, 1.0);

    Vector3D drawn = trail.positionAt(0, SolarSystemBody.EARTH).add(frame.displacementAt(0));
    assertEquals(0.0, drawn.distance(terrestrialAt(10_000.0, now)), TOLERANCE_M);
  }

  @Test
  void anObjectAboveTheRampLeavesTheTrailInertial() {
    assertEquals(0.0, CoRotatingTrail.groundFollowing(earthObject(400_000.0)), 0.0);
    assertNull(new CoRotatingTrail(groundTrail(10_000.0, 10_000.0)).at(epoch, 0.0));
  }

  @Test
  void theGroundFollowingOfAnObjectIsTheComplementOfTheNoseWeight() {
    assertEquals(1.0, CoRotatingTrail.groundFollowing(earthObject(50_000.0)), 0.0);
    assertEquals(0.5, CoRotatingTrail.groundFollowing(earthObject(110_000.0)), 1e-12);
  }

  @Test
  void aVertexAboveTheRampNeverMoves() {
    TrajectoryPolyline trail = groundTrail(10_000.0, 400_000.0);

    CoRotatingTrail.Frame frame = new CoRotatingTrail(trail).at(epoch.shiftedBy(LATER_S), 1.0);

    assertEquals(Vector3D.ZERO, frame.displacementAt(1));
  }

  @Test
  void halfwayUpTheObjectsRampTheDisplacementIsHalved() {
    TrajectoryPolyline trail = groundTrail(10_000.0, 10_000.0);
    CoRotatingTrail coRotating = new CoRotatingTrail(trail);
    AbsoluteDate now = epoch.shiftedBy(LATER_S);

    Vector3D full = coRotating.at(now, 1.0).displacementAt(0);
    Vector3D half = coRotating.at(now, 0.5).displacementAt(0);

    assertEquals(0.0, half.subtract(full.scalarMultiply(0.5)).getNorm(), TOLERANCE_M);
  }

  @Test
  void aVertexDatedNowStaysWhereItIs() {
    TrajectoryPolyline trail = groundTrail(10_000.0, 10_000.0);

    CoRotatingTrail.Frame frame = new CoRotatingTrail(trail).at(trail.timeAt(1), 1.0);

    assertEquals(0.0, frame.displacementAt(1).getNorm(), TOLERANCE_M);
  }

  @Test
  void aLunarTrailIsNeverCarried() {
    TrajectoryArc moon = new TrajectoryArc(SolarSystemBody.MOON, gcrf);
    List<MissionEphemerisPoint> points = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      points.add(
          new MissionEphemerisPoint(
              epoch.shiftedBy(i),
              new Vector3D(1_747_000.0 + i, 0.0, 0.0),
              new Vector3D(0.0, 1_600.0, 0.0),
              "Lunar orbit",
              false,
              1.0,
              10_000.0,
              moon));
    }

    CoRotatingTrail coRotating = new CoRotatingTrail(new MissionEphemeris(points).displayTrail());

    assertNull(coRotating.at(epoch.shiftedBy(LATER_S), 1.0));
  }

  /**
   * An object back in the air after leaving it — a payload deorbited days after its launch —
   * carries only what it flew since: its ascent, flown before it last stood above the ramp, stays
   * inertial instead of being dragged across the globe by every turn the Earth made since.
   */
  @Test
  void onlyTheCurrentPassageThroughTheAirIsCarried() {
    TrajectoryPolyline trail = groundTrail(10_000.0, 400_000.0, 10_000.0);
    AbsoluteDate now = epoch.shiftedBy(LATER_S);

    CoRotatingTrail.Frame frame = new CoRotatingTrail(trail).at(now, 1.0);

    assertEquals(3, trail.size());
    assertEquals(Vector3D.ZERO, frame.displacementAt(0));
    Vector3D reentry = trail.positionAt(2, SolarSystemBody.EARTH).add(frame.displacementAt(2));
    assertEquals(0.0, reentry.distance(terrestrialAt(10_000.0, now)), TOLERANCE_M);
  }

  /** The launch-node marker is drawn where its vertex is, carried with the Earth like it. */
  @Test
  void aPhaseMarkerMovesWithTheVertexItSitsOn() {
    TrajectoryPolyline trail = groundTrail(10_000.0, 10_000.0);
    AbsoluteDate now = epoch.shiftedBy(LATER_S);
    Node node = new Node("near");
    MissionTrajectoryRenderer renderer = new MissionTrajectoryRenderer("m", ColorRGBA.White);
    renderer.initialize(node);
    RenderContext ctx = RenderContext.planet(SolarSystemBody.EARTH);
    Vector3D tip = trail.positionAt(1, SolarSystemBody.EARTH);

    renderer.update(trail, 1, tip, tip, Vector3D.ZERO, 1.0, ctx, now);

    FloatBuffer drawn =
        (FloatBuffer)
            ((Geometry) node.getChild(1)).getMesh().getBuffer(VertexBuffer.Type.Position).getData();
    FloatBuffer expected = FloatBuffer.allocate(3);
    MissionTrajectoryRenderer.putVertex(expected, terrestrialAt(10_000.0, now).subtract(tip), ctx);
    for (int axis = 0; axis < 3; axis++) {
      assertEquals(expected.get(axis), drawn.get(axis), 1e-3f, "axis " + axis);
    }
  }

  /**
   * A trail over Kourou, one vertex per altitude, flown a second apart from the epoch on, each
   * vertex fixed in the terrestrial frame at its altitude.
   */
  private static TrajectoryPolyline groundTrail(double... altitudes) {
    List<MissionEphemerisPoint> points = new ArrayList<>();
    for (int i = 0; i < altitudes.length; i++) {
      AbsoluteDate date = epoch.shiftedBy(i);
      points.add(
          new MissionEphemerisPoint(
              date,
              terrestrialAt(altitudes[i], date),
              new Vector3D(0.0, 0.0, 100.0),
              "Ascent",
              true,
              1.0,
              altitudes[i],
              TrajectoryArc.earth()));
    }
    return new MissionEphemeris(points).displayTrail();
  }

  /** The terrestrial point over Kourou at {@code altitude}, expressed in GCRF at {@code date}. */
  private static Vector3D terrestrialAt(double altitude, AbsoluteDate date) {
    Vector3D up =
        new Vector3D(
            FastMath.cos(LATITUDE) * FastMath.cos(LONGITUDE),
            FastMath.cos(LATITUDE) * FastMath.sin(LONGITUDE),
            FastMath.sin(LATITUDE));
    return itrf.getTransformTo(gcrf, date)
        .transformPosition(up.scalarMultiply(EARTH_RADIUS_M + altitude));
  }

  private static MissionEphemerisPoint earthObject(double altitude) {
    return new MissionEphemerisPoint(
        epoch,
        new Vector3D(EARTH_RADIUS_M + altitude, 0.0, 0.0),
        new Vector3D(0.0, 7_500.0, 0.0),
        "Flight",
        true,
        1.0,
        altitude,
        TrajectoryArc.earth());
  }
}
