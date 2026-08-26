package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import org.junit.jupiter.api.Test;

class TransfertTwoManeuverStageTest {

  @Test
  void configure_withoutOptimization_rejected() {
    TransfertTwoManeuverStage stage = new TransfertTwoManeuverStage("Transfert", 400_000, 0.8);
    assertThrows(OrbitlabException.class, () -> stage.configure(null, null));
  }

  @Test
  void optimizationKey_isStageName() {
    TransfertTwoManeuverStage stage = new TransfertTwoManeuverStage("Transfert", 400_000, 0.8);
    assertEquals("Transfert", stage.optimizationKey());
  }
}
