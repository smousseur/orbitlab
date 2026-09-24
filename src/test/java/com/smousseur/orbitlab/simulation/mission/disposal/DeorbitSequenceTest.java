package com.smousseur.orbitlab.simulation.mission.disposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which ways a deorbit sequence can end are followed by a fall to the ground. */
class DeorbitSequenceTest {

  @Test
  void onlyTheTargetAndAFallBeforeTheNextBurnLeadToAReentry() {
    Set<DeorbitSequence.End> reentering = EnumSet.noneOf(DeorbitSequence.End.class);
    for (DeorbitSequence.End end : DeorbitSequence.End.values()) {
      if (end.leadsToReentry()) {
        reentering.add(end);
      }
    }

    assertEquals(
        EnumSet.of(DeorbitSequence.End.TARGET_REACHED, DeorbitSequence.End.FELL_BEFORE_NEXT_BURN),
        reentering);
  }
}
