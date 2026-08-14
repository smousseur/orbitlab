package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.scene.Node;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.mission.display.MissionDisplayPanelWidget;
import com.smousseur.orbitlab.ui.menu.AppMenu;
import com.smousseur.orbitlab.ui.menu.AppMenuItem;
import java.util.List;
import java.util.Objects;

/**
 * Owns the top-left {@link AppMenu} and the non-modal {@link MissionDisplayPanelWidget}. Drains
 * telemetry focus events, applies the panel's auto-coherence rules ({@link
 * MissionDisplayPanelRules}), and forwards UI actions through the {@link EventBus}.
 */
public final class MissionDisplayPanelAppState extends BaseAppState implements ActionListener {

  /** Toggles the mission display panel; carries the menu's only check mark. */
  private static final String ITEM_MISSION_PANEL = "missionPanel";

  /** Opens the mission management modal. */
  private static final String ITEM_MANAGE_MISSIONS = "manageMissions";

  /** Opens the mission creation wizard. */
  private static final String ITEM_NEW_MISSION = "newMission";

  private static final List<AppMenuItem> MENU_ITEMS =
      List.of(
          AppMenuItem.toggle(ITEM_MISSION_PANEL, "Mission panel", "missions/icon-action-view"),
          AppMenuItem.action(
              ITEM_MANAGE_MISSIONS, "Manage missions...", "missions/icon-action-manage"),
          AppMenuItem.action(ITEM_NEW_MISSION, "New mission...", "wizard/icon-plus")
              .withSeparatorBefore());

  private static final String ACTION_DISMISS = "hud.dismiss";

  private final ApplicationContext context;
  private final MissionDisplayPanelRules rules = new MissionDisplayPanelRules();
  private final AppMenuModel menuModel = new AppMenuModel(MENU_ITEMS);

  private AppMenu menu;
  private MissionDisplayPanelWidget widget;
  private Node parentNode;
  private InputManager inputManager;

  public MissionDisplayPanelAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    int sh = app.getCamera().getHeight();
    float topOffset = AppStyles.HUD_MARGIN_PX;

    menu = new AppMenu(context, MENU_ITEMS);
    menu.layoutTopLeft(sh, topOffset);
    menu.setOnTitleClicked(
        () -> {
          menuModel.toggle();
          syncMenu();
        });
    menu.setOnDismiss(
        () -> {
          menuModel.close();
          syncMenu();
        });
    menu.setOnItemClicked(id -> menuModel.select(id).ifPresent(this::runMenuAction));

    parentNode = context.guiGraph().getMissionPanelNode();
    widget = new MissionDisplayPanelWidget(context);
    widget.layoutTopLeft(sh, topOffset);
    widget.setOnManageClicked(this::publishOpenManagement);
    widget.setOnHideAll(this::handleHideAll);
    widget.setRowListener(buildRowListener());
    widget.attachTo(parentNode);
    widget.setVisible(false);
    syncMenu();

    // ESC dismisses the open menu. SimpleApplication binds the same key to quitting the
    // application, and its listener cannot be unregistered from here, so this one owns the key
    // outright and forwards to stop() when there is no menu to close — the app keeps exiting on
    // ESC exactly as before.
    inputManager = app.getInputManager();
    if (inputManager.hasMapping(SimpleApplication.INPUT_MAPPING_EXIT)) {
      inputManager.deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);
    }
    inputManager.addMapping(ACTION_DISMISS, new KeyTrigger(KeyInput.KEY_ESCAPE));
    inputManager.addListener(this, ACTION_DISMISS);
  }

  @Override
  public void update(float tpf) {
    drainTelemetryFocusEvents();
    rules.applyStatusTransitionRules(context.missionContext());
    if (widget != null) {
      widget.update();
    }
    if (menu != null) {
      menu.update(getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    if (inputManager != null) {
      inputManager.removeListener(this);
      inputManager.deleteMapping(ACTION_DISMISS);
      inputManager = null;
    }
    if (widget != null) {
      widget.close();
      widget = null;
    }
    if (menu != null) {
      menu.close();
      menu = null;
    }
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  @Override
  public void onAction(String name, boolean isPressed, float tpf) {
    if (!ACTION_DISMISS.equals(name) || !isPressed) {
      return;
    }
    if (menuModel.isOpen()) {
      menuModel.close();
      syncMenu();
      return;
    }
    getApplication().stop();
  }

  /** Runs the action an enabled entry stands for. The model has already closed the menu. */
  private void runMenuAction(String itemId) {
    switch (itemId) {
      case ITEM_MISSION_PANEL -> togglePanel();
      case ITEM_MANAGE_MISSIONS -> publishOpenManagement();
      case ITEM_NEW_MISSION -> publishOpenWizard();
      default -> throw new IllegalStateException("Unwired menu item: " + itemId);
    }
    syncMenu();
  }

  /**
   * Pushes the model's state onto the widget. The check mark of <i>Mission panel</i> is read back
   * from the panel itself, which is the only place that knows whether it is on screen.
   */
  private void syncMenu() {
    if (menu == null) return;
    menuModel.setChecked(ITEM_MISSION_PANEL, widget != null && widget.isVisible());
    menu.setOpen(menuModel.isOpen());
    for (AppMenuItem item : menuModel.items()) {
      menu.setEnabled(item.id(), menuModel.isEnabled(item.id()));
      if (item.isToggle()) {
        menu.setChecked(item.id(), menuModel.isChecked(item.id()));
      }
    }
  }

  private void togglePanel() {
    if (widget == null) return;
    if (widget.isVisible()) {
      widget.setVisible(false);
    } else {
      widget.show(parentNode);
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
      public void onToggleTelemetry(MissionId missionId, boolean currentlyOn) {
        bus.publishTelemetryFocus(currentlyOn ? null : missionId);
      }

      @Override
      public void onToggleVisibility(MissionId missionId) {
        bus.publishMissionAction(missionId, EventBus.MissionAction.TOGGLE_VISIBLE);
      }
    };
  }

  private void handleHideAll() {
    MissionContext mc = context.missionContext();
    EventBus bus = context.eventBus();
    for (MissionEntry entry : mc.getMissions()) {
      if (entry.mission().getStatus() == MissionStatus.READY && entry.isVisible()) {
        bus.publishMissionAction(entry.id(), EventBus.MissionAction.TOGGLE_VISIBLE);
      }
    }
    // R8: clear telemetry on hide-all.
    bus.publishTelemetryFocus(null);
  }

  private void drainTelemetryFocusEvents() {
    EventBus.MissionTelemetryFocusRequest req;
    MissionContext mc = context.missionContext();
    while ((req = context.eventBus().pollTelemetryFocus()) != null) {
      rules.applyTelemetryFocus(mc, req.missionId());
    }
  }
}
