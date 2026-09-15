package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;

class SeparationImpulseTest {

  private static final Vector3D VELOCITY = new Vector3D(0.0, 7_000.0, 0.0);

  private static double along(Vector3D impulse, Vector3D velocity) {
    return impulse.dotProduct(velocity.normalize());
  }

  private static Vector3D fan(Vector3D impulse, Vector3D velocity) {
    Vector3D vhat = velocity.normalize();
    return impulse.subtract(vhat.scalarMultiply(impulse.dotProduct(vhat)));
  }

  @Test
  void singleStageGetsPureRetro() {
    Vector3D impulse = SeparationImpulse.of(VELOCITY, 1, 1);
    assertTrue(along(impulse, VELOCITY) < 0.0, "retro: opposite the velocity");
    assertEquals(0.0, fan(impulse, VELOCITY).getNorm(), 1e-9, "no fan for a single exemplar");
  }

  @Test
  void aBoosterGetsRetroPlusAPerpendicularFan() {
    Vector3D impulse = SeparationImpulse.of(VELOCITY, 1, 2);
    assertTrue(along(impulse, VELOCITY) < 0.0, "retro component present");
    Vector3D fan = fan(impulse, VELOCITY);
    assertTrue(fan.getNorm() > 0.0, "fan component present");
    assertEquals(0.0, fan.dotProduct(VELOCITY.normalize()), 1e-9, "fan perpendicular to velocity");
  }

  @Test
  void twoOpposedBoostersFanApart() {
    Vector3D fan1 = fan(SeparationImpulse.of(VELOCITY, 1, 2), VELOCITY);
    Vector3D fan2 = fan(SeparationImpulse.of(VELOCITY, 2, 2), VELOCITY);
    assertTrue(fan1.dotProduct(fan2) < 0.0, "azimuth 0 and pi point opposite ways");
  }

  @Test
  void isDeterministic() {
    assertEquals(SeparationImpulse.of(VELOCITY, 2, 4), SeparationImpulse.of(VELOCITY, 2, 4));
  }
}
