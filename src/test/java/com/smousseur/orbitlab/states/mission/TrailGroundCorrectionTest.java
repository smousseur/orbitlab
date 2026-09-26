package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.EllipsoidGlobe;
import com.smousseur.orbitlab.engine.scene.planet.GroundCorrection;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryPolyline;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/** Which ribbon vertices can come near the ground, and how each is brought onto the drawn globe. */
class TrailGroundCorrectionTest {

  private static final double A = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void onlyTheVerticesThatCanNearTheGroundAreKept() {
    TrajectoryPolyline trail = earthTrail(new Vector3D(A + 400_000.0, 0.0, 0.0), ground());
    TrailGroundCorrection grounded = new TrailGroundCorrection(trail);

    assertFalse(grounded.isEmpty());
    assertFalse(grounded.isNearGround(0));
    assertTrue(grounded.isNearGround(1));
  }

  /**
   * The ground vertex sits at the centre of an equatorial cell, where the facet runs 484 m deep.
   */
  @Test
  void aGroundVertexIsDisplacedOntoTheDrawnFacet() {
    TrajectoryPolyline trail = earthTrail(new Vector3D(A + 400_000.0, 0.0, 0.0), ground());
    TrailGroundCorrection grounded = new TrailGroundCorrection(trail);

    Vector3D displacement =
        grounded.displacementAt(1, GroundCorrection.withDrawnRotation(Quaternion.IDENTITY));

    assertEquals(484.06, displacement.getNorm(), 1.0);
  }

  @Test
  void aTrailFlownAboutTheMoonHasNoGroundVertex() {
    TrajectoryArc moon = TrajectoryArc.forBody(SolarSystemBody.MOON);
    MissionEphemeris lunar =
        new MissionEphemeris(
            List.of(
                point(epoch, new Vector3D(1_837_400.0, 0.0, 0.0), moon),
                point(epoch.shiftedBy(60.0), new Vector3D(1_737_400.0, 0.0, 0.0), moon)));

    assertTrue(new TrailGroundCorrection(lunar.displayTrail()).isEmpty());
  }

  private static Vector3D ground() {
    return RenderContext.planet(SolarSystemBody.EARTH)
        .axisConvention()
        .jmeToIcrf(EllipsoidGlobe.earth().surface(0.5, 0.5).scalarMultiply(A));
  }

  private static TrajectoryPolyline earthTrail(Vector3D first, Vector3D second) {
    TrajectoryArc earth = TrajectoryArc.forBody(SolarSystemBody.EARTH);
    return new MissionEphemeris(
            List.of(point(epoch, first, earth), point(epoch.shiftedBy(600.0), second, earth)))
        .displayTrail();
  }

  private static MissionEphemerisPoint point(
      AbsoluteDate time, Vector3D position, TrajectoryArc arc) {
    return new MissionEphemerisPoint(
        time, position, new Vector3D(0.0, 7_500.0, 0.0), "Reentry", false, 1_000.0, 0.0, arc);
  }
}
