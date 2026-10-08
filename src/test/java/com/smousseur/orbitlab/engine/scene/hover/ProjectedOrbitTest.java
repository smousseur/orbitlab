package com.smousseur.orbitlab.engine.scene.hover;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.junit.jupiter.api.Test;

/** The reused polyline must not hand out what an earlier, longer projection left in its array. */
class ProjectedOrbitTest {

  @Test
  void refusesAPointBeyondItsCount() {
    ProjectedOrbit orbit = new ProjectedOrbit(SolarSystemBody.MARS);
    orbit.reset(4);
    orbit.add(1f, 1f);
    orbit.add(2f, 2f);
    orbit.add(3f, 3f);
    orbit.reset(4);
    orbit.add(4f, 4f);

    assertThrows(IndexOutOfBoundsException.class, () -> orbit.x(1));
    assertThrows(IndexOutOfBoundsException.class, () -> orbit.y(2));
  }
}
