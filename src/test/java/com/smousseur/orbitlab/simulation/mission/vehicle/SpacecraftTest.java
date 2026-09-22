package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Test
  void disposalReserveCountsInMassOnly() {
    Spacecraft s = new Spacecraft(1_000, 100, 50, ENGINE, null, 200);
    assertEquals(1_250.0, s.getMass(), 1e-9, "dry 1000 + load 50 + reserve 200");
    assertEquals(50.0, s.propellantLoad(), 1e-9);
    assertEquals(100.0, s.propellantCapacity(), 1e-9);
    assertEquals(200.0, s.disposalReserve(), 1e-9);
  }

  @Test
  void disposalReserveIsNotUsablePropellant() {
    // A reserve with no usable load must not flip the ascent trim on: hasUsablePropellant
    // reads the load alone, and the reserve rides as mass the launcher lifts.
    Spacecraft s = new Spacecraft(1_000, 100, 0, ENGINE, null, 200);
    assertFalse(s.hasUsablePropellant());
    assertEquals(1_200.0, s.getMass(), 1e-9);
  }

  @Test
  void defaultConstructorCarriesNoReserve() {
    Spacecraft s = new Spacecraft(2_000, 100, 50, ENGINE);
    assertEquals(0.0, s.disposalReserve(), 1e-9);
    assertEquals(2_050.0, s.getMass(), 1e-9);
  }
}
