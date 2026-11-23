package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The plasma's shape and tint read off a speed and an altitude, as the prototype's {@code
 * physics()} computes them. Every expected value is that function evaluated independently, at
 * states the re-entry measure recorded on the production path.
 */
class ReentryPlasmaShapeTest {

  private static final double SOUND_SPEED = 290.0;

  /** A Falcon Heavy booster at its heating peak: Mach 15.1 at 24.5 km. */
  @Test
  void aBoosterAtItsPeakGetsThePrototypesShock() {
    ReentryPlasmaShape shape = ReentryPlasmaShape.of(4_379.0, 24_500.0, SOUND_SPEED);

    assertEquals(0.775989f, shape.xA(), 1e-6f);
    assertEquals(257.4783f, shape.k(), 1e-3f);
    assertEquals(0.002589215f, shape.a(), 1e-8f);
    assertEquals(1.5f, shape.rc(), 0f);
    assertEquals(1.033545f, shape.temperature(), 1e-6f);
  }

  /** Below 60 km at Mach 15 the prototype's violet share is negative, and is held at zero. */
  @Test
  void aLowPeakHasNoViolet() {
    assertEquals(0f, ReentryPlasmaShape.of(4_379.0, 24_500.0, SOUND_SPEED).violet(), 0f);
  }

  /** A Falcon Heavy core entering at 90.5 km, Mach 24.1: a violet share of 1.41, held at one. */
  @Test
  void aHighFastEntryIsAllViolet() {
    ReentryPlasmaShape shape = ReentryPlasmaShape.of(6_989.0, 90_500.0, SOUND_SPEED);

    assertEquals(1f, shape.violet(), 0f);
    assertEquals(0.7287726f, shape.xA(), 1e-6f);
    assertEquals(1.080648f, shape.temperature(), 1e-6f);
  }

  /** An Ariane 64 booster entering at 56.4 km, Mach 10.4. */
  @Test
  void aSlowerEntryIsPartlyViolet() {
    ReentryPlasmaShape shape = ReentryPlasmaShape.of(3_016.0, 56_400.0, SOUND_SPEED);

    assertEquals(0.22f, shape.violet(), 1e-6f);
    assertEquals(0.8318202f, shape.temperature(), 1e-6f);
  }

  /** {@code asin(1/M)} has no value under Mach 1: the shape holds the one at Mach 1.5 instead. */
  @Test
  void underMachOneAndAHalfTheShapeIsHeldThere() {
    ReentryPlasmaShape shape = ReentryPlasmaShape.of(100.0, 30_000.0, SOUND_SPEED);

    assertEquals(1.977244f, shape.xA(), 1e-5f);
    assertEquals(1.417769f, shape.k(), 1e-5f);
    assertEquals(0.4702222f, shape.a(), 1e-6f);
  }

  /** The Mach number is the speed over the given speed of sound, not over a constant. */
  @Test
  void theMachNumberIsTakenAgainstTheGivenSpeedOfSound() {
    ReentryPlasmaShape shape = ReentryPlasmaShape.of(8_758.0, 24_500.0, 580.0);

    assertEquals(0.775989f, shape.xA(), 1e-6f);
    assertEquals(257.4783f, shape.k(), 1e-3f);
  }
}
