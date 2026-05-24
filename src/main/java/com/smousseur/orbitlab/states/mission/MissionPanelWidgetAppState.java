package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.ui.mission.panel.MissionPanelWidget;

/**
 * On-demand opener of the mission management modal. Listens for {@link
 * EventBus.UiNavigationEvent.OpenMissionManagement} requests (published by the Display Panel) and
 * builds the modal panel when needed. The trigger button is now owned by {@link
 * MissionDisplayPanelAppState}.
 */
public final class MissionPanelWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private MissionPanelWidget panel;

  public MissionPanelWidgetAppState(ApplicationContext context) {
    this.context = context;
    for (int i = 0; i < 19; i++) {
      context.missionContext().addMission(new LEOMission("LEO-" + i, 400_000 + i * 10_000));
    }
  }

  @Override
  protected void initialize(Application app) {}

  @Override
  public void update(float tpf) {
    if (context.eventBus().pollOpenManagement() != null) {
      openPanel();
    }
    if (panel != null) {
      panel.update(tpf, getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    closePanel();
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  private void openPanel() {
    if (panel != null) return;
    panel = new MissionPanelWidget(context);
    panel.setOnClose(this::closePanel);
    panel.setOnNewMission(
        () -> {
          closePanel();
          context
              .eventBus()
              .publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());
        });
    panel.attachTo(context.guiGraph().getModalNode());
  }

  private void closePanel() {
    if (panel != null) {
      panel.close();
      panel = null;
    }
  }
}
