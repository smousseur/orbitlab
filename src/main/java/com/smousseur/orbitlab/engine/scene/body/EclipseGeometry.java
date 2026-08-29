package com.smousseur.orbitlab.engine.scene.body;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;

/**
 * Pure geometry for occultation shading: angular radii, angular separation, and the fraction of an
 * occulted disk (the Sun) that stays lit behind an occulder — ombre and pénombre together, from the
 * classic circle-circle intersection area, not a hard step.
 *
 * <p>This is the CPU-side reference for {@code MatDefs/Light/WrapLighting.frag}'s per-fragment
 * occlusion test (`docs/eclipses/01-decoupage.md`, L1): the shader evaluates the same formula per
 * fragment, this class evaluates it once for a single point and is what the unit tests — including
 * the agreement check against Orekit's own {@code EclipseDetector} — exercise directly.
 */
public final class EclipseGeometry {

  private EclipseGeometry() {}

  /**
   * The angular radius of a sphere as seen from a distance.
   *
   * <p>Clamped to {@code pi/2} when {@code distanceMeters <= bodyRadiusMeters} — the observer is
   * inside or on the body — rather than returning {@code asin}'s {@code NaN} outside its domain.
   *
   * @param bodyRadiusMeters the sphere's radius, in meters
   * @param distanceMeters the distance from the observer to the sphere's centre, in meters
   * @return the angular radius, in radians
   */
  public static double angularRadius(double bodyRadiusMeters, double distanceMeters) {
    return FastMath.asin(FastMath.min(1.0, bodyRadiusMeters / distanceMeters));
  }

  /**
   * The Sun's angular radius as seen from a given distance from it.
   *
   * @param sunDistanceMeters the distance to the Sun's centre, in meters
   * @return the Sun's angular radius, in radians
   */
  public static double sunApparentRadius(double sunDistanceMeters) {
    return angularRadius(PlanetRadius.radiusFor(SolarSystemBody.SUN), sunDistanceMeters);
  }

  /**
   * The angular separation between the direction to an occluder and the direction to the Sun, as
   * seen from a common point.
   *
   * @param fromPointToOccluder vector from the observing point to the occluder's centre
   * @param fromPointToSun vector from the observing point to the Sun's centre
   * @return the separation, in radians
   */
  public static double separationRadians(Vector3D fromPointToOccluder, Vector3D fromPointToSun) {
    return Vector3D.angle(fromPointToOccluder, fromPointToSun);
  }

  /**
   * The fraction of the Sun's disk that stays visible past an occluder, from the area of
   * intersection of two circles — the occluder's apparent disk and the Sun's apparent disk,
   * {@code separationRadians} apart. {@code 1.0} is fully lit, {@code 0.0} is totality; anything in
   * between is the penumbra.
   *
   * @param separationRadians angular separation between the occluder and the Sun, in radians
   * @param occluderApparentRadiusRadians the occluder's angular radius, in radians
   * @param sunApparentRadiusRadians the Sun's angular radius, in radians
   * @return the illuminated fraction, in {@code [0, 1]}
   */
  public static double illuminationFraction(
      double separationRadians,
      double occluderApparentRadiusRadians,
      double sunApparentRadiusRadians) {
    double d = separationRadians;
    double rs = sunApparentRadiusRadians;
    double ro = occluderApparentRadiusRadians;

    if (d >= rs + ro) {
      return 1.0;
    }
    if (d <= FastMath.abs(rs - ro)) {
      return ro >= rs ? 0.0 : 1.0 - (ro * ro) / (rs * rs);
    }

    double d2 = d * d;
    double rs2 = rs * rs;
    double ro2 = ro * ro;
    double part1 = rs2 * FastMath.acos((d2 + rs2 - ro2) / (2.0 * d * rs));
    double part2 = ro2 * FastMath.acos((d2 + ro2 - rs2) / (2.0 * d * ro));
    double part3 =
        0.5
            * FastMath.sqrt(
                FastMath.max(
                    0.0, (-d + rs + ro) * (d + rs - ro) * (d - rs + ro) * (d + rs + ro)));
    double overlapArea = part1 + part2 - part3;
    double occludedFraction = overlapArea / (FastMath.PI * rs2);

    return FastMath.max(0.0, FastMath.min(1.0, 1.0 - occludedFraction));
  }
}
