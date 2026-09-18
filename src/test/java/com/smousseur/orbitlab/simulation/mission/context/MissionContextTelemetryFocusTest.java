package com.smousseur.orbitlab.simulation.mission.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import org.junit.jupiter.api.Test;

/**
 * The SEL-1 / L2 telemetry-focus shim: the pointer becomes object-capable, but the mission-level
 * getters keep answering the mission so their consumers are untouched.
 */
class MissionContextTelemetryFocusTest {

  @Test
  void objectFocusExposesItsMissionToMissionLevelConsumers() {
    MissionContext mc = new MissionContext();
    MissionId id = MissionId.newId();
    FollowedObject.Debris debris = new FollowedObject.Debris(id, StageRole.BOOSTER, 1);

    mc.setTelemetryFocus(debris);

    assertEquals(debris, mc.getTelemetryFocus());
    assertEquals(id, mc.getTelemetryFocusMissionId());
  }

  @Test
  void missionLevelSetterWrapsInPrimary() {
    MissionContext mc = new MissionContext();
    MissionId id = MissionId.newId();

    mc.setTelemetryFocusMissionId(id);

    assertEquals(new FollowedObject.Primary(id), mc.getTelemetryFocus());
    assertEquals(id, mc.getTelemetryFocusMissionId());
  }

  @Test
  void clearingFromEitherSideIsNull() {
    MissionContext mc = new MissionContext();

    mc.setTelemetryFocus(new FollowedObject.Primary(MissionId.newId()));
    mc.setTelemetryFocusMissionId(null);
    assertNull(mc.getTelemetryFocus());
    assertNull(mc.getTelemetryFocusMissionId());

    mc.setTelemetryFocus(new FollowedObject.Primary(MissionId.newId()));
    mc.setTelemetryFocus(null);
    assertNull(mc.getTelemetryFocus());
    assertNull(mc.getTelemetryFocusMissionId());
  }
}
