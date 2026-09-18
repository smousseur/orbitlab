package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import org.junit.jupiter.api.Test;

/**
 * {@link FollowedObject#debrisLabel} — the piece label shared by the renderer and the telemetry.
 */
class FollowedObjectTest {

  @Test
  void debrisLabelNamesEachPiece() {
    assertEquals("Booster 1", FollowedObject.debrisLabel(StageRole.BOOSTER, 1));
    assertEquals("Booster 2", FollowedObject.debrisLabel(StageRole.BOOSTER, 2));
    assertEquals("Core", FollowedObject.debrisLabel(StageRole.CORE, 1));
    assertEquals("Upper stage", FollowedObject.debrisLabel(StageRole.UPPER, 1));
    assertEquals("Kick stage", FollowedObject.debrisLabel(StageRole.KICK, 1));
  }
}
