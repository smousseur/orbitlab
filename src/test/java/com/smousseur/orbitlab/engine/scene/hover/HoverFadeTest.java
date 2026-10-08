package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.engine.HoverConfig;
import org.junit.jupiter.api.Test;

/** The hover fade is the mockup's exponential smoothing, toward 1 when lit and 0 otherwise. */
class HoverFadeTest {

  private static final float FRAME = 1f / 60f;
  private static final float TOLERANCE = 1e-4f;

  @Test
  void risesWithTheFadeInTimeConstant() {
    HoverFade fade = HoverFade.of(HoverConfig.defaults());

    fade.advance(true, 0.05f);
    assertEquals(1f - (float) Math.exp(-1), fade.intensity(), TOLERANCE, "one time constant");

    HoverFade framed = HoverFade.of(HoverConfig.defaults());
    for (int frame = 0; frame < 9; frame++) {
      framed.advance(true, FRAME);
    }
    assertTrue(framed.intensity() >= 0.95f, "150 ms in: " + framed.intensity());
  }

  @Test
  void fallsWithTheFadeOutTimeConstant() {
    HoverFade fade = HoverFade.of(HoverConfig.defaults());
    fade.advance(true, 1f);
    assertEquals(1f, fade.intensity());

    fade.advance(false, 0.085f);

    assertEquals((float) Math.exp(-1), fade.intensity(), TOLERANCE);
  }

  @Test
  void snapsOnceWithinTheSnapOfItsTarget() {
    HoverFade fade = new HoverFade(0.05f, 0.085f, 0.1f);

    fade.advance(true, 0.1f);
    assertEquals(1f - (float) Math.exp(-2), fade.intensity(), TOLERANCE, "0.135 away: no snap");

    fade.advance(true, 0.1f);
    assertEquals(1f, fade.intensity(), "0.018 away: snapped onto the target exactly");
  }

  @Test
  void turnsBackFromWhereverItIsWhenTheTargetChanges() {
    HoverFade fade = HoverFade.of(HoverConfig.defaults());
    fade.advance(true, 0.05f);
    float halfway = fade.intensity();

    fade.advance(false, 0.085f);

    assertEquals(halfway * (float) Math.exp(-1), fade.intensity(), TOLERANCE);
  }

  @Test
  void aZeroFrameChangesNothing() {
    HoverFade fade = new HoverFade(0.05f, 0.085f, 0.1f);
    fade.advance(true, 0.001f);
    float before = fade.intensity();

    assertEquals(before, fade.advance(false, 0f));
    assertEquals(before, fade.advance(true, 0f));
  }
}
