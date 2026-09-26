package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.math.Quaternion;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.EllipsoidGlobe;
import com.smousseur.orbitlab.engine.scene.planet.GroundCorrection;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.ephemeris.TrajectoryArc;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/** The ground correction as the renderer applies it to a drawn sample. */
class MissionRendererGroundTest {

  private static final double A = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final GroundCorrection UNTURNED =
      GroundCorrection.withDrawnRotation(Quaternion.IDENTITY);
  private static AbsoluteDate epoch;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void aPointFlownAboutAnotherBodyIsLeftAlone() {
    MissionEphemerisPoint lunar =
        point(new Vector3D(1_737_400.0, 0.0, 0.0), TrajectoryArc.forBody(SolarSystemBody.MOON));
    assertSame(lunar, MissionRenderer.groundCorrected(lunar, UNTURNED));
  }

  /** At the centre of an equatorial cell, where the facet runs deepest: 484 m. */
  @Test
  void anEarthGroundPointIsCorrectedAndKeepsEverythingElse() {
    Vector3D ground =
        RenderContext.planet(SolarSystemBody.EARTH)
            .axisConvention()
            .jmeToIcrf(EllipsoidGlobe.earth().surface(0.5, -179.5).scalarMultiply(A));
    MissionEphemerisPoint pt = point(ground, TrajectoryArc.forBody(SolarSystemBody.EARTH));

    MissionEphemerisPoint corrected = MissionRenderer.groundCorrected(pt, UNTURNED);

    assertEquals(484.06, ground.getNorm() - corrected.position().getNorm(), 1.0);
    assertEquals(pt.time(), corrected.time());
    assertEquals(pt.velocity(), corrected.velocity());
    assertEquals(pt.stageName(), corrected.stageName());
    assertEquals(pt.mass(), corrected.mass());
    assertEquals(pt.altitudeMeters(), corrected.altitudeMeters());
    assertEquals(pt.arc(), corrected.arc());
  }

  /**
   * The impact velocity dips below the horizon — the fall ends on a downward altitude crossing — so
   * a landed object lies along its horizontal part instead: its heading, pitched up.
   */
  @Test
  void aLandedObjectLiesAlongTheHorizontalOfItsImpactVelocity() {
    Vector3D up = new Vector3D(0.2, 0.3, 0.9).normalize();
    Vector3D impact = new Vector3D(400.0, 100.0, -300.0);

    MissionRenderer.LandedAttitude lying = MissionRenderer.landedAttitude(impact, up);

    assertEquals(1.0, lying.heading().getNorm(), 1e-12);
    assertEquals(0.0, lying.heading().dotProduct(up), 1e-12);
    Vector3D horizontal = impact.subtract(impact.dotProduct(up), up);
    assertEquals(0.0, Vector3D.angle(horizontal, lying.heading()), 1e-12);
    assertEquals(up, lying.up());
  }

  /** Near a pole the Earth's rotation no longer gives the velocity a horizontal part. */
  @Test
  void aVerticalImpactLiesAlongCelestialNorth() {
    Vector3D up = Vector3D.PLUS_I;

    MissionRenderer.LandedAttitude lying =
        MissionRenderer.landedAttitude(up.scalarMultiply(-90.0), up);

    assertEquals(0.0, Vector3D.angle(Vector3D.PLUS_K, lying.heading()), 1e-12);
  }

  @Test
  void aVerticalImpactAtThePoleStillLiesFlat() {
    Vector3D up = Vector3D.PLUS_K;

    MissionRenderer.LandedAttitude lying =
        MissionRenderer.landedAttitude(up.scalarMultiply(-90.0), up);

    assertEquals(1.0, lying.heading().getNorm(), 1e-12);
    assertEquals(0.0, lying.heading().dotProduct(up), 1e-12);
  }

  /** Lifted by its clearance along the vertical and turned to its heading, at its own speed. */
  @Test
  void theLyingPointIsLiftedAndTurnedButKeepsItsSpeed() {
    Vector3D up = Vector3D.PLUS_I;
    Vector3D heading = Vector3D.PLUS_J;
    MissionEphemerisPoint pt =
        point(new Vector3D(A, 0.0, 0.0), TrajectoryArc.forBody(SolarSystemBody.EARTH));

    MissionEphemerisPoint drawn =
        MissionRenderer.lying(pt, new MissionRenderer.LandedAttitude(heading, up), 2.9);

    assertEquals(new Vector3D(A + 2.9, 0.0, 0.0), drawn.position());
    assertEquals(pt.velocity().getNorm(), drawn.velocity().getNorm(), 1e-9);
    assertEquals(0.0, Vector3D.angle(heading, drawn.velocity()), 1e-12);
    assertEquals(pt.time(), drawn.time());
    assertEquals(pt.altitudeMeters(), drawn.altitudeMeters());
    assertEquals(pt.arc(), drawn.arc());
  }

  /**
   * A landed object rests its clearance plus a margin above its ground point: the drawn ground
   * under it moves by up to 1.9 m from frame to frame through the float scene graph.
   */
  @Test
  void aLandedObjectIsLiftedByItsClearancePlusTheFloatMargin() {
    assertEquals(2.9 + 3.0, MissionRenderer.landedLiftMeters(2.9), 1e-12);
  }

  private static MissionEphemerisPoint point(Vector3D position, TrajectoryArc arc) {
    return new MissionEphemerisPoint(
        epoch, position, new Vector3D(0.0, 465.0, 0.0), "Reentry", false, 1_000.0, 0.0, arc);
  }
}
