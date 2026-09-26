package com.smousseur.orbitlab.engine.scene.planet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.EllipsoidGlobe;
import java.util.List;
import java.util.Random;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

/**
 * An object near the Earth's ground is brought onto the drawn facet along the ellipsoid normal,
 * fully at the ground and not at all from {@link GroundCorrection#BLEND_HEIGHT_METERS} up.
 */
class GroundCorrectionTest {

  private static final double A = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final EllipsoidGlobe GLOBE = EllipsoidGlobe.earth();
  private static final AxisConvention AXES =
      RenderContext.planet(SolarSystemBody.EARTH).axisConvention();
  private static final Quaternion TURNED = new Quaternion().fromAngles(0.3f, 1.1f, -0.4f);

  @Test
  void aGroundPointIsBroughtDownOntoTheDrawnFacet() {
    for (Quaternion q : List.of(Quaternion.IDENTITY, TURNED)) {
      Vector3D p = icrf(q, 5.23, -52.77, 0.0);
      EllipsoidGlobe.Geodetic after =
          geodeticOf(q, GroundCorrection.withDrawnRotation(q).correct(p));
      assertEquals(-GLOBE.facetDepth(5.23, -52.77) * A, after.height() * A, 1e-4);
      assertEquals(5.23, after.latitudeDeg(), 1e-9);
      assertEquals(-52.77, after.azimuthDeg(), 1e-9);
    }
  }

  @Test
  void halfwayUpTheBlendTheCorrectionIsHalved() {
    Vector3D p = icrf(TURNED, 28.5, -80.6, 2_500.0);
    EllipsoidGlobe.Geodetic after =
        geodeticOf(TURNED, GroundCorrection.withDrawnRotation(TURNED).correct(p));
    assertEquals(2_500.0 - 0.5 * GLOBE.facetDepth(28.5, -80.6) * A, after.height() * A, 1e-4);
  }

  @Test
  void aPointAboveTheBlendIsReturnedUntouched() {
    GroundCorrection correction = GroundCorrection.withDrawnRotation(TURNED);
    Vector3D justAbove = icrf(TURNED, 28.5, -80.6, GroundCorrection.BLEND_HEIGHT_METERS + 0.5);
    Vector3D inOrbit = icrf(TURNED, 28.5, -80.6, 400_000.0);
    assertSame(justAbove, correction.correct(justAbove));
    assertSame(inOrbit, correction.correct(inOrbit));
  }

  @Test
  void theDisplacementIsAlongTheEllipsoidNormal() {
    Vector3D p = icrf(TURNED, 45.0, 10.4, 0.0);
    Vector3D displacement = GroundCorrection.withDrawnRotation(TURNED).correct(p).subtract(p);
    Vector3D up = AXES.jmeToIcrf(GroundCorrection.rotate(TURNED, GLOBE.normal(45.0, 10.4)));
    assertEquals(0.0, displacement.crossProduct(up).getNorm(), 1e-6);
    assertTrue(displacement.dotProduct(up) < 0.0);
  }

  @Test
  void upIsTheDrawnGlobesGeodeticVertical() {
    for (Quaternion q : List.of(Quaternion.IDENTITY, TURNED)) {
      Vector3D up = GroundCorrection.withDrawnRotation(q).up(icrf(q, 52.0, 13.4, 0.0));
      Vector3D expected = AXES.jmeToIcrf(GroundCorrection.rotate(q, GLOBE.normal(52.0, 13.4)));
      assertEquals(1.0, up.getNorm(), 1e-12);
      assertEquals(0.0, Vector3D.angle(expected, up), 1e-9);
    }
  }

  @Test
  void mayReachIsANormTest() {
    assertTrue(GroundCorrection.mayReach(new Vector3D(A + 4_999.0, 0.0, 0.0)));
    assertFalse(GroundCorrection.mayReach(new Vector3D(0.0, 0.0, A + 5_001.0)));
  }

  @Test
  void rotateMatchesJmesQuaternion() {
    Random random = new Random(7);
    for (int i = 0; i < 20; i++) {
      Vector3D v =
          new Vector3D(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
      Vector3f expected =
          TURNED.mult(new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ()));
      Vector3D actual = GroundCorrection.rotate(TURNED, v);
      assertEquals(expected.x, actual.getX(), 1e-5);
      assertEquals(expected.y, actual.getY(), 1e-5);
      assertEquals(expected.z, actual.getZ(), 1e-5);
    }
  }

  /** The drawn height above the facet is h·(1 + δ/H): ten deepest facets keep it within 10 %. */
  @Test
  void theBlendHeightHoldsTenOfTheDeepestFacets() {
    double deepest = 0.0;
    for (double lat = -1.0; lat <= 1.0; lat += 0.01) {
      for (int k = 0; k < 20; k++) {
        deepest = Math.max(deepest, GLOBE.facetDepth(lat, -180.0 + 0.05 * k) * A);
      }
    }
    assertTrue(10.0 * deepest <= GroundCorrection.BLEND_HEIGHT_METERS, "deepest " + deepest);
  }

  private static Vector3D icrf(Quaternion q, double lat, double az, double heightMeters) {
    Vector3D local = GLOBE.surface(lat, az).add(heightMeters / A, GLOBE.normal(lat, az));
    return AXES.jmeToIcrf(GroundCorrection.rotate(q, local.scalarMultiply(A)));
  }

  private static EllipsoidGlobe.Geodetic geodeticOf(Quaternion q, Vector3D icrf) {
    Quaternion inverse = new Quaternion(-q.getX(), -q.getY(), -q.getZ(), q.getW());
    return GLOBE.geodetic(
        GroundCorrection.rotate(inverse, AXES.icrfToJme(icrf)).scalarMultiply(1.0 / A));
  }
}
