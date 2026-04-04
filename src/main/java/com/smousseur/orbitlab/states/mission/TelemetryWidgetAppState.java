package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.ui.telemetry.TelemetryWidget;
import java.util.Optional;

/**
 * Application state that manages the mission telemetry HUD widget.
 *
 * <p>Each frame, looks for a currently ongoing mission in {@link
 * com.smousseur.orbitlab.simulation.mission.MissionContext} and updates the {@link TelemetryWidget}
 * accordingly. The widget is hidden when no mission is active.
 */
public final class TelemetryWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private TelemetryWidget widget;

  /**
   * Creates a new telemetry widget state.
   *
   * @param context the application context providing mission context and GUI graph
   */
  public TelemetryWidgetAppState(ApplicationContext context) {
    this.context = context;
  }

  @Override
  protected void initialize(Application app) {
    widget = new TelemetryWidget(context);
    widget.setVisible(false);
    widget.layoutTopRight(app.getCamera().getWidth(), app.getCamera().getHeight());
  }

  @Override
  public void update(float tpf) {
    Optional<Mission> ongoing =
        context.missionContext().getMissions().stream()
            .map(entry -> entry.mission())
            .filter(m -> m.getStatus() == MissionStatus.RUNNING)
            .findFirst();

    if (ongoing.isPresent()) {
      widget.setVisible(true);
      widget.update(ongoing.get());
    } else {
      widget.setVisible(false);
    }
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
