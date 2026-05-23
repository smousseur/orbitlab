package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.mission.display.MissionDisplayPanelWidget;
import com.smousseur.orbitlab.ui.mission.panel.MissionPanelTrigger;

import java.util.Objects;

/**
 * Owns the top-left {@link MissionPanelTrigger} and the non-modal {@link
 * MissionDisplayPanelWidget}. Drains telemetry focus events, applies the panel's auto-coherence
 * rules ({@link MissionDisplayPanelRules}), and forwards UI actions through the {@link EventBus}.
 */
public final class MissionDisplayPanelAppState extends BaseAppState {

  private final ApplicationContext context;
  private final MissionDisplayPanelRules rules = new MissionDisplayPanelRules();

  private MissionPanelTrigger trigger;
  private MissionDisplayPanelWidget widget;
  private Node parentNode;

  public MissionDisplayPanelAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    int sw = app.getCamera().getWidth();
    int sh = app.getCamera().getHeight();

    trigger = new MissionPanelTrigger(context);
    trigger.layoutTopLeft(sw, sh);
    trigger.setOnClick(this::togglePanel);

    parentNode = context.guiGraph().getMissionPanelNode();
    widget = new MissionDisplayPanelWidget(context);
    widget.layoutTopLeft(sw, sh);
    widget.setOnManageClicked(this::publishOpenManagement);
    widget.setOnCreateClicked(this::publishOpenWizard);
    widget.setOnHideAll(this::handleHideAll);
    widget.setRowListener(buildRowListener());
    widget.attachTo(parentNode);
    widget.setVisible(false);
    trigger.setEnabled(true);
  }

  @Override
  public void update(float tpf) {
    drainTelemetryFocusEvents();
    rules.applyStatusTransitionRules(context.missionContext());
    if (widget != null) {
      widget.update(tpf, getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    if (widget != null) {
      widget.close();
      widget = null;
    }
    if (trigger != null) {
      trigger.close();
      trigger = null;
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  private void togglePanel() {
    if (widget == null) return;
    if (widget.isVisible()) {
      widget.setVisible(false);
      trigger.setEnabled(false);
    } else {
      widget.show(parentNode);
      trigger.setEnabled(true);
    }
  }

  private void publishOpenManagement() {
    context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
  }

  private void publishOpenWizard() {
    context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());
  }

  private MissionDisplayPanelWidget.RowListener buildRowListener() {
    EventBus bus = context.eventBus();
    return new MissionDisplayPanelWidget.RowListener() {
      @Override
      public void onToggleTelemetry(String name, boolean currentlyOn) {
        bus.publishTelemetryFocus(currentlyOn ? null : name);
      }

      @Override
      public void onToggleVisibility(String name) {
        bus.publishMissionAction(name, EventBus.MissionAction.TOGGLE_VISIBLE);
      }
    };
  }

  private void handleHideAll() {
    MissionContext mc = context.missionContext();
    EventBus bus = context.eventBus();
    for (MissionEntry entry : mc.getMissions()) {
      if (entry.mission().getStatus() == MissionStatus.READY && entry.isVisible()) {
        bus.publishMissionAction(entry.mission().getName(), EventBus.MissionAction.TOGGLE_VISIBLE);
      }
    }
    // R8: clear telemetry on hide-all.
    bus.publishTelemetryFocus(null);
  }

  private void drainTelemetryFocusEvents() {
    EventBus.MissionTelemetryFocusRequest req;
    MissionContext mc = context.missionContext();
    while ((req = context.eventBus().pollTelemetryFocus()) != null) {
      rules.applyTelemetryFocus(mc, req.missionName());
    }
  }
}
