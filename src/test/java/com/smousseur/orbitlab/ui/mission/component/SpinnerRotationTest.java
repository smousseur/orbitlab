package com.smousseur.orbitlab.ui.mission.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the spinner's cadence. The geometry it drives needs a JME context and is not
 * covered; everything that can be wrong about <em>when</em> it turns is here.
 */
class SpinnerRotationTest {

  private static final float EPS = 1e-5f;

  @Test
  void aFrameShorterThanThePeriodTakesNoStep() {
    SpinnerRotation rotation = new SpinnerRotation();

    assertFalse(rotation.advance(0.05f));
    assertEquals(0, rotation.step());
  }

  @Test
  void reachingThePeriodTakesExactlyOneStep() {
    SpinnerRotation rotation = new SpinnerRotation();

    assertTrue(rotation.advance(SpinnerRotation.STEP_SECONDS));
    assertEquals(1, rotation.step());
  }

  @Test
  void severalShortFramesAccumulateIntoOneStep() {
    SpinnerRotation rotation = new SpinnerRotation();

    assertFalse(rotation.advance(0.04f));
    assertFalse(rotation.advance(0.04f));
    assertTrue(rotation.advance(0.04f));
    assertEquals(1, rotation.step());
  }

  @Test
  void aLongFrameTakesEveryStepItCoversRatherThanDroppingThem() {
    SpinnerRotation rotation = new SpinnerRotation();

    assertTrue(rotation.advance(0.25f));
    assertEquals(2, rotation.step());
  }

  @Test
  void aFullTurnComesBackToTheFirstStep() {
    SpinnerRotation rotation = new SpinnerRotation();

    for (int i = 0; i < SpinnerRotation.STEPS_PER_TURN; i++) {
      rotation.advance(SpinnerRotation.STEP_SECONDS);
    }

    assertEquals(0, rotation.step());
    assertEquals(0f, rotation.angleRadians(), EPS);
  }

  @Test
  void theAngleTurnsClockwiseByOneSpokePerStep() {
    SpinnerRotation rotation = new SpinnerRotation();
    float spoke = (float) (2.0 * Math.PI / SpinnerRotation.STEPS_PER_TURN);

    rotation.advance(SpinnerRotation.STEP_SECONDS);

    assertEquals(-spoke, rotation.angleRadians(), EPS);
  }

  @Test
  void aNonPositiveFrameTimeIsIgnored() {
    SpinnerRotation rotation = new SpinnerRotation();

    assertFalse(rotation.advance(0f));
    assertFalse(rotation.advance(-1f));
    assertEquals(0, rotation.step());
  }
}
