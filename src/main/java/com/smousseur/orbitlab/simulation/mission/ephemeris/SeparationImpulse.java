package com.smousseur.orbitlab.simulation.mission.ephemeris;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;

/**
 * The velocity kick a jettisoned object gets at separation (PHY-5 / L2, spec {@code
 * docs/multi-objets/04-conception-L2.md} §2.4): a retro component so the debris falls behind the
 * primary that keeps flying its optimized trajectory, plus — for a multi-exemplar jettison — a fan
 * that opens the exemplars apart. Cosmetic, tunable, never a physical claim (D4).
 */
public final class SeparationImpulse {

  /** Retro magnitude (m/s): how hard a debris is pushed back along the flight path. */
  private static final double RETRO_MAG = 1.0;

  /** Fan magnitude (m/s): how hard each exemplar of a block is pushed out sideways. */
  private static final double FAN_MAG = 1.0;

  private SeparationImpulse() {}

  /**
   * The separation Δv for one exemplar of a jettison.
   *
   * @param velocity the object's velocity at separation (never zero — a separation has flight
   *     speed)
   * @param exemplarIndex the 1-based index of this exemplar among {@code multiplicity}
   * @param multiplicity how many identical exemplars are jettisoned together (at least 1)
   * @return the velocity kick to add to the exemplar's separation velocity
   */
  public static Vector3D of(Vector3D velocity, int exemplarIndex, int multiplicity) {
    Vector3D flightDirection = velocity.normalize();
    Vector3D retro = flightDirection.scalarMultiply(-RETRO_MAG);
    if (multiplicity <= 1) {
      return retro;
    }
    // An orthonormal basis of the plane perpendicular to the velocity. The reference is any vector
    // not colinear with the flight direction; the +Z axis serves, unless the flight is
    // near-vertical.
    Vector3D reference =
        FastMath.abs(flightDirection.getZ()) > 0.9 ? Vector3D.PLUS_I : Vector3D.PLUS_K;
    Vector3D perp1 = Vector3D.crossProduct(flightDirection, reference).normalize();
    Vector3D perp2 = Vector3D.crossProduct(flightDirection, perp1).normalize();
    double azimuth = 2.0 * FastMath.PI * (exemplarIndex - 1) / multiplicity;
    Vector3D fanDirection =
        perp1
            .scalarMultiply(FastMath.cos(azimuth))
            .add(perp2.scalarMultiply(FastMath.sin(azimuth)));
    return retro.add(fanDirection.scalarMultiply(FAN_MAG));
  }
}
