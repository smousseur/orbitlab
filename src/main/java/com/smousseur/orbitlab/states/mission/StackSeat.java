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
 * vehicle body frame (axial along the drawn axis, lateral out to the side). It is added to the
 * drawn position of both the mesh and its ribbon tip so the two stay together, and never enters the
 * propagation: the CoM trajectory the optimizer flew and the gates pin is untouched.
 *
 * <p>The axial lift follows the axis the object is drawn along ({@link DrawnFlightAxis}), so a
 * silhouette is lifted along its own nose. The lateral fan stays on the velocity: it reuses {@link
 * SeparationImpulse#fanDirection} so a booster is drawn on the same flank the velocity kick pushes
 * it toward — start and drift agree.
 */
final class StackSeat {

  /** Below this speed (m/s) the velocity has no direction, so no lateral fan is drawn. */
  private static final double MIN_SPEED = 1e-3;

  /** Below this norm an axis has no direction, so no axial lift is drawn. */
  private static final double MIN_AXIS_NORM = 1e-9;

  private StackSeat() {}

  /**
   * The body-frame seat as a world offset in metres, in the frame {@code axis}, {@code velocity}
   * and {@code position} are expressed in.
   *
   * @param axis the axis the object is drawn along — its nose points this way; {@link
   *     Vector3D#ZERO} for a seat with no axial lift
   * @param velocity the object's velocity, the reference of the lateral fan (never converted here)
   * @param position the object's position, only the fallback reference for the lateral fan when the
   *     flight runs along the celestial pole (see {@link SeparationImpulse#fanDirection})
   * @param axialMeters distance to lift the base along the axis (the nose)
   * @param lateralMeters distance out to the side, in the plane perpendicular to the flight
   * @param fanIndex the 1-based index of this exemplar among {@code fanCount}, for the lateral fan
   * @param fanCount how many exemplars share the lateral fan (at least 1)
   * @return the offset to add to the drawn position, in metres
   */
  static Vector3D offset(
      Vector3D axis,
      Vector3D velocity,
      Vector3D position,
      double axialMeters,
      double lateralMeters,
      int fanIndex,
      int fanCount) {
    Vector3D seat = Vector3D.ZERO;
    if (axialMeters != 0.0 && axis.getNorm() >= MIN_AXIS_NORM) {
      seat = axis.normalize().scalarMultiply(axialMeters);
    }
    if (lateralMeters != 0.0 && velocity.getNorm() >= MIN_SPEED) {
      seat =
          seat.add(
              SeparationImpulse.fanDirection(velocity, position, fanIndex, fanCount)
                  .scalarMultiply(lateralMeters));
    }
    return seat;
  }
}
