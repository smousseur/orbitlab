package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.HudSurface;
import com.smousseur.orbitlab.app.HudSurfaces;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.mission.panel.MissionPanelWidget;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owner of the non-modal mission management window. {@link
 * EventBus.UiNavigationEvent.OpenMissionManagement} <b>toggles</b> it — that is what the menu's
 * checked entry means, and the display panel's <i>Manage</i> button publishes the same event.
 *
 * <p>The window is built and destroyed on each opening, so its position is remembered here rather
 * than by the widget: centred the first time, then reopened where the user left it, and back to the
 * centre when the application restarts.
 */
public final class MissionPanelWidgetAppState extends BaseAppState {

  private static final Logger logger = LogManager.getLogger(MissionPanelWidgetAppState.class);

  private final ApplicationContext context;
  private MissionPanelWidget panel;
  private AutoCloseable surfaceHandle;

  /** Where the window was when it was last closed; null until it has been opened once. */
  private Vector3f lastPosition;

  public MissionPanelWidgetAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    surfaceHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.MISSION_MANAGEMENT,
                    UiLayers.WINDOW,
                    () -> panel != null,
                    this::closePanel));
  }

  @Override
  public void update(float tpf) {
    if (context.eventBus().pollOpenManagement() != null) {
      togglePanel();
    }
    if (panel != null) {
      panel.update(tpf, getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    closePanel();
    HudSurfaces.closeQuietly(surfaceHandle, logger);
    surfaceHandle = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  private void togglePanel() {
    if (panel != null) {
      closePanel();
    } else {
      openPanel();
    }
  }

  private void openPanel() {
    if (panel != null) return;
    panel = new MissionPanelWidget(context);
    panel.setInitialPosition(lastPosition);
    panel.setOnClose(this::closePanel);
    panel.setOnNewMission(
        () ->
            context
                .eventBus()
                .publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard()));
    panel.attachTo(context.guiGraph().getMissionPanelNode());
  }

  private void closePanel() {
    if (panel != null) {
      lastPosition = panel.getPosition();
      panel.close();
      panel = null;
    }
  }
}
