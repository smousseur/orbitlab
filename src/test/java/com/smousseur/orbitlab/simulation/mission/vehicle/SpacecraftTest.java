package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpacecraftTest {

  private static final PropulsionSystem ENGINE = PropulsionSystem.getSpacecraftPropulsion();

  @Test
  void legacyPayloadHasNoUsablePropellant() {
    assertFalse(Spacecraft.LEGACY.hasUsablePropellant());
  }

  @Test
  void loadedPayloadHasUsablePropellant() {
    assertTrue(new Spacecraft(2_000, 100, 50, ENGINE).hasUsablePropellant());
  }

  @Test
  void payloadLoadedToZeroHasNoUsablePropellant() {
    assertFalse(new Spacecraft(2_000, 100, 0, ENGINE).hasUsablePropellant());
  }

  @Test
  void payloadWithoutPropulsionHasNoUsablePropellant() {
    assertFalse(new Spacecraft(2_000, 100, 50, null).hasUsablePropellant());
  }
}
