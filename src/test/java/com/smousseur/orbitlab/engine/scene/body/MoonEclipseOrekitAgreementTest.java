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
 * Closes L2 of {@code docs/eclipses/01-decoupage.md}: the same {@link EclipseGeometry} engine L1
 * validated at LEO scale must also agree with Orekit's {@link EclipseDetector} at the Earth-Moon
 * distance, for a Moon positioned opposite the Sun (lunar eclipse) and on the sunward side (no
 * eclipse). This only re-exercises the geometry — {@code PlanetPoseAppState}'s per-frame wiring is
 * JME-only code, untestable headless like the rest of that layer (no test precedent for {@code
 * LodView}/{@code Model3dView}/{@code AssetFactory} either).
 */
class MoonEclipseOrekitAgreementTest {

  private static final double EARTH_RADIUS = 6_378_137.0;
  private static final double MOON_DISTANCE = 384_400_000.0;

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
            OrekitService.get().body(SolarSystemBody.SUN),
            PlanetRadius.radiusFor(SolarSystemBody.SUN),
            earth);
    penumbraDetector = new EclipseDetector(engine).withPenumbra();
  }

  @Test
  void agreesOnAMoonOppositeTheSunFromEarth() {
    Vector3D sunPosition = OrekitService.get().body(SolarSystemBody.SUN).getPosition(date, gcrf);
    Vector3D moonPosition = sunPosition.normalize().negate().scalarMultiply(MOON_DISTANCE);

    double g = penumbraDetector.g(stateAt(moonPosition));
    double illumination = illuminationAt(moonPosition, sunPosition);

    assertTrue(g < 0.0, "EclipseDetector must report the Moon as shadowed, g=" + g);
    assertTrue(illumination < 1.0, "illuminationFraction must show some occlusion: " + illumination);
  }

  @Test
  void agreesOnAMoonBetweenEarthAndTheSun() {
    Vector3D sunPosition = OrekitService.get().body(SolarSystemBody.SUN).getPosition(date, gcrf);
    Vector3D moonPosition = sunPosition.normalize().scalarMultiply(MOON_DISTANCE);

    double g = penumbraDetector.g(stateAt(moonPosition));
    double illumination = illuminationAt(moonPosition, sunPosition);

    assertTrue(g > 0.0, "EclipseDetector must report the Moon as lit, g=" + g);
    assertTrue(illumination > 0.99, "illuminationFraction must be fully lit: " + illumination);
  }

  private static SpacecraftState stateAt(Vector3D position) {
    return new SpacecraftState(
        new AbsolutePVCoordinates(gcrf, date, new PVCoordinates(position, Vector3D.ZERO)));
  }

  private static double illuminationAt(Vector3D moonPosition, Vector3D sunPosition) {
    Vector3D toEarth = moonPosition.negate();
    Vector3D toSun = sunPosition.subtract(moonPosition);
    double separation = EclipseGeometry.separationRadians(toEarth, toSun);
    double occluderRadius = EclipseGeometry.angularRadius(EARTH_RADIUS, toEarth.getNorm());
    double sunRadius = EclipseGeometry.sunApparentRadius(toSun.getNorm());
    return EclipseGeometry.illuminationFraction(separation, occluderRadius, sunRadius);
  }
}
