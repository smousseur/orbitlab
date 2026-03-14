package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.EphemerisInterpolator;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.utils.PVCoordinates;

/**
 * A decoded ephemeris chunk containing pre-parsed position/velocity and rotation data for a single
 * celestial body over a fixed time interval.
 *
 * <p>Chunks are decoded from the binary dataset format and cached by {@link BodyFile}. Sampling
 * methods use Hermite interpolation for position/velocity and spherical linear interpolation
 * (SLERP) for rotations to produce smooth results between stored data points.
 */
final class DecodedChunk {
  @SuppressWarnings("unused")
  private final SolarSystemBody body;

  @SuppressWarnings("unused")
  private final double chunkStartOffsetSeconds;

  @SuppressWarnings("unused")
  private final double chunkDurationSeconds;

  private final EphemerisV1Parser.PvBlock pv;
  private final EphemerisV1Parser.RotBlock rot;

  DecodedChunk(
      SolarSystemBody body,
      double chunkStartOffsetSeconds,
      double chunkDurationSeconds,
      EphemerisV1Parser.PvBlock pv,
      EphemerisV1Parser.RotBlock rot) {
    this.body = body;
    this.chunkStartOffsetSeconds = chunkStartOffsetSeconds;
    this.chunkDurationSeconds = chunkDurationSeconds;
    this.pv = pv;
    this.rot = rot;
  }

  /**
   * Samples the position and velocity at the given time offset using Hermite interpolation.
   *
   * @param offsetSeconds elapsed seconds from the dataset start epoch
   * @return the interpolated position and velocity coordinates in ICRF
   */
  PVCoordinates samplePv(double offsetSeconds) {
    int i0 = (int) Math.floor((offsetSeconds - pv.t0()) / pv.dt());
    i0 = clamp(i0, 0, pv.n() - 2);
    int i1 = i0 + 1;

    double t0 = pv.t0() + i0 * pv.dt();
    double tau = (offsetSeconds - t0) / pv.dt();

    Vector3D p0 = vec3(pv.raw(), i0 * 6);
    Vector3D v0 = vec3(pv.raw(), i0 * 6 + 3);
    Vector3D p1 = vec3(pv.raw(), i1 * 6);
    Vector3D v1 = vec3(pv.raw(), i1 * 6 + 3);

    Vector3D p = EphemerisInterpolator.hermitePosition(p0, v0, p1, v1, pv.dt(), tau);
    Vector3D v = EphemerisInterpolator.hermiteVelocity(p0, v0, p1, v1, pv.dt(), tau);

    return new PVCoordinates(p, v);
  }

  /**
   * Samples the body rotation at the given time offset using spherical linear interpolation (SLERP).
   *
   * @param offsetSeconds elapsed seconds from the dataset start epoch
   * @return the interpolated rotation quaternion
   */
  Rotation sampleRot(double offsetSeconds) {
    int i0 = (int) Math.floor((offsetSeconds - rot.t0()) / rot.dt());
    i0 = clamp(i0, 0, rot.n() - 2);
    int i1 = i0 + 1;

    double t0 = rot.t0() + i0 * rot.dt();
    double tau = (offsetSeconds - t0) / rot.dt();

    Rotation r0 = quat(rot.rawQuat(), i0 * 4);
    Rotation r1 = quat(rot.rawQuat(), i1 * 4);

    return EphemerisInterpolator.slerp(r0, r1, tau);
  }

  private static Vector3D vec3(double[] a, int off) {
    return new Vector3D(a[off], a[off + 1], a[off + 2]);
  }

  private static Rotation quat(double[] a, int off) {
    return new Rotation(a[off], a[off + 1], a[off + 2], a[off + 3], true);
  }

  private static int clamp(int x, int lo, int hi) {
    if (x < lo) return lo;
    if (x > hi) return hi;
    return x;
  }
}
