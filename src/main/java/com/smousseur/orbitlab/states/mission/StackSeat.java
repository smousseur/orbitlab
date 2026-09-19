package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.simulation.mission.ephemeris.SeparationImpulse;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * The render-only offset a jettisoned piece or a shrunk primary silhouette is <em>drawn</em> at,
 * relative to the single propagated point the whole stack shares.
 *
 * <p>Every piece mesh is authored with its base at the origin, so base-at-anchor would pile them
 * all on the propagated point. This offset places each where it belongs — the primary shrinking
 * from the nose down, a booster on its flank, the upper stage up where it sat — expressed in the
 * vehicle body frame (axial along the flight direction, lateral out to the side). It is added to
 * the drawn position of both the mesh and its ribbon tip so the two stay together, and never enters
 * the propagation: the CoM trajectory the optimizer flew and the gates pin is untouched.
 *
 * <p>The lateral fan reuses {@link SeparationImpulse#fanDirection} so a booster is drawn on the
 * same flank the velocity kick pushes it toward — start and drift agree.
 */
final class StackSeat {

  /**
   * Below this speed (m/s) the flight direction is undefined, so no seat is drawn: on the pad the
   * silhouette is the full stack anyway, whose seat is zero.
   */
  private static final double MIN_SPEED = 1e-3;

  private StackSeat() {}

  /**
   * The body-frame seat as a world offset in metres, in the frame {@code velocity} and {@code
   * position} are expressed in.
   *
   * @param velocity the object's velocity — its nose points this way (never converted here)
   * @param position the object's position, only the fallback reference for the lateral fan when the
   *     flight runs along the celestial pole (see {@link SeparationImpulse#fanDirection})
   * @param axialMeters distance to lift the base along the flight direction (the nose)
   * @param lateralMeters distance out to the side, in the plane perpendicular to the flight
   * @param fanIndex the 1-based index of this exemplar among {@code fanCount}, for the lateral fan
   * @param fanCount how many exemplars share the lateral fan (at least 1)
   * @return the offset to add to the drawn position, in metres; zero when the object is at rest
   */
  static Vector3D offset(
      Vector3D velocity,
      Vector3D position,
      double axialMeters,
      double lateralMeters,
      int fanIndex,
      int fanCount) {
    if (velocity.getNorm() < MIN_SPEED) {
      return Vector3D.ZERO;
    }
    Vector3D seat = velocity.normalize().scalarMultiply(axialMeters);
    if (lateralMeters != 0.0) {
      seat =
          seat.add(
              SeparationImpulse.fanDirection(velocity, position, fanIndex, fanCount)
                  .scalarMultiply(lateralMeters));
    }
    return seat;
  }
}
