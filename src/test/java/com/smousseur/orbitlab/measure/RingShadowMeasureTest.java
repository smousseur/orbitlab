package com.smousseur.orbitlab.measure;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.EclipseGeometry;
import com.smousseur.orbitlab.engine.scene.mesh.ModelNodes;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.CelestialBody;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * {@code FX-5} — the numbers the fiche asks for, and the one question no assertion should hold: how
 * much of its ring a planet's shadow actually covers, which depends entirely on the date.
 *
 * <p><b>The whole effect is one angle.</b> Put the globe at the origin and its ring in the
 * equatorial plane. The umbra is a cylinder of the globe's radius {@code R} about the anti-solar
 * direction, and that direction leaves the plane at elevation {@code beta} — the Sun's height above
 * the ring plane, i.e. the sub-solar latitude. A ring point at radius {@code r} and azimuth {@code
 * phi} from the sub-solar direction sees the globe's centre {@code acos(-cos phi cos beta)} away
 * from the Sun, so along the anti-solar azimuth ({@code phi = 180}) that separation is exactly
 * {@code beta}. Handing it to the same {@link EclipseGeometry#illuminationFraction} the shader
 * evaluates gives the depth of the shadow, and the umbra runs out at {@code r = R / sin(beta +
 * rs)}.
 *
 * <p>Two consequences the arithmetic makes plain and the eye does not. The band is <b>always {@code
 * 2R} across</b>, whatever the date — the cylinder's cross-section does not care how the plane cuts
 * it — while its <b>reach along the ring goes as {@code 1 / sin beta}</b>. And a ring whose Sun
 * stands high enough is not shadowed at all: the umbra ends before the ring's inner edge. Which of
 * the two ringed bodies shows the effect is therefore a property of the simulated date, not of the
 * body.
 *
 * <p>Nothing here is asserted. The geometry is already pinned by {@code EclipseGeometryTest} and
 * {@code EarthEclipseSpotTest}; what varies below is the epoch, and a number that legitimately
 * changes with the date belongs in {@code docs/} rather than in a test that would go red for being
 * right.
 */
class RingShadowMeasureTest {

  private static final Logger logger = LogManager.getLogger(RingShadowMeasureTest.class);

  /** Sidereal orbital periods, in Julian years (IAU). A quarter of one spans the ring's opening. */
  private static final double SATURN_PERIOD_YEARS = 29.4571;

  private static final double URANUS_PERIOD_YEARS = 84.0205;

  private static final int SAMPLES = 7;

  private static final double SECONDS_PER_YEAR = 365.25 * 86400.0;

  private static AbsoluteDate today;

  private static Frame icrf;

  private static CelestialBody sun;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    today = new AbsoluteDate(2026, 9, 4, 0, 0, 0.0, TimeScalesFactory.getUTC());
    icrf = OrekitService.get().icrf();
    sun = OrekitService.get().body(SolarSystemBody.SUN);
  }

  @Test
  void howMuchOfEachRingIsShadowed() {
    for (SolarSystemBody body : List.of(SolarSystemBody.SATURN, SolarSystemBody.URANUS)) {
      RingExtent ring = measureRing(body);
      double radiusKm = PlanetRadius.radiusFor(body) / 1000.0;
      double quarterPeriodYears =
          (body == SolarSystemBody.SATURN ? SATURN_PERIOD_YEARS : URANUS_PERIOD_YEARS) / 4.0;

      logger.info("");
      logger.info(
          String.format(
              Locale.ROOT,
              "%s — globe drawn at R = %,.0f km; ring measured on the asset at %.3f R to %.3f R"
                  + " (%,.0f to %,.0f km). Shadow band is 2R = %,.0f km across at every epoch.",
              body.displayName(),
              radiusKm,
              ring.innerRadii(),
              ring.outerRadii(),
              ring.innerRadii() * radiusKm,
              ring.outerRadii() * radiusKm,
              2 * radiusKm));
      logger.info(
          String.format(
              Locale.ROOT,
              "%-12s %9s %11s %12s %12s %13s  %s",
              "date",
              "opening",
              "umbra ends",
              "illum inner",
              "illum outer",
              "penumbra",
              "verdict"));

      for (int i = 0; i < SAMPLES; i++) {
        double years = quarterPeriodYears * i / (SAMPLES - 1.0);
        logRow(body, ring, radiusKm, today.shiftedBy(years * SECONDS_PER_YEAR));
      }
    }
  }

  private void logRow(SolarSystemBody body, RingExtent ring, double radiusKm, AbsoluteDate date) {
    CelestialBody planet = OrekitService.get().body(body);
    Vector3D planetToSun = sun.getPosition(date, icrf).subtract(planet.getPosition(date, icrf));
    Vector3D pole =
        planet.getBodyOrientedFrame().getTransformTo(icrf, date).transformVector(Vector3D.PLUS_K);

    double betaRad = FastMath.abs(FastMath.PI / 2.0 - Vector3D.angle(pole, planetToSun));
    double sunApparentRadius = EclipseGeometry.sunApparentRadius(planetToSun.getNorm());
    double umbraEndsRadii = 1.0 / FastMath.sin(betaRad + sunApparentRadius);

    String verdict;
    if (umbraEndsRadii >= ring.outerRadii()) {
      verdict = "covers the whole ring";
    } else if (umbraEndsRadii >= ring.innerRadii()) {
      verdict = "inner ring only";
    } else {
      verdict = "MISSES THE RING";
    }

    logger.info(
        String.format(
            Locale.ROOT,
            "%-12s %8.2f° %10.2fR %12.3f %12.3f %10.0f km  %s",
            date.toString().substring(0, 10),
            FastMath.toDegrees(betaRad),
            umbraEndsRadii,
            illuminationAtAntiSolarAzimuth(ring.innerRadii(), betaRad, sunApparentRadius),
            illuminationAtAntiSolarAzimuth(ring.outerRadii(), betaRad, sunApparentRadius),
            ring.outerRadii() * radiusKm * sunApparentRadius,
            verdict));
  }

  /**
   * Illumination of the ring point deepest in the shadow at radius {@code radii}: the one on the
   * anti-solar azimuth, where the separation between the globe's centre and the Sun collapses to
   * {@code beta} exactly.
   */
  private static double illuminationAtAntiSolarAzimuth(
      double radii, double betaRad, double sunApparentRadius) {
    return EclipseGeometry.illuminationFraction(
        betaRad, FastMath.asin(1.0 / radii), sunApparentRadius);
  }

  /**
   * Inner and outer radius of a body's ring, in units of its globe's radius, read off the asset
   * itself rather than assumed: the shadow's reach is compared against these, so a re-export that
   * moved the ring would otherwise be measured against a number from the wrong file.
   */
  private static RingExtent measureRing(SolarSystemBody body) {
    AssetManager assetManager = new DesktopAssetManager(true);
    String name = body.displayName().toLowerCase(Locale.ROOT);
    Spatial model = assetManager.loadModel("models/planets/" + name + "/" + name + ".gltf");
    model.updateGeometricState();

    String prefix = PlanetMeshCorrection.ringNodePrefixFor(body).orElseThrow();
    List<Geometry> ring =
        ModelNodes.firstNamed(model, prefix).map(ModelNodes::geometriesUnder).orElseThrow();

    double globeRadius = 0.0;
    double ringInner = Double.POSITIVE_INFINITY;
    double ringOuter = 0.0;
    for (Geometry geometry : ModelNodes.geometriesUnder(model)) {
      double[] extent = radialExtent(geometry);
      if (ring.contains(geometry)) {
        ringInner = Math.min(ringInner, extent[0]);
        ringOuter = Math.max(ringOuter, extent[1]);
      } else {
        globeRadius = Math.max(globeRadius, extent[1]);
      }
    }
    return new RingExtent(ringInner / globeRadius, ringOuter / globeRadius);
  }

  /** Smallest and largest distance from the model's origin over a geometry's vertices. */
  private static double[] radialExtent(Geometry geometry) {
    FloatBuffer positions = geometry.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
    Vector3f point = new Vector3f();
    double min = Double.POSITIVE_INFINITY;
    double max = 0.0;
    for (int i = 0; i < positions.limit() / 3; i++) {
      point.set(positions.get(i * 3), positions.get(i * 3 + 1), positions.get(i * 3 + 2));
      geometry.getWorldTransform().transformVector(point, point);
      double length = point.length();
      min = Math.min(min, length);
      max = Math.max(max, length);
    }
    return new double[] {min, max};
  }

  /** A ring's radial span, in units of the globe's radius in the same model. */
  private record RingExtent(double innerRadii, double outerRadii) {}
}
