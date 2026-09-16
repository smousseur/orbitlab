package com.smousseur.orbitlab.simulation.mission.ephemeris;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;

/**
 * The kinematics a jettisoned object gets at separation (PHY-5 / L2, spec {@code
 * docs/multi-objets/04-conception-L2.md} §2.4): a velocity kick — a retro component so the debris
 * falls behind the primary that keeps flying its optimized trajectory, plus, for a multi-exemplar
 * jettison, a fan that opens the exemplars apart. Cosmetic, tunable, never a physical claim (D4).
 *
 * <p>The fan opens in the plane perpendicular to the flight, its azimuth referenced to
 * <em>celestial north</em> (ICRF {@code +Z}) and offset half a step ({@code fanDirection}). North
 * is the axis the full stack is drawn rolling about, so a jettisoned exemplar peels off along the
 * exact flank it occupied on the stack, and the half step lands the four Ariane boosters on the
 * diagonals their meshes are mounted at rather than on the cardinal axes. That same basis is reused
 * by the render-only seat and roll that draw each piece where it detached ({@code StackSeat}, PHY-5
 * / L6, spec {@code docs/multi-objets/08-conception-L6.md}), so start, drift and drawing agree
 * (PHY-5 / L7). This reverses the L2 choice of a radial (orbital-frame) reference, which did not
 * match the frame the stack is actually drawn in.
 */
public final class SeparationImpulse {

  /** Retro magnitude (m/s): how hard a debris is pushed back along the flight path. */
  private static final double RETRO_MAG = 3.0;

  /** Fan magnitude (m/s): how hard each exemplar of a block is pushed out sideways. */
  private static final double FAN_MAG = 10.0;

  /**
   * The roll reference the fan is built about: celestial north — ICRF {@code +Z}, which is JME
   * world up ({@code +Y}). This is the axis the full stack is drawn rolling about, so the fan
   * matches it.
   */
  private static final Vector3D CELESTIAL_NORTH = Vector3D.PLUS_K;

  private SeparationImpulse() {}

  /**
   * The separation Δv for one exemplar of a jettison.
   *
   * @param velocity the object's velocity at separation (never zero — a separation has flight
   *     speed)
   * @param position the object's position at separation, the fallback fan reference for flight
   *     along the celestial pole (see {@link #fanDirection})
   * @param exemplarIndex the 1-based index of this exemplar among {@code multiplicity}
   * @param multiplicity how many identical exemplars are jettisoned together (at least 1)
   * @return the velocity kick to add to the exemplar's separation velocity
   */
  public static Vector3D of(
      Vector3D velocity, Vector3D position, int exemplarIndex, int multiplicity) {
    Vector3D retro = velocity.normalize().scalarMultiply(-RETRO_MAG);
    if (multiplicity <= 1) {
      return retro;
    }
    return retro.add(
        fanDirection(velocity, position, exemplarIndex, multiplicity).scalarMultiply(FAN_MAG));
  }

  /**
   * The unit direction, in the plane perpendicular to the flight, that exemplar {@code
   * exemplarIndex} of a block fans out along. The plane's basis is built from celestial north (the
   * axis the stack is drawn rolling about), and the azimuth is offset half a step so a four-booster
   * block lands on the diagonals its meshes are mounted at. Public because {@code StackSeat} draws
   * — and the roll hint turns — each exemplar on the same flank it is pushed toward.
   *
   * @param velocity the object's velocity, defining the flight direction
   * @param position the object's position, used only as the fallback reference when the flight runs
   *     along the celestial pole and north defines no frame
   * @param exemplarIndex the 1-based index of this exemplar among {@code multiplicity}
   * @param multiplicity how many identical exemplars share the fan (at least 1)
   * @return the unit fan direction for that exemplar
   */
  public static Vector3D fanDirection(
      Vector3D velocity, Vector3D position, int exemplarIndex, int multiplicity) {
    Vector3D flight = velocity.normalize();
    Vector3D right = Vector3D.crossProduct(flight, CELESTIAL_NORTH);
    // Flight along the celestial pole leaves no north-referenced frame; the local radial serves as
    // the reference instead, the ring only needing to spread evenly, not point a particular way.
    if (right.getNorm() < 1e-6) {
      right = Vector3D.crossProduct(flight, radial(position, flight));
    }
    right = right.normalize();
    Vector3D down = Vector3D.crossProduct(flight, right).normalize();
    double azimuth =
        FastMath.PI / multiplicity + 2.0 * FastMath.PI * (exemplarIndex - 1) / multiplicity;
    return right
        .scalarMultiply(FastMath.cos(azimuth))
        .add(down.scalarMultiply(FastMath.sin(azimuth)));
  }

  private static Vector3D radial(Vector3D position, Vector3D fallback) {
    double norm = position.getNorm();
    return norm < 1e-6 ? fallback : position.scalarMultiply(1.0 / norm);
  }
}
