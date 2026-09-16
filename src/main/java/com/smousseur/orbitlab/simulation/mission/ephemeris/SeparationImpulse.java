package com.smousseur.orbitlab.simulation.mission.ephemeris;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;

/**
 * The kinematics a jettisoned object gets at separation (PHY-5 / L2, spec {@code
 * docs/multi-objets/04-conception-L2.md} §2.4): a velocity kick — a retro component so the debris
 * falls behind the primary that keeps flying its optimized trajectory, plus, for a multi-exemplar
 * jettison, a fan that opens the exemplars apart. Cosmetic, tunable, never a physical claim (D4).
 *
 * <p>The fan opens in the <em>local orbital frame</em> — cross-range and radial about the flight
 * direction — so the exemplars peel to the sides where they were mounted rather than along a pole-
 * referenced plane that swings with the launch azimuth ({@code fanDirection}). That same basis is
 * reused by the render-only seat that draws each piece where it detached ({@code StackSeat}, PHY-5
 * / L6, spec {@code docs/multi-objets/08-conception-L6.md}), so start and drift agree.
 */
public final class SeparationImpulse {

  /** Retro magnitude (m/s): how hard a debris is pushed back along the flight path. */
  private static final double RETRO_MAG = 3.0;

  /** Fan magnitude (m/s): how hard each exemplar of a block is pushed out sideways. */
  private static final double FAN_MAG = 10.0;

  private SeparationImpulse() {}

  /**
   * The separation Δv for one exemplar of a jettison.
   *
   * @param velocity the object's velocity at separation (never zero — a separation has flight
   *     speed)
   * @param position the object's position at separation, for the local orbital frame the fan opens
   *     in
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
   * exemplarIndex} of a block fans out along — cross-range at azimuth 0, so two boosters peel to
   * opposite flanks in the local horizontal rather than toward the celestial pole. Public because
   * {@code StackSeat} draws each exemplar on the same side it is pushed toward.
   *
   * @param velocity the object's velocity, defining the flight direction
   * @param position the object's position, defining the local vertical (radial)
   * @param exemplarIndex the 1-based index of this exemplar among {@code multiplicity}
   * @param multiplicity how many identical exemplars share the fan (at least 1)
   * @return the unit fan direction for that exemplar
   */
  public static Vector3D fanDirection(
      Vector3D velocity, Vector3D position, int exemplarIndex, int multiplicity) {
    Vector3D flight = velocity.normalize();
    Vector3D crossRange = Vector3D.crossProduct(flight, radial(position, flight));
    // Near-vertical flight (radial ∥ flight) leaves no orbital frame; any perpendicular serves,
    // since the exemplars only need to spread evenly, not point a particular way.
    if (crossRange.getNorm() < 1e-6) {
      Vector3D reference = FastMath.abs(flight.getZ()) > 0.9 ? Vector3D.PLUS_I : Vector3D.PLUS_K;
      crossRange = Vector3D.crossProduct(flight, reference);
    }
    crossRange = crossRange.normalize();
    Vector3D inPlane = Vector3D.crossProduct(crossRange, flight).normalize();
    double azimuth = 2.0 * FastMath.PI * (exemplarIndex - 1) / multiplicity;
    return crossRange
        .scalarMultiply(FastMath.cos(azimuth))
        .add(inPlane.scalarMultiply(FastMath.sin(azimuth)));
  }

  private static Vector3D radial(Vector3D position, Vector3D fallback) {
    double norm = position.getNorm();
    return norm < 1e-6 ? fallback : position.scalarMultiply(1.0 / norm);
  }
}
