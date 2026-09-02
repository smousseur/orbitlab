package com.smousseur.orbitlab.engine.scene.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A ring's only possible defect is the plane it lies in, so its only possible correction is the
 * turn that brings that plane onto the globe's equator — an angle <em>and</em> an axis, since an
 * angle alone does not make a rotation.
 */
class RingAlignmentTest {

  private static final Vector3f POLE = new Vector3f(0f, 0f, 1f);

  @Test
  void anEquatorialRingNeedsNothing() {
    RingAlignment alignment = RingAlignment.between(ring(POLE), POLE);

    assertTrue(alignment.isAligned());
    assertEquals(0.0, alignment.angleDeg(), 1e-3);
  }

  /** The turn has to be the one that actually puts the ring back, axis included. */
  @Test
  void reportsTheTurnThatBringsTheRingOntoTheEquator() {
    Vector3f axis = new Vector3f(1f, 0f, 0f);
    Vector3f tilted = new Quaternion().fromAngleAxis(20f * FastMath.DEG_TO_RAD, axis).mult(POLE);

    RingAlignment alignment = RingAlignment.between(ring(tilted), POLE);

    assertFalse(alignment.isAligned());
    assertEquals(20.0, alignment.angleDeg(), 1e-3);
    Vector3f corrected =
        new Quaternion()
            .fromAngleAxis(alignment.angleDeg() * FastMath.DEG_TO_RAD, alignment.axis())
            .mult(tilted);
    assertEquals(1.0, corrected.dot(POLE), 1e-4, "the turn must land the normal on the pole");
  }

  /**
   * The sign of a plane's normal is arbitrary — a plane has no preferred side — so the same ring
   * measured the other way round must give the same correction. Taken naively it would give its
   * supplement instead, and a 13 degree defect would be reported as 167.
   */
  @Test
  void ignoresWhichWayRoundTheNormalWasMeasured() {
    Vector3f tilted =
        new Quaternion()
            .fromAngleAxis(20f * FastMath.DEG_TO_RAD, new Vector3f(1f, 0f, 0f))
            .mult(POLE);

    RingAlignment oneWay = RingAlignment.between(ring(tilted), POLE);
    RingAlignment theOther = RingAlignment.between(ring(tilted.negate()), POLE);

    assertEquals(oneWay.angleDeg(), theOther.angleDeg(), 1e-3);
  }

  private static RingPlane ring(Vector3f normal) {
    return new RingPlane(normal, 0f);
  }
}
