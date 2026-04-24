package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.LEOMission;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.ui.mission.MissionPanelTrigger;
import com.smousseur.orbitlab.ui.mission.MissionPanelWidget;

/**
 * Orchestrates the mission panel: a persistent top-left trigger button and an on-demand modal
 * panel attached to the shared modal layer. The panel itself is created only when the trigger is
 * pressed and torn down when it closes.
 */
public final class MissionPanelWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private MissionPanelTrigger trigger;
  private MissionPanelWidget panel;

  public MissionPanelWidgetAppState(ApplicationContext context) {
    this.context = context;
    MissionContext missionContext = context.missionContext();
    missionContext.addMission(new LEOMission("LEO", 400_000));
  }

  @Override
  protected void initialize(Application app) {
    trigger = new MissionPanelTrigger(context);
    trigger.layoutTopLeft(app.getCamera().getWidth(), app.getCamera().getHeight());
    trigger.setOnClick(this::togglePanel);
  }

  @Override
  public void update(float tpf) {
    if (panel != null) {
      panel.update(tpf, getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    closePanel();
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
    if (panel == null) {
      openPanel();
    } else {
      closePanel();
    }
  }

  private void openPanel() {
    panel = new MissionPanelWidget(context);
    panel.setOnClose(this::closePanel);
    panel.setOnNewMission(
        () -> {
          closePanel();
          context.eventBus().publishUiNavigation(EventBus.UiNavigation.OPEN_MISSION_WIZARD);
        });
    panel.attachTo(context.guiGraph().getModalNode());
    trigger.setEnabled(false);
  }

  private void closePanel() {
    if (panel != null) {
      panel.close();
      panel = null;
    }
    if (trigger != null) {
      trigger.setEnabled(true);
    }
  }
}
