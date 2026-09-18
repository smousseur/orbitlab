package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.simulation.mission.FollowedObject;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.ui.telemetry.TelemetryWidget;

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
    widget =
        new TelemetryWidget(
            context,
            missionId -> context.focusController().select(new FollowedObject.Primary(missionId)));
    widget.setVisible(false);
    widget.layoutTopRight(app.getCamera().getWidth(), app.getCamera().getHeight());
  }

  @Override
  public void update(float tpf) {
    MissionContext mc = context.missionContext();
    FollowedObject object = mc.getTelemetryFocus();
    if (object == null) {
      widget.setVisible(false);
      return;
    }

    // Gated on the parent mission — READY and visible — whichever of its objects is followed; the
    // panels stay mission-level too (SEL-1 / L2). The object only changes which ephemeris is read.
    MissionEntry entry = mc.findMission(object.mission()).orElse(null);
    if (entry == null || entry.mission().getStatus() != MissionStatus.READY || !entry.isVisible()) {
      widget.setVisible(false);
      return;
    }

    MissionEphemeris eph = mc.ephemerisOf(object).orElse(null);
    if (eph == null) {
      widget.setVisible(false);
      return;
    }

    widget.setVisible(true);
    widget.updateFromEphemeris(eph, context.clock().now(), entry.mission(), object);
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
