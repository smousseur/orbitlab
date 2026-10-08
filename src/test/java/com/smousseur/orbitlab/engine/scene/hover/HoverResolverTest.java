package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.hover.HoverTarget.Handle;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Which planet the hover designates, from what is under the cursor — and when the orbit detection
 * is allowed to run at all.
 */
class HoverResolverTest {

  private static final HoverTarget EARTH_ICON = new HoverTarget(SolarSystemBody.EARTH, Handle.ICON);
  private static final HoverTarget MARS_ORBIT = new HoverTarget(SolarSystemBody.MARS, Handle.ORBIT);

  @Test
  void aPlanetIconWinsOverTheScene() {
    CountingPicker picker = new CountingPicker(SolarSystemBody.VENUS);

    Optional<HoverTarget> target =
        HoverResolver.resolve(false, null, SolarSystemBody.MARS, true, picker);

    assertEquals(Optional.of(new HoverTarget(SolarSystemBody.MARS, Handle.ICON)), target);
    assertEquals(0, picker.calls, "the detection is not run under an icon");
  }

  @Test
  void theSceneAloneRunsTheDetection() {
    CountingPicker picker = new CountingPicker(SolarSystemBody.VENUS);

    Optional<HoverTarget> target = HoverResolver.resolve(false, null, null, true, picker);

    assertEquals(Optional.of(new HoverTarget(SolarSystemBody.VENUS, Handle.ORBIT)), target);
    assertEquals(1, picker.calls);
  }

  @Test
  void anOrbitFarFromTheCursorDesignatesNothing() {
    CountingPicker picker = new CountingPicker(null);

    assertTrue(HoverResolver.resolve(false, MARS_ORBIT, null, true, picker).isEmpty());
  }

  @Test
  void theDetectionKnowsWhichPlanetWasLit() {
    CountingPicker picker = new CountingPicker(SolarSystemBody.EARTH);

    HoverResolver.resolve(false, EARTH_ICON, null, true, picker);
    assertEquals(SolarSystemBody.EARTH, picker.lastCurrent, "a planet lit by its icon");

    HoverResolver.resolve(false, null, null, true, picker);
    assertNull(picker.lastCurrent, "nothing lit");
  }

  @Test
  void neitherAnIconNorTheSceneDesignatesNothing() {
    CountingPicker picker = new CountingPicker(SolarSystemBody.VENUS);

    assertTrue(HoverResolver.resolve(false, MARS_ORBIT, null, false, picker).isEmpty());
    assertEquals(0, picker.calls, "the detection is not run over a panel");
  }

  @Test
  void frozenKeepsThePreviousTargetWhateverIsUnderTheCursor() {
    CountingPicker picker = new CountingPicker(SolarSystemBody.VENUS);

    assertEquals(
        Optional.of(MARS_ORBIT),
        HoverResolver.resolve(true, MARS_ORBIT, SolarSystemBody.EARTH, true, picker));
    assertEquals(
        Optional.of(EARTH_ICON), HoverResolver.resolve(true, EARTH_ICON, null, true, picker));
    assertTrue(HoverResolver.resolve(true, null, SolarSystemBody.EARTH, true, picker).isEmpty());
    assertEquals(0, picker.calls, "the detection is not run while frozen");
  }

  private static final class CountingPicker implements HoverResolver.OrbitPicker {
    private final SolarSystemBody answer;
    private int calls;
    private SolarSystemBody lastCurrent;

    CountingPicker(SolarSystemBody answer) {
      this.answer = answer;
    }

    @Override
    public Optional<SolarSystemBody> pick(SolarSystemBody current) {
      calls++;
      lastCurrent = current;
      return Optional.ofNullable(answer);
    }
  }
}
