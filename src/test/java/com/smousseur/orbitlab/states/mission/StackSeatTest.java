package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

class StackSeatTest {

  private static final Vector3D VELOCITY = new Vector3D(0.0, 7_000.0, 0.0);
  private static final Vector3D POSITION = new Vector3D(7_000_000.0, 0.0, 0.0);

  /**
   * Same flight, a different radial: the lateral fan must not move, being north-referenced (L7).
   */
  private static final Vector3D ROTATED_POSITION = new Vector3D(0.0, 0.0, 7_000_000.0);

  private static double along(Vector3D offset, Vector3D velocity) {
    return offset.dotProduct(velocity.normalize());
  }

  private static Vector3D lateral(Vector3D offset, Vector3D velocity) {
    Vector3D vhat = velocity.normalize();
    return offset.subtract(vhat.scalarMultiply(offset.dotProduct(vhat)));
  }

  @Test
  void anEmptySeatIsZero() {
    assertEquals(Vector3D.ZERO, StackSeat.offset(VELOCITY, POSITION, 0.0, 0.0, 1, 1));
  }

  @Test
  void axialFollowsTheFlightDirection() {
    Vector3D offset = StackSeat.offset(VELOCITY, POSITION, 43.0, 0.0, 1, 1);
    assertEquals(43.0, along(offset, VELOCITY), 1e-9, "axial magnitude along the nose");
    assertEquals(0.0, lateral(offset, VELOCITY).getNorm(), 1e-9, "no lateral component");
  }

  @Test
  void lateralIsPerpendicularToTheFlight() {
    Vector3D offset = StackSeat.offset(VELOCITY, POSITION, 0.0, 4.0, 1, 2);
    assertEquals(0.0, along(offset, VELOCITY), 1e-9, "no axial component");
    assertEquals(
        4.0, lateral(offset, VELOCITY).getNorm(), 1e-9, "lateral magnitude out to the side");
  }

  @Test
  void twoBoostersSitOnOppositeFlanks() {
    Vector3D first = StackSeat.offset(VELOCITY, POSITION, 0.0, 4.0, 1, 2);
    Vector3D second = StackSeat.offset(VELOCITY, POSITION, 0.0, 4.0, 2, 2);
    assertTrue(first.dotProduct(second) < 0.0, "exemplars 1 and 2 of 2 point opposite ways");
  }

  @Test
  void theLateralFanIsReferencedToNorthNotTheRadial() {
    Vector3D atOneRadial = lateral(StackSeat.offset(VELOCITY, POSITION, 0.0, 4.0, 1, 2), VELOCITY);
    Vector3D atAnother =
        lateral(StackSeat.offset(VELOCITY, ROTATED_POSITION, 0.0, 4.0, 1, 2), VELOCITY);
    assertEquals(
        atOneRadial, atAnother, "the lateral fan follows celestial north, not the swinging radial");
  }

  @Test
  void axialAndLateralCombine() {
    Vector3D offset = StackSeat.offset(VELOCITY, POSITION, 43.0, 4.0, 1, 2);
    assertEquals(43.0, along(offset, VELOCITY), 1e-9, "axial component kept");
    assertEquals(4.0, lateral(offset, VELOCITY).getNorm(), 1e-9, "lateral component kept");
  }

  @Test
  void aStandstillGetsNoSeat() {
    assertEquals(Vector3D.ZERO, StackSeat.offset(Vector3D.ZERO, POSITION, 43.0, 4.0, 1, 2));
  }
}
