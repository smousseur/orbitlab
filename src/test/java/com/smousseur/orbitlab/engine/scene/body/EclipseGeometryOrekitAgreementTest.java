package com.smousseur.orbitlab.engine.scene.body;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.EclipseDetector;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.AbsolutePVCoordinates;
import org.orekit.utils.OccultationEngine;
import org.orekit.utils.PVCoordinates;

/**
 * Closes L1 of {@code docs/eclipses/01-decoupage.md}: {@link EclipseGeometry#illuminationFraction}
 * must agree with Orekit's own {@link EclipseDetector} — an independent oracle already shipped with
 * the dependency and unused elsewhere in this codebase — on whether a point is in the Earth's shadow.
 */
class EclipseGeometryOrekitAgreementTest {

  private static final double EARTH_RADIUS = 6_378_137.0;
  private static final double LEO_ALTITUDE = 400_000.0;

  private static AbsoluteDate date;
  private static Frame gcrf;
  private static EclipseDetector penumbraDetector;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    date = new AbsoluteDate(2026, 3, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
    gcrf = OrekitService.get().gcrf();
    OneAxisEllipsoid earth = new OneAxisEllipsoid(EARTH_RADIUS, 0.0, gcrf);
    OccultationEngine engine =
        new OccultationEngine(
            OrekitService.get().body(SolarSystemBody.SUN), PlanetRadius.radiusFor(SolarSystemBody.SUN), earth);
    penumbraDetector = new EclipseDetector(engine).withPenumbra();
  }

  @Test
  void agreesOnASpacecraftDirectlyBehindEarthFromTheSun() {
    Vector3D sunPosition = OrekitService.get().body(SolarSystemBody.SUN).getPosition(date, gcrf);
    Vector3D spacecraftPosition =
        sunPosition.normalize().negate().scalarMultiply(EARTH_RADIUS + LEO_ALTITUDE);

    double g = penumbraDetector.g(stateAt(spacecraftPosition));
    double illumination = illuminationAt(spacecraftPosition, sunPosition);

    assertTrue(g < 0.0, "EclipseDetector must report this point as shadowed, g=" + g);
    assertTrue(illumination < 0.01, "illuminationFraction must be near total eclipse: " + illumination);
  }

  @Test
  void agreesOnASpacecraftDirectlyBetweenEarthAndTheSun() {
    Vector3D sunPosition = OrekitService.get().body(SolarSystemBody.SUN).getPosition(date, gcrf);
    Vector3D spacecraftPosition =
        sunPosition.normalize().scalarMultiply(EARTH_RADIUS + LEO_ALTITUDE);

    double g = penumbraDetector.g(stateAt(spacecraftPosition));
    double illumination = illuminationAt(spacecraftPosition, sunPosition);

    assertTrue(g > 0.0, "EclipseDetector must report this point as lit, g=" + g);
    assertTrue(illumination > 0.99, "illuminationFraction must be fully lit: " + illumination);
  }

  private static SpacecraftState stateAt(Vector3D position) {
    return new SpacecraftState(
        new AbsolutePVCoordinates(gcrf, date, new PVCoordinates(position, Vector3D.ZERO)));
  }

  private static double illuminationAt(Vector3D spacecraftPosition, Vector3D sunPosition) {
    Vector3D toEarth = spacecraftPosition.negate();
    Vector3D toSun = sunPosition.subtract(spacecraftPosition);
    double separation = EclipseGeometry.separationRadians(toEarth, toSun);
    double occluderRadius = EclipseGeometry.angularRadius(EARTH_RADIUS, toEarth.getNorm());
    double sunRadius = EclipseGeometry.sunApparentRadius(toSun.getNorm());
    return EclipseGeometry.illuminationFraction(separation, occluderRadius, sunRadius);
  }
}
