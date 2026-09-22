package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissionTypeTest {

  /**
   * PHY-10 / L2 — a type delivers its payload to a stable orbit exactly when the payload stays
   * there and must be able to dispose of itself. A lunar flyby does not stay, so it is spared.
   */
  @Test
  void deliversToStableOrbit_trueForOrbitalTypes_falseForFlyby() {
    assertTrue(MissionType.LEO.deliversToStableOrbit());
    assertTrue(MissionType.GEO.deliversToStableOrbit());
    assertTrue(MissionType.LUNAR_ORBIT.deliversToStableOrbit());
    assertFalse(MissionType.LUNAR_FLYBY.deliversToStableOrbit(), "a flyby does not stay in orbit");
  }

  /**
   * The two predicates answer different questions and coincide everywhere but LEO: LEO stays in
   * orbit (so it delivers to a stable orbit) yet the mission hands it no in-flight burn.
   */
  @Test
  void deliversToStableOrbit_differsFromRequiresPayloadPropulsion_onlyOnLeo() {
    assertTrue(MissionType.LEO.deliversToStableOrbit());
    assertFalse(MissionType.LEO.requiresPayloadPropulsion());
    for (MissionType type : MissionType.values()) {
      if (type != MissionType.LEO) {
        assertEquals(
            type.requiresPayloadPropulsion(),
            type.deliversToStableOrbit(),
            () -> "the two predicates must agree off LEO, disagreed on " + type);
      }
    }
  }
}
