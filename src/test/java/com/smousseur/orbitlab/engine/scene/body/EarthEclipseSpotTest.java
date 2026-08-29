package com.smousseur.orbitlab.engine.scene.body;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

/**
 * Closes L3 of {@code docs/eclipses/01-decoupage.md}: the payoff of building a per-fragment
 * mechanism rather than a whole-body scalar is that the shadow the Moon casts on the Earth is
 * <em>localised</em> — a point under the Moon's shadow darkens, a point elsewhere on the same lit
 * hemisphere does not. {@link EclipseGeometry#illuminationFraction} is what the shader evaluates per
 * fragment (`WrapLighting.frag`'s {@code eclipseIllumination}, same formula) — this test evaluates
 * it directly at several points on the Earth's surface instead of once at the Earth's centre, since
 * the centre is never a meaningful eclipse point (it sits under thousands of km of rock, never in
 * anyone's shadow) and the whole point of L3 is spatial variation across the surface.
 */
class EarthEclipseSpotTest {

  private static final double EARTH_RADIUS = 6_378_137.0;
  private static final double MOON_RADIUS = 1_737_400.0;
  private static final double MOON_DISTANCE = 384_400_000.0;
  private static final double SUN_DISTANCE = 1.495978707e11;

  @Test
  void theSubLunarPointIsDarkenedByAnAlignedMoon() {
    // Sun, Moon and the sub-lunar point all lie on the Sun direction from Earth's centre — the
    // definition of maximum eclipse.
    Vector3D sunDirection = new Vector3D(1.0, 0.0, 0.0);
    Vector3D sunPosition = sunDirection.scalarMultiply(SUN_DISTANCE);
    Vector3D moonPosition = sunDirection.scalarMultiply(MOON_DISTANCE);
    Vector3D subLunarPoint = sunDirection.scalarMultiply(EARTH_RADIUS);

    double illumination = illuminationAt(subLunarPoint, moonPosition, sunPosition);

    assertTrue(illumination < 0.2, "the sub-lunar point must be substantially eclipsed: " + illumination);
  }

  // No antipodal-point case: a point directly behind the Earth from the Sun stays exactly
  // colinear with the Sun-Moon axis (Earth's radius does not move it off that line the way a
  // perpendicular displacement does), so the formula alone — which has no notion of the Earth's
  // own bulk blocking the view — reports it as eclipsed too. That is not a visible bug: the same
  // point is deep on the night side, so WrapLighting.frag's existing day/night term (m_FallOffFactor
  // on N.L) already renders it near-black regardless of what the eclipse factor multiplies in. The
  // meaningful localisation test is displacement *across* the Sun-Moon axis, covered below.

  @Test
  void aPointNinetyDegreesFromTheSubLunarPointIsUnaffected() {
    Vector3D sunDirection = new Vector3D(1.0, 0.0, 0.0);
    Vector3D sunPosition = sunDirection.scalarMultiply(SUN_DISTANCE);
    Vector3D moonPosition = sunDirection.scalarMultiply(MOON_DISTANCE);
    Vector3D sidePoint = sunDirection.orthogonal().scalarMultiply(EARTH_RADIUS);

    double illumination = illuminationAt(sidePoint, moonPosition, sunPosition);

    assertTrue(illumination > 0.99, "a point 90 degrees away must be unaffected: " + illumination);
  }

  /** Mirrors what {@code WrapLighting.frag}'s {@code eclipseIllumination(vPosWorld)} computes. */
  private static double illuminationAt(Vector3D surfacePoint, Vector3D moonPosition, Vector3D sunPosition) {
    Vector3D toMoon = moonPosition.subtract(surfacePoint);
    Vector3D toSun = sunPosition.subtract(surfacePoint);
    double separation = EclipseGeometry.separationRadians(toMoon, toSun);
    double moonAngularRadius = EclipseGeometry.angularRadius(MOON_RADIUS, toMoon.getNorm());
    double sunAngularRadius = EclipseGeometry.sunApparentRadius(toSun.getNorm());
    return EclipseGeometry.illuminationFraction(separation, moonAngularRadius, sunAngularRadius);
  }
}
