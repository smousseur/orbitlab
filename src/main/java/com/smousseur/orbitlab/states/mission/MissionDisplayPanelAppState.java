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
import com.smousseur.orbitlab.app.HudSurface;
import com.smousseur.orbitlab.app.HudSurfaces;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.engine.events.ScenarioBrowserMode;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.AppStyles;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.ConfirmDialog;
import com.smousseur.orbitlab.ui.mission.display.MissionDisplayPanelWidget;
import com.smousseur.orbitlab.ui.menu.AppMenu;
import com.smousseur.orbitlab.ui.menu.AppMenuItem;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owns the top-left {@link AppMenu} and the non-modal {@link MissionDisplayPanelWidget}. Drains
 * telemetry focus events, applies the panel's auto-coherence rules ({@link
 * MissionDisplayPanelRules}), and forwards UI actions through the {@link EventBus}.
 */
public final class MissionDisplayPanelAppState extends BaseAppState implements ActionListener {

  private static final Logger logger = LogManager.getLogger(MissionDisplayPanelAppState.class);

  /** Toggles the mission display panel; carries a check mark, like {@code Mission management}. */
  private static final String ITEM_MISSION_PANEL = "missionPanel";

  /** Toggles the mission management window; carries a check mark like the display panel. */
  private static final String ITEM_MANAGE_MISSIONS = "manageMissions";

  /** Opens the mission creation wizard. */
  private static final String ITEM_NEW_MISSION = "newMission";

  /** Opens the scenario browser to read a saved session. */
  private static final String ITEM_OPEN_SCENARIO = "openScenario";

  /** Opens the scenario browser to write the current session. */
  private static final String ITEM_SAVE_SCENARIO = "saveScenario";

  /** Quits the application, behind a confirmation. */
  private static final String ITEM_QUIT = "quit";

  private static final List<AppMenuItem> MENU_ITEMS =
      List.of(
          AppMenuItem.toggle(ITEM_MISSION_PANEL, "Mission panel", "missions/icon-action-view"),
          AppMenuItem.toggle(
              ITEM_MANAGE_MISSIONS, "Mission management", "missions/icon-action-manage"),
          AppMenuItem.action(ITEM_NEW_MISSION, "New mission...", "wizard/icon-plus")
              .withSeparatorBefore(),
          AppMenuItem.action(ITEM_OPEN_SCENARIO, "Open scenario...", "scenario/icon-open")
              .withSeparatorBefore(),
          AppMenuItem.action(ITEM_SAVE_SCENARIO, "Save scenario...", "scenario/icon-save"),
          AppMenuItem.action(ITEM_QUIT, "Quit", "wizard/icon-close-red").withSeparatorBefore());

  private static final String ACTION_DISMISS = "hud.dismiss";

  private final ApplicationContext context;
  private final MissionDisplayPanelRules rules = new MissionDisplayPanelRules();
  private final AppMenuModel menuModel = new AppMenuModel(MENU_ITEMS);

  private AppMenu menu;
  private MissionDisplayPanelWidget widget;
  private Node parentNode;
  private InputManager inputManager;
  private AutoCloseable menuSurfaceHandle;
  private AutoCloseable quitDialogHandle;
  private ConfirmDialog quitDialog;

  public MissionDisplayPanelAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    int sh = app.getCamera().getHeight();
    // Under the breadcrumb band, not against the top edge: the band is permanent and reserves its
    // own height (docs/navigation/01-breadcrumb.md §5.5).
    float topOffset = AppStyles.HUD_TOP_OFFSET_PX;

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

    menuSurfaceHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.APP_MENU,
                    UiLayers.MENU_DROPDOWN,
                    menuModel::isOpen,
                    () -> {
                      menuModel.close();
                      syncMenu();
                    }));

    parentNode = context.guiGraph().getMissionPanelNode();
    widget = new MissionDisplayPanelWidget(context);
    widget.layoutTopLeft(sh, topOffset);
    widget.setOnManageClicked(this::publishOpenManagement);
    widget.setOnHideAll(this::handleHideAll);
    widget.setRowListener(buildRowListener());
    widget.attachTo(parentNode);
    widget.setVisible(false);
    syncMenu();

    // ESC sends away the topmost open surface, and nothing else. SimpleApplication binds the same
    // key to quitting the application and its listener cannot be unregistered from here, so this
    // one owns the key outright; quitting has moved to the menu's Quit entry, behind a
    // confirmation (docs/ui/01-surfaces-et-modalite.md §6.2).
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
    if (quitDialog != null) {
      quitDialog.update(getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    if (inputManager != null) {
      inputManager.removeListener(this);
      inputManager.deleteMapping(ACTION_DISMISS);
      inputManager = null;
    }
    closeQuitDialog();
    HudSurfaces.closeQuietly(menuSurfaceHandle, logger);
    menuSurfaceHandle = null;
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
    context.hudSurfaces().dismissTopmost();
  }

  /** Runs the action an enabled entry stands for. The model has already closed the menu. */
  private void runMenuAction(String itemId) {
    switch (itemId) {
      case ITEM_MISSION_PANEL -> togglePanel();
      case ITEM_MANAGE_MISSIONS -> publishOpenManagement();
      case ITEM_NEW_MISSION -> publishOpenWizard();
      case ITEM_OPEN_SCENARIO -> publishOpenScenarioBrowser(ScenarioBrowserMode.OPEN);
      case ITEM_SAVE_SCENARIO -> publishOpenScenarioBrowser(ScenarioBrowserMode.SAVE);
      case ITEM_QUIT -> confirmQuit();
      default -> throw new IllegalStateException("Unwired menu item: " + itemId);
    }
    syncMenu();
  }

  /**
   * Pushes the model's state onto the widget. The check mark of <i>Mission panel</i> is read back
   * from the panel itself, which is the only place that knows whether it is on screen; the one of
   * <i>Mission management</i> is read from the surface registry, since {@code CLAUDE.md} forbids
   * asking {@code MissionPanelWidgetAppState} directly.
   *
   * <p>{@code syncMenu()} runs only on menu interactions, so both marks are correct every time the
   * dropdown opens — the only moment they are visible.
   */
  private void syncMenu() {
    if (menu == null) return;
    menuModel.setChecked(ITEM_MISSION_PANEL, widget != null && widget.isVisible());
    menuModel.setChecked(
        ITEM_MANAGE_MISSIONS, context.hudSurfaces().isOpen(HudSurface.MISSION_MANAGEMENT));
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

  /**
   * Asks for the mission management window. The event is a toggle, so the <i>Manage</i> button of
   * the display panel closes the window when it is already on screen.
   */
  private void publishOpenManagement() {
    context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
  }

  private void publishOpenWizard() {
    context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());
  }

  /**
   * Asks for the scenario browser. Which mode it opens in is decided here and nowhere else — the
   * window is built per opening and never switches mode once up.
   */
  private void publishOpenScenarioBrowser(ScenarioBrowserMode mode) {
    context
        .eventBus()
        .publishUiNavigation(new EventBus.UiNavigationEvent.OpenScenarioBrowser(mode));
  }

  /**
   * Opens the quit confirmation. The dialog registers itself as a surface, so ESC cancels it the
   * same way the Cancel button does.
   */
  private void confirmQuit() {
    if (quitDialog != null) return;

    quitDialog = new ConfirmDialog("Quit OrbitLab?");
    quitDialog.setOnCancel(this::closeQuitDialog);
    quitDialog.setOnConfirm(
        () -> {
          closeQuitDialog();
          getApplication().stop();
        });
    quitDialog.attachTo(context.guiGraph().getModalNode());
    // Registered only now: before attachTo, isVisible() (and the dialog itself) would report
    // open with nothing yet on screen, which HudSurface's contract does not allow.
    quitDialogHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.QUIT_DIALOG,
                    UiLayers.DIALOG,
                    quitDialog::isVisible,
                    this::closeQuitDialog));
  }

  private void closeQuitDialog() {
    if (quitDialog != null) {
      quitDialog.close();
      quitDialog = null;
    }
    HudSurfaces.closeQuietly(quitDialogHandle, logger);
    quitDialogHandle = null;
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
