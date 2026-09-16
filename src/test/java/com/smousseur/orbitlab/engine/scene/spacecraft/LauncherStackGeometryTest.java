package com.smousseur.orbitlab.engine.scene.spacecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import org.junit.jupiter.api.Test;

class LauncherStackGeometryTest {

  @Test
  void falconHeavyRemnantFractionIsMeasured() {
    assertEquals(0.383, LauncherStackGeometry.afterS1Fraction(Launchers.FALCON_HEAVY.id()), 1e-9);
  }

  @Test
  void arianeRemnantFractionIsMeasured() {
    assertEquals(0.514, LauncherStackGeometry.afterS1Fraction(Launchers.ARIANE_64.id()), 1e-9);
  }

  @Test
  void anUnknownLauncherGetsNoLift() {
    assertEquals(1.0, LauncherStackGeometry.afterS1Fraction("NO_SUCH_LAUNCHER"), 1e-9);
    assertEquals(1.0, LauncherStackGeometry.afterS1Fraction(null), 1e-9);
  }
}
