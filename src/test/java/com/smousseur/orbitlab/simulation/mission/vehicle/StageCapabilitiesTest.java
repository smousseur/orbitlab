package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import org.junit.jupiter.api.Test;

class StageCapabilitiesTest {

  private static StageCapabilities liquidUpper(double maxCoastDuration) {
    return new StageCapabilities(
        IgnitionMode.AIRSTART,
        1,
        ShutdownMode.COMMANDED,
        PropellantType.STORABLE,
        maxCoastDuration,
        StageRole.UPPER);
  }

  @Test
  void solidStage_valid() {
    StageCapabilities solid =
        new StageCapabilities(
            IgnitionMode.GROUND,
            0,
            ShutdownMode.BURN_TO_DEPLETION,
            PropellantType.SOLID,
            0.0,
            StageRole.BOOSTER);
    assertFalse(solid.variableLoad());
  }

  @Test
  void solidStage_commandedShutdown_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.COMMANDED,
                PropellantType.SOLID,
                0.0,
                StageRole.BOOSTER));
  }

  @Test
  void solidStage_restartable_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageCapabilities(
                IgnitionMode.GROUND,
                1,
                ShutdownMode.BURN_TO_DEPLETION,
                PropellantType.SOLID,
                0.0,
                StageRole.BOOSTER));
  }

  @Test
  void cryogenicStage_infiniteCoast_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageCapabilities(
                IgnitionMode.AIRSTART,
                1,
                ShutdownMode.COMMANDED,
                PropellantType.CRYOGENIC,
                Double.POSITIVE_INFINITY,
                StageRole.UPPER));
  }

  @Test
  void negativeRestartCount_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StageCapabilities(
                IgnitionMode.AIRSTART,
                -1,
                ShutdownMode.COMMANDED,
                PropellantType.STORABLE,
                0.0,
                StageRole.UPPER));
  }

  @Test
  void negativeOrNanMaxCoast_rejected() {
    assertThrows(IllegalArgumentException.class, () -> liquidUpper(-1.0));
    assertThrows(IllegalArgumentException.class, () -> liquidUpper(Double.NaN));
  }

  @Test
  void variableLoad_trueForLiquids_falseForSolids() {
    assertTrue(liquidUpper(3_600).variableLoad());
    assertTrue(
        new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.COMMANDED,
                PropellantType.CRYOGENIC,
                0.0,
                StageRole.CORE)
            .variableLoad());
    assertFalse(
        new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.BURN_TO_DEPLETION,
                PropellantType.SOLID,
                0.0,
                StageRole.BOOSTER)
            .variableLoad());
  }

  @Test
  void canCoastFor_boundsInclusive() {
    StageCapabilities capabilities = liquidUpper(3_600);
    assertTrue(capabilities.canCoastFor(0.0));
    assertTrue(capabilities.canCoastFor(3_600));
    assertFalse(capabilities.canCoastFor(3_600.1));
  }

  @Test
  void canCoastFor_unlimitedForInfiniteCoast() {
    assertTrue(liquidUpper(Double.POSITIVE_INFINITY).canCoastFor(1e9));
  }
}
