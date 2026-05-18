package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Unit tests for the auto-coherence rules of the Mission Display Panel. The rules are exercised
 * directly against an in-memory {@link MissionContext}; the JME lifecycle is not involved.
 */
class MissionDisplayPanelRulesTest {

  private static MissionEntry addMission(MissionContext mc, String name, MissionStatus status) {
    Mission m = new StubMission(name);
    m.setStatus(status);
    mc.addMission(m);
    return mc.findMission(name).orElseThrow();
  }

  @Test
  void r1_firstMissionReachingReadyAutoArmsTelemetryAndForcesVisibility() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    MissionEntry entry = addMission(mc, "Apollo", MissionStatus.COMPUTING);
    rules.applyStatusTransitionRules(mc); // record COMPUTING

    entry.mission().setStatus(MissionStatus.READY);
    rules.applyStatusTransitionRules(mc);

    assertEquals("Apollo", mc.getTelemetryFocusMissionName());
    assertTrue(entry.isVisible(), "R1 must force visible=true");
  }

  @Test
  void r2_secondReadyMissionDoesNotStealTelemetry() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    MissionEntry first = addMission(mc, "Apollo", MissionStatus.READY);
    rules.applyStatusTransitionRules(mc); // R1 fires: Apollo telemetered, visible

    MissionEntry second = addMission(mc, "GTO", MissionStatus.COMPUTING);
    rules.applyStatusTransitionRules(mc);
    second.mission().setStatus(MissionStatus.READY);
    rules.applyStatusTransitionRules(mc);

    assertEquals("Apollo", mc.getTelemetryFocusMissionName());
    assertTrue(first.isVisible());
    assertFalse(second.isVisible(), "second mission default visibility stays false (R2)");
  }

  @Test
  void r9_telemeteredMissionLeavingReadyClearsFocus() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    MissionEntry entry = addMission(mc, "Apollo", MissionStatus.READY);
    rules.applyStatusTransitionRules(mc); // R1 fires
    assertEquals("Apollo", mc.getTelemetryFocusMissionName());

    entry.mission().setStatus(MissionStatus.COMPUTING);
    rules.applyStatusTransitionRules(mc);

    assertNull(mc.getTelemetryFocusMissionName(), "R9 must clear the telemetry focus");
  }

  @Test
  void r10_deletingTelemeteredMissionClearsFocus() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    addMission(mc, "Apollo", MissionStatus.READY);
    rules.applyStatusTransitionRules(mc);
    assertEquals("Apollo", mc.getTelemetryFocusMissionName());

    mc.removeMission("Apollo");
    rules.applyStatusTransitionRules(mc);

    assertNull(mc.getTelemetryFocusMissionName(), "R10 must clear focus when deleted");
  }

  @Test
  void telemetryFocus_appliedOnReadyMissionForcesVisibility() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    MissionEntry hidden = addMission(mc, "Apollo", MissionStatus.READY);
    hidden.setVisible(false);

    rules.applyTelemetryFocus(mc, "Apollo");

    assertEquals("Apollo", mc.getTelemetryFocusMissionName());
    assertTrue(hidden.isVisible(), "R3 must force the targeted mission visible");
  }

  @Test
  void telemetryFocus_ignoresUnknownMission() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    rules.applyTelemetryFocus(mc, "DoesNotExist");

    assertNull(mc.getTelemetryFocusMissionName());
  }

  @Test
  void telemetryFocus_ignoresNonReadyMission() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    addMission(mc, "Draft", MissionStatus.DRAFT);
    rules.applyTelemetryFocus(mc, "Draft");

    assertNull(mc.getTelemetryFocusMissionName());
  }

  @Test
  void telemetryFocus_nullClearsFocus() {
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    addMission(mc, "Apollo", MissionStatus.READY);
    rules.applyTelemetryFocus(mc, "Apollo");
    assertEquals("Apollo", mc.getTelemetryFocusMissionName());

    rules.applyTelemetryFocus(mc, null);
    assertNull(mc.getTelemetryFocusMissionName());
  }

  @Test
  void r5_hidingTelemeteredMissionClearsFocus_isHandledByOrchestratorBranchNotByRules() {
    // R5 is implemented in MissionOrchestratorAppState.pollMissionActions (TOGGLE_VISIBLE branch).
    // The Display Panel rules engine does not own R5; we cover the relevant side effect here by
    // simulating what the orchestrator now does: flip visible=false on the telemetered entry, then
    // clear the focus name. The rules engine must then NOT re-arm telemetry on the next tick
    // (R1 only fires on a status transition into READY, which did not happen).
    MissionContext mc = new MissionContext();
    MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

    MissionEntry entry = addMission(mc, "Apollo", MissionStatus.READY);
    rules.applyStatusTransitionRules(mc); // R1 arms telemetry

    // Orchestrator simulates the TOGGLE_VISIBLE off branch:
    entry.setVisible(false);
    mc.setTelemetryFocusMissionName(null);

    // Subsequent rule tick must NOT re-arm telemetry: Apollo is still READY but the status did not
    // transition this frame, so R1 does not fire.
    rules.applyStatusTransitionRules(mc);
    assertNull(mc.getTelemetryFocusMissionName());
    assertFalse(entry.isVisible());
  }

  @Test
  void eventBus_routesPublishUiNavigationByConcreteType() {
    EventBus bus = new EventBus();

    bus.publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());
    bus.publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
    bus.publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());

    // Three distinct queues drain independently.
    assertEquals(EventBus.UiNavigationEvent.OpenMissionWizard.class, bus.pollOpenWizard().getClass());
    assertEquals(
        EventBus.UiNavigationEvent.OpenMissionManagement.class,
        bus.pollOpenManagement().getClass());
    assertEquals(EventBus.UiNavigationEvent.OpenMissionWizard.class, bus.pollOpenWizard().getClass());
    assertNull(bus.pollOpenWizard());
    assertNull(bus.pollOpenManagement());
    assertNull(bus.pollCreateMission());
  }

  // ---------------------------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------------------------

  /**
   * Minimal {@link Mission} stub. Only name + status are exercised by the Display Panel rules
   * engine, so vehicle/stages/objective are intentionally {@code null}.
   */
  private static final class StubMission extends Mission {
    StubMission(String name) {
      super(name, null, null, null);
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      throw new UnsupportedOperationException("Stub mission has no propagation");
    }
  }
}
