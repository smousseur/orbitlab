package com.smousseur.orbitlab.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import org.junit.jupiter.api.Test;

/**
 * {@link FocusController#handleLost} — the pure decision behind the L3 automatic return: a followed
 * debris whose icon (its handle) is no longer shown hands the focus back to the primary. The
 * predicate reads only whether the icon is up now, so an impact — where the icon stays — is not a
 * special case: it simply never satisfies the predicate.
 */
class FocusControllerTest {

  private static final MissionId MISSION = MissionId.newId();
  private static final FollowedObject PRIMARY = new FollowedObject.Primary(MISSION);
  private static final FollowedObject DEBRIS =
      new FollowedObject.Debris(MISSION, StageRole.BOOSTER, 1);

  @Test
  void primaryNeverDetaches() {
    assertFalse(FocusController.handleLost(PRIMARY, false, true));
  }

  @Test
  void debrisWithVisibleIconWithinItsLifeKeepsFocus() {
    assertFalse(FocusController.handleLost(DEBRIS, true, false));
  }

  @Test
  void debrisReturnsToPrimaryWhenToggledOff() {
    assertTrue(FocusController.handleLost(DEBRIS, false, false));
  }

  @Test
  void debrisReturnsToPrimaryWhenRewoundBeforeJettison() {
    assertTrue(FocusController.handleLost(DEBRIS, true, true));
  }

  @Test
  void impactDoesNotDetach() {
    // After impact the piece is still drawn with its icon (debris visible, past jettison), so it
    // reads exactly as a live, handled debris. The predicate never inspects the impact instant.
    assertFalse(FocusController.handleLost(DEBRIS, true, false));
  }
}
