package com.smousseur.orbitlab.simulation.ephemeris;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Shared interpolation utilities for ephemeris data (position, velocity, rotation).
 *
 * <p>Used by {@link SlidingWindowEphemerisBuffer} and
 * {@code DatasetEphemerisSource.DecodedChunk}.
 */
public final class EphemerisInterpolator {

  private EphemerisInterpolator() {}

  /**
   * Cubic Hermite position interpolation.
   *
   * @param p0 position at t=0
   * @param v0 velocity at t=0
   * @param p1 position at t=1
   * @param v1 velocity at t=1
   * @param dt time interval (seconds) between t=0 and t=1
   * @param t normalized time in [0, 1]
   * @return interpolated position
   */
  public static Vector3D hermitePosition(
      Vector3D p0, Vector3D v0, Vector3D p1, Vector3D v1, double dt, double t) {
    double t2 = t * t;
    double t3 = t2 * t;

    double h00 = 2 * t3 - 3 * t2 + 1;
    double h10 = t3 - 2 * t2 + t;
    double h01 = -2 * t3 + 3 * t2;
    double h11 = t3 - t2;

    return new Vector3D(h00, p0, h10 * dt, v0, h01, p1, h11 * dt, v1);
  }

  /**
   * Cubic Hermite velocity interpolation (derivative of {@link #hermitePosition}).
   *
   * @param p0 position at t=0
   * @param v0 velocity at t=0
   * @param p1 position at t=1
   * @param v1 velocity at t=1
   * @param dt time interval (seconds) between t=0 and t=1
   * @param t normalized time in [0, 1]
   * @return interpolated velocity
   */
  public static Vector3D hermiteVelocity(
      Vector3D p0, Vector3D v0, Vector3D p1, Vector3D v1, double dt, double t) {
    double t2 = t * t;

    double dh00 = 6 * t2 - 6 * t;
    double dh10 = 3 * t2 - 4 * t + 1;
    double dh01 = -6 * t2 + 6 * t;
    double dh11 = 3 * t2 - 2 * t;

    double invDt = 1.0 / dt;

    return new Vector3D(dh00 * invDt, p0, dh10, v0, dh01 * invDt, p1, dh11, v1);
  }

  /**
   * Quaternion spherical linear interpolation (SLERP) for Hipparchus {@link Rotation}.
   *
   * <p>Rotation stores quaternion components: q0 = scalar, q1/q2/q3 = vector part.
   *
   * @param r0 start rotation
   * @param r1 end rotation
   * @param t normalized time in [0, 1]
   * @return interpolated rotation
   */
  public static Rotation slerp(Rotation r0, Rotation r1, double t) {
    if (t <= 0.0) return r0;
    if (t >= 1.0) return r1;

    double q00 = r0.getQ0(), q01 = r0.getQ1(), q02 = r0.getQ2(), q03 = r0.getQ3();
    double q10 = r1.getQ0(), q11 = r1.getQ1(), q12 = r1.getQ2(), q13 = r1.getQ3();

    double dot = q00 * q10 + q01 * q11 + q02 * q12 + q03 * q13;
    if (dot < 0.0) {
      dot = -dot;
      q10 = -q10;
      q11 = -q11;
      q12 = -q12;
      q13 = -q13;
    }

    if (dot > 0.9995) {
      // Quaternions are very close — linear interpolation is accurate enough.
      double a = 1.0 - t;
      double b = t;
      return new Rotation(a * q00 + b * q10, a * q01 + b * q11, a * q02 + b * q12, a * q03 + b * q13, true);
    }

    double theta0 = Math.acos(dot);
    double sinTheta0 = Math.sin(theta0);
    double theta = theta0 * t;
    double sinTheta = Math.sin(theta);

    double sA = Math.sin(theta0 - theta) / sinTheta0;
    double sB = sinTheta / sinTheta0;

    return new Rotation(sA * q00 + sB * q10, sA * q01 + sB * q11, sA * q02 + sB * q12, sA * q03 + sB * q13, true);
  }
}
