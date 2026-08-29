package com.smousseur.orbitlab.engine.scene.body;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link EclipseGeometry}. No Orekit, no JME — angles and the disk-overlap
 * illumination fraction only.
 */
class EclipseGeometryTest {

  private static final double TOLERANCE = 1e-9;

  @Test
  void angularRadiusOfBodyAtTwiceItsRadiusIsThirtyDegrees() {
    // asin(r / 2r) = asin(0.5) = pi/6.
    assertEquals(Math.PI / 6.0, EclipseGeometry.angularRadius(1.0, 2.0), TOLERANCE);
  }

  @Test
  void angularRadiusAtEqualDistanceIsNinetyDegrees() {
    assertEquals(Math.PI / 2.0, EclipseGeometry.angularRadius(1.0, 1.0), TOLERANCE);
  }

  @Test
  void angularRadiusClampsWhenInsideTheBody() {
    // Distance smaller than the radius is degenerate (observer inside the body); asin's domain
    // would otherwise be exceeded and return NaN.
    assertEquals(Math.PI / 2.0, EclipseGeometry.angularRadius(2.0, 1.0), TOLERANCE);
  }

  @Test
  void sunApparentRadiusAtOneAuMatchesTheKnownRealWorldValue() {
    // The Sun's real apparent radius as seen from Earth is ~0.267 degrees (~4.66 mrad).
    double oneAuMeters = 1.495978707e11;
    double radiusRad = EclipseGeometry.sunApparentRadius(oneAuMeters);
    assertEquals(Math.toRadians(0.267), radiusRad, Math.toRadians(0.01));
  }

  @Test
  void separationOfIdenticalDirectionsIsZero() {
    Vector3D a = new Vector3D(3.0, 0.0, 0.0);
    assertEquals(0.0, EclipseGeometry.separationRadians(a, a), TOLERANCE);
  }

  @Test
  void separationOfPerpendicularDirectionsIsNinetyDegrees() {
    Vector3D a = new Vector3D(1.0, 0.0, 0.0);
    Vector3D b = new Vector3D(0.0, 1.0, 0.0);
    assertEquals(Math.PI / 2.0, EclipseGeometry.separationRadians(a, b), TOLERANCE);
  }

  @Test
  void separationOfOppositeDirectionsIsOneEighty() {
    Vector3D a = new Vector3D(1.0, 0.0, 0.0);
    Vector3D b = new Vector3D(-5.0, 0.0, 0.0);
    assertEquals(Math.PI, EclipseGeometry.separationRadians(a, b), TOLERANCE);
  }

  @Test
  void illuminationIsFullWhenDisksDoNotOverlap() {
    double sunRadius = 0.1;
    double occluderRadius = 0.05;
    double separation = sunRadius + occluderRadius + 0.1; // clear of the sum of the two radii
    assertEquals(
        1.0,
        EclipseGeometry.illuminationFraction(separation, occluderRadius, sunRadius),
        TOLERANCE);
  }

  @Test
  void illuminationIsFullExactlyAtTheTouchingBoundary() {
    double sunRadius = 0.1;
    double occluderRadius = 0.05;
    double separation = sunRadius + occluderRadius;
    assertEquals(
        1.0, EclipseGeometry.illuminationFraction(separation, occluderRadius, sunRadius), 1e-6);
  }

  @Test
  void illuminationIsZeroInTotalEclipse() {
    // Occluder bigger than the Sun, centred on it: full totality.
    double sunRadius = 0.1;
    double occluderRadius = 0.2;
    assertEquals(
        0.0, EclipseGeometry.illuminationFraction(0.0, occluderRadius, sunRadius), TOLERANCE);
  }

  @Test
  void illuminationIsPartialInAnnularConfiguration() {
    // Occluder smaller than the Sun, centred on it: blocks (occluderRadius/sunRadius)^2 of the
    // disk area, the rest stays lit as an annulus.
    double sunRadius = 0.2;
    double occluderRadius = 0.1;
    double expected = 1.0 - (occluderRadius / sunRadius) * (occluderRadius / sunRadius);
    assertEquals(
        expected, EclipseGeometry.illuminationFraction(0.0, occluderRadius, sunRadius), TOLERANCE);
  }

  @Test
  void illuminationAtPartialOverlapMatchesTheHandComputedLensArea() {
    // Equal radii, separation equal to that radius: a classic circle-circle intersection case.
    // overlapArea = 2 * (pi/3) - 0.5 * sqrt(3); sunArea = pi; illumination = 1 -
    // overlapArea/sunArea.
    double radius = 1.0;
    double separation = 1.0;
    double overlapArea = 2.0 * (Math.PI / 3.0) - 0.5 * Math.sqrt(3.0);
    double expected = 1.0 - overlapArea / Math.PI;
    assertEquals(expected, EclipseGeometry.illuminationFraction(separation, radius, radius), 1e-6);
  }

  @Test
  void illuminationStaysWithinZeroAndOne() {
    double result = EclipseGeometry.illuminationFraction(0.02, 0.05, 0.03);
    assertEquals(Math.max(0.0, Math.min(1.0, result)), result, TOLERANCE);
  }
}
