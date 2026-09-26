package com.smousseur.orbitlab.engine.scene.planet;

import com.jme3.math.Quaternion;
import com.smousseur.orbitlab.app.view.AxisConvention;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.EllipsoidGlobe;
import java.util.Objects;
import java.util.Optional;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Brings a point near the Earth's ground onto the globe as it is drawn: the WGS84 ground the
 * physics lands on runs up to half a kilometre above the facets of {@link EllipsoidGlobe}, and a
 * camera ten metres from a landed piece would see it float by that much.
 *
 * <p>The displacement is along the ellipsoid's normal — purely vertical, so the object stays on its
 * own texel — by the facet depth under it, fully at the ground and fading linearly to nothing at
 * {@link #BLEND_HEIGHT_METERS}, so lift-off and touchdown stay continuous. Render-only: the
 * propagated sample is never touched, only the point it is drawn from.
 *
 * <p>One instance holds the drawn rotation of one frame ({@link #at}), so a ribbon correcting
 * several vertices reads the rotation once.
 */
public final class GroundCorrection {

  /**
   * The height over which the correction fades out: ten times the grid's deepest facet (484 m). An
   * object at height h below it is drawn h·(1 + δ/H) above the facet under it, a vertical
   * distortion of 10 % at most.
   */
  public static final double BLEND_HEIGHT_METERS = 5_000.0;

  private static final double EQUATORIAL_RADIUS = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
  private static final EllipsoidGlobe GLOBE = EllipsoidGlobe.earth();
  private static final AxisConvention AXES =
      RenderContext.planet(SolarSystemBody.EARTH).axisConvention();

  private final Quaternion drawn;
  private final Quaternion inverse;

  private GroundCorrection(Quaternion drawn) {
    this.drawn = drawn.clone();
    this.inverse = new Quaternion(-drawn.getX(), -drawn.getY(), -drawn.getZ(), drawn.getW());
  }

  /**
   * The correction for the globe as drawn at {@code date}.
   *
   * @param date the current simulation date
   * @return the correction, or empty while the drawn rotation is not available yet
   */
  public static Optional<GroundCorrection> at(AbsoluteDate date) {
    return PlanetDrawnRotation.at(SolarSystemBody.EARTH, date).map(GroundCorrection::new);
  }

  /**
   * The correction for a drawn rotation already in hand — {@link #at} is the production entry.
   *
   * @param drawnRotation the Earth globe's drawn rotation
   * @return the correction
   */
  public static GroundCorrection withDrawnRotation(Quaternion drawnRotation) {
    return new GroundCorrection(Objects.requireNonNull(drawnRotation, "drawnRotation"));
  }

  /**
   * Whether a point can be within {@link #BLEND_HEIGHT_METERS} of the ground: the ellipsoid lies
   * inside the sphere of its equatorial radius, so beyond that sphere plus the blend nothing is
   * corrected. A norm, which the Earth's rotation leaves unchanged.
   *
   * @param earthCentredMeters a point about the Earth's centre, in metres
   * @return {@code false} when the point certainly needs no correction
   */
  public static boolean mayReach(Vector3D earthCentredMeters) {
    return earthCentredMeters.getNorm() < EQUATORIAL_RADIUS + BLEND_HEIGHT_METERS;
  }

  /**
   * The point to draw for a point about the Earth's centre.
   *
   * @param earthCentredMeters the point, ICRF-oriented, in metres
   * @return the corrected point, or the very same instance when it is out of the blend
   */
  public Vector3D correct(Vector3D earthCentredMeters) {
    if (!mayReach(earthCentredMeters)) {
      return earthCentredMeters;
    }
    EllipsoidGlobe.Geodetic geodetic = geodeticOf(earthCentredMeters);
    double heightMeters = geodetic.height() * EQUATORIAL_RADIUS;
    if (heightMeters >= BLEND_HEIGHT_METERS) {
      return earthCentredMeters;
    }
    double weight = Math.min(1.0, 1.0 - heightMeters / BLEND_HEIGHT_METERS);
    double depthMeters =
        GLOBE.facetDepth(geodetic.latitudeDeg(), geodetic.azimuthDeg()) * EQUATORIAL_RADIUS;
    return earthCentredMeters.subtract(weight * depthMeters, upAt(geodetic));
  }

  /**
   * The drawn globe's geodetic vertical under a point — the ellipsoid normal at its latitude and
   * azimuth — in the ICRF axes the point is given in: the up a landed object lies against.
   *
   * @param earthCentredMeters the point, ICRF-oriented, in metres
   * @return the unit vertical
   */
  public Vector3D up(Vector3D earthCentredMeters) {
    return upAt(geodeticOf(earthCentredMeters));
  }

  private EllipsoidGlobe.Geodetic geodeticOf(Vector3D earthCentredMeters) {
    return GLOBE.geodetic(
        rotate(inverse, AXES.icrfToJme(earthCentredMeters))
            .scalarMultiply(1.0 / EQUATORIAL_RADIUS));
  }

  private Vector3D upAt(EllipsoidGlobe.Geodetic geodetic) {
    return AXES.jmeToIcrf(
        rotate(drawn, GLOBE.normal(geodetic.latitudeDeg(), geodetic.azimuthDeg())));
  }

  /**
   * Rotates {@code v} by {@code q} as {@link Quaternion#mult(com.jme3.math.Vector3f)} does, but in
   * double and normalised: a float quaternion is unit only to 1e-7, which at the Earth's radius
   * would leave the point 0.6 m off a pure rotation.
   */
  static Vector3D rotate(Quaternion q, Vector3D v) {
    double w = q.getW();
    double x = q.getX();
    double y = q.getY();
    double z = q.getZ();
    double norm2 = w * w + x * x + y * y + z * z;
    double dot = x * v.getX() + y * v.getY() + z * v.getZ();
    double scalar = w * w - (x * x + y * y + z * z);
    double cx = y * v.getZ() - z * v.getY();
    double cy = z * v.getX() - x * v.getZ();
    double cz = x * v.getY() - y * v.getX();
    return new Vector3D(
        (scalar * v.getX() + 2.0 * dot * x + 2.0 * w * cx) / norm2,
        (scalar * v.getY() + 2.0 * dot * y + 2.0 * w * cy) / norm2,
        (scalar * v.getZ() + 2.0 * dot * z + 2.0 * w * cz) / norm2);
  }
}
