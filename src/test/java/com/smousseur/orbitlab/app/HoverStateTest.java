package com.smousseur.orbitlab.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.hover.HoverTarget;
import com.smousseur.orbitlab.engine.scene.hover.HoverTarget.Handle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The hover facts the listeners write, and the click on a lit orbit. */
class HoverStateTest {

  private final HoverState state = new HoverState();
  private final List<SolarSystemBody> flights = new ArrayList<>();

  @Test
  void aPressOnALitOrbitFliesToItsPlanetOnce() {
    state.update(new HoverTarget(SolarSystemBody.MARS, Handle.ORBIT), false);

    state.routeScenePress(flights::add);

    assertEquals(List.of(SolarSystemBody.MARS), flights);
  }

  @Test
  void aPressUnderALitIconIsLeftToTheIcon() {
    state.update(new HoverTarget(SolarSystemBody.MARS, Handle.ICON), false);

    state.routeScenePress(flights::add);

    assertTrue(flights.isEmpty());
  }

  @Test
  void aPressWhileFrozenOrWithNothingLitDoesNothing() {
    state.update(new HoverTarget(SolarSystemBody.MARS, Handle.ORBIT), true);
    state.routeScenePress(flights::add);

    state.update(null, false);
    state.routeScenePress(flights::add);

    assertTrue(flights.isEmpty());
  }

  @Test
  void anIconExitOnlyClearsItsOwnPlanet() {
    state.iconEntered(SolarSystemBody.VENUS);
    state.iconExited(SolarSystemBody.MARS);
    assertEquals(Optional.of(SolarSystemBody.VENUS), state.iconUnderCursor());

    state.iconExited(SolarSystemBody.VENUS);
    assertTrue(state.iconUnderCursor().isEmpty());
  }

  @Test
  void exposesWhatTheStateWrote() {
    HoverTarget target = new HoverTarget(SolarSystemBody.EARTH, Handle.ICON);

    state.update(target, true);

    assertEquals(Optional.of(target), state.target());
    assertTrue(state.isFrozen());
  }
}
