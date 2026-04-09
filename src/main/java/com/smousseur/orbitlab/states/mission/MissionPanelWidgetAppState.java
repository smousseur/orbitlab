package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.LEOMission;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.ui.mission.MissionPanelWidget;

/**
 * Application state that manages the mission panel GUI widget.
 *
 * <p>Creates and lays out a {@link MissionPanelWidget} during initialization, updates it each frame
 * to keep the mission list current, and properly closes the widget on cleanup.
 */
public final class MissionPanelWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private MissionPanelWidget widget;

  /**
   * Creates a new mission panel widget state.
   *
   * @param context the application context providing mission context and GUI graph
   */
  public MissionPanelWidgetAppState(ApplicationContext context) {
    this.context = context;
    MissionContext missionContext = context.missionContext();
    missionContext.addMission(new LEOMission("LEO", 400_000));
  }

  @Override
  protected void initialize(Application app) {
    widget = new MissionPanelWidget(context);
    widget.layoutTopLeft(app.getCamera().getWidth(), app.getCamera().getHeight());
  }

  @Override
  public void update(float tpf) {
    widget.update(tpf);
  }

  @Override
  protected void cleanup(Application app) {
    if (widget != null) {
      widget.close();
      widget = null;
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
