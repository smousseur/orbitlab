package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.engine.HoverConfig;
import org.junit.jupiter.api.Test;

/**
 * A mission icon fades with its own hover, and freezes its target with the planets' while the
 * camera turns or flies.
 */
class MissionIconHoverTest {

  private static final float LONG = 1f;

  @Test
  void followsItsOwnHover() {
    MissionIconHover hover = new MissionIconHover(HoverConfig.defaults());

    hover.setHovered(true);
    assertEquals(1f, hover.advance(false, LONG));

    hover.setHovered(false);
    assertEquals(0f, hover.advance(false, LONG));
  }

  @Test
  void keepsItsTargetWhileFrozen() {
    MissionIconHover hover = new MissionIconHover(HoverConfig.defaults());

    hover.setHovered(true);
    assertEquals(0f, hover.advance(true, LONG), "entered while frozen: stays out");

    assertEquals(1f, hover.advance(false, LONG), "thawed: lights up");

    hover.setHovered(false);
    assertEquals(1f, hover.advance(true, LONG), "left while frozen: stays lit");
  }

  @Test
  void keepsFadingWhileFrozen() {
    MissionIconHover hover = new MissionIconHover(HoverConfig.defaults());
    hover.setHovered(true);
    float partway = hover.advance(false, 0.05f);

    float later = hover.advance(true, 0.05f);

    assertTrue(later > partway, "the fade runs on toward the frozen target");
  }
}
