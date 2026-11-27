package com.smousseur.orbitlab.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** How a plume's shape follows the air it fires into, and the tuning's own guards. */
class NozzlePlumeConfigTest {

  private final NozzlePlumeConfig config = NozzlePlumeConfig.defaults();

  @Test
  void aPlumeFiredAtSeaLevelKeepsItsSeaLevelShape() {
    assertEquals(0f, config.pressureFactor(0.0), 0f);
  }

  @Test
  void aPlumeFiredAboveTheAirTakesItsVacuumShape() {
    double vacuumAltitude = config.scaleHeightM() * Math.log(1.0 / config.vacuumRatio());

    assertEquals(1f, config.pressureFactor(vacuumAltitude), 1e-6f);
    assertEquals(1f, config.pressureFactor(200_000.0), 0f);
  }

  @Test
  void theShapeOpensSteadilyAsTheAirThins() {
    float previous = -1f;
    for (double h = 0.0; h <= 80_000.0; h += 500.0) {
      float t = config.pressureFactor(h);
      assertTrue(t >= previous, "pressure factor falls at " + h + " m");
      assertTrue(t >= 0f && t <= 1f, "pressure factor out of [0, 1] at " + h + " m");
      previous = t;
    }
  }

  @Test
  void theShapeStaysAtSeaLevelUntilTheAirHasThinnedEnough() {
    double startAltitude = config.scaleHeightM() * Math.log(1.0 / config.expansionStartRatio());

    assertEquals(0f, config.pressureFactor(startAltitude * 0.99), 0f);
    assertTrue(config.pressureFactor(startAltitude * 1.5) > 0f);
  }

  @Test
  void aShapeBetweenTheTwoIsTheirBlend() {
    PlumeLookConfig look = config.look(PlumeLook.MERLIN);

    assertEquals(look.seaLevel(), look.shapeAt(0f));
    assertEquals(look.vacuum(), look.shapeAt(1f));
    PlumeShape half = look.shapeAt(0.5f);
    assertEquals(
        0.5f * (look.seaLevel().lengthRadii() + look.vacuum().lengthRadii()),
        half.lengthRadii(),
        1e-5f);
    assertEquals(
        0.5f * (look.seaLevel().halfAngleDeg() + look.vacuum().halfAngleDeg()),
        half.halfAngleDeg(),
        1e-5f);
  }

  @Test
  void everyLookIsTuned() {
    for (PlumeLook look : PlumeLook.values()) {
      assertTrue(config.looks().containsKey(look), look + " has no tuning");
    }
  }

  @Test
  void aTuningMissingALookIsRefused() {
    Map<PlumeLook, PlumeLookConfig> looks = new EnumMap<>(config.looks());
    looks.remove(PlumeLook.VINCI);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NozzlePlumeConfig(
                looks,
                config.stepRadii(),
                config.maxSteps(),
                config.scaleHeightM(),
                config.expansionStartRatio(),
                config.vacuumRatio(),
                config.rampSeconds(),
                config.exposure()));
  }

  @Test
  void aVacuumThatIsNotThinnerThanTheStartIsRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NozzlePlumeConfig(
                config.looks(),
                config.stepRadii(),
                config.maxSteps(),
                config.scaleHeightM(),
                0.1,
                0.2,
                config.rampSeconds(),
                config.exposure()));
  }

  @Test
  void aShapeWithANegativeLengthIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> new PlumeShape(-1f, 5f, 1f, 0.3f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new PlumeShape(10f, 90f, 1f, 0.3f, 0f));
  }
}
