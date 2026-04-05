package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.ui.telemetry.TelemetryWidget;
import java.util.Optional;

/**
 * Application state that manages the mission telemetry HUD widget.
 *
 * <p>Telemetry is shown when a mission is selected, READY, visible, and has an ephemeris. All
 * values come from the pre-computed ephemeris — no Orekit calls at runtime.
 */
public final class TelemetryWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private TelemetryWidget widget;

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
    MissionContext mc = context.missionContext();
    Optional<MissionEntry> selected = mc.getSelectedMission();

    // Telemetry requires: selected + READY + visible
    if (selected.isEmpty()
        || selected.get().mission().getStatus() != MissionStatus.READY
        || !selected.get().isVisible()) {
      widget.setVisible(false);
      return;
    }

    MissionEntry entry = selected.get();
    MissionEphemeris eph = entry.getEphemeris().orElse(null);
    if (eph == null) {
      widget.setVisible(false);
      return;
    }

    widget.setVisible(true);
    widget.updateFromEphemeris(eph, context.clock().now(), entry.mission());
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
