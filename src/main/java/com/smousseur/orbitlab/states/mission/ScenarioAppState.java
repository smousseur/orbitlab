package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.HudSurface;
import com.smousseur.orbitlab.app.HudSurfaces;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.engine.events.ScenarioBrowserMode;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.scenario.ScenarioLoadReport;
import com.smousseur.orbitlab.simulation.mission.scenario.ScenarioSession;
import com.smousseur.orbitlab.simulation.mission.scenario.ScenarioStore;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.ConfirmDialog;
import com.smousseur.orbitlab.ui.mission.scenario.ScenarioBrowserWidget;
import com.smousseur.orbitlab.ui.mission.wizard.WizardPrefill;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owner of the scenario browser, and the only place a session is swapped (spec {@code
 * docs/scenario/01-persistance-missions.md} §6.3).
 *
 * <p>It knows nothing of the menu that opens it and the menu knows nothing of it: the two speak
 * through {@link EventBus} and {@link ApplicationContext}, and the project rule against {@code
 * getState()} admits no exception here.
 *
 * <p><b>The file is entirely converted before a single current mission is destroyed.</b> {@link
 * ScenarioSession#restore} has already returned its entries by the time anything is removed, so a
 * corrupt or half-readable file costs a message and nothing else — never the session on screen.
 */
public final class ScenarioAppState extends BaseAppState {

  private static final Logger logger = LogManager.getLogger(ScenarioAppState.class);

  private final ApplicationContext context;
  private final ScenarioStore store;

  private ScenarioBrowserModel model;
  private ScenarioBrowserWidget browser;
  private AutoCloseable surfaceHandle;
  private ConfirmDialog confirmDialog;
  private AutoCloseable confirmDialogHandle;

  public ScenarioAppState(ApplicationContext context) {
    this(context, ScenarioStore.inUserHome());
  }

  /**
   * @param context the application context
   * @param store where scenarios are read and written
   */
  public ScenarioAppState(ApplicationContext context, ScenarioStore store) {
    this.context = Objects.requireNonNull(context, "context");
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  protected void initialize(Application app) {
    surfaceHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.SCENARIO_BROWSER,
                    UiLayers.MODAL,
                    this::isBrowserVisible,
                    this::closeBrowser));
  }

  @Override
  protected void cleanup(Application app) {
    closeConfirmDialog();
    closeBrowser();
    HudSurfaces.closeQuietly(surfaceHandle, logger);
    surfaceHandle = null;
  }

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}

  @Override
  public void update(float tpf) {
    EventBus.UiNavigationEvent.OpenScenarioBrowser open =
        context.eventBus().pollOpenScenarioBrowser();
    if (open != null) {
      openBrowser(open.mode());
    }
    if (confirmDialog != null) {
      confirmDialog.update(getApplication().getCamera());
    }
    if (browser != null) {
      browser.update(tpf, getApplication().getCamera());
    }
  }

  private boolean isBrowserVisible() {
    return browser != null && browser.isVisible();
  }

  /**
   * Opens the browser in the requested mode, rebuilding both model and window.
   *
   * <p>Neither survives a closing: the mode is fixed for the life of a window, and the listing is
   * read from disk each time — a scenario saved by another instance since the last opening has to
   * show up.
   */
  private void openBrowser(ScenarioBrowserMode mode) {
    closeBrowser();

    model = new ScenarioBrowserModel(mode, listEntries());
    browser = new ScenarioBrowserWidget(mode);
    browser.setOnCancel(this::closeBrowser);
    browser.setOnConfirm(this::confirmSelection);
    browser.setOnRowClicked(
        name -> {
          model.select(name);
          browser.setName(model.typedName());
          syncBrowser();
        });
    browser.setOnNameChanged(
        name -> {
          model.setTypedName(name);
          syncBrowser();
        });
    browser.attachTo(context.guiGraph().getModalNode());
    syncBrowser();
  }

  /**
   * Pushes the model onto the window, the way {@code MissionDisplayPanelAppState} syncs its menu.
   */
  private void syncBrowser() {
    if (browser == null || model == null) return;
    List<ScenarioBrowserWidget.Row> rows = new ArrayList<>();
    for (ScenarioBrowserModel.Entry entry : model.entries()) {
      rows.add(new ScenarioBrowserWidget.Row(entry.name(), entry.savedAt(), entry.missionCount()));
    }
    browser.setRows(rows, model.selectedName().orElse(null));
    browser.setConfirmEnabled(model.isConfirmEnabled());
  }

  private void closeBrowser() {
    if (browser != null) {
      browser.close();
      browser = null;
    }
    model = null;
  }

  /**
   * Reads what the list can say about each scenario without opening it.
   *
   * <p>A file that cannot be parsed is <b>still listed</b>, with no date and no count. Hiding it
   * would leave the user staring at a name that exists on disk and nowhere on screen; listed, it
   * says what it is, and confirming it fails with the reason.
   */
  private List<ScenarioBrowserModel.Entry> listEntries() {
    List<String> names;
    try {
      names = store.list();
    } catch (RuntimeException e) {
      logger.error("Scenario folder {} cannot be listed", store.directory(), e);
      return List.of();
    }

    List<ScenarioBrowserModel.Entry> entries = new ArrayList<>();
    for (String name : names) {
      try {
        ScenarioFile file = store.read(name);
        entries.add(new ScenarioBrowserModel.Entry(name, file.savedAt(), file.missions().size()));
      } catch (RuntimeException e) {
        logger.warn("Scenario '{}' cannot be read: {}", name, e.getMessage());
        entries.add(new ScenarioBrowserModel.Entry(name, null, 0));
      }
    }
    return entries;
  }

  /**
   * Runs the confirm button: straight through, or behind a question when something would be lost.
   *
   * <p>Two irreversible gestures, two confirmations, on the pattern already in place for
   * <i>Quit</i> and for a mission deletion: opening replaces the missions on screen, saving over an
   * existing name replaces a file.
   */
  private void confirmSelection() {
    if (model == null) return;
    String name = model.targetName().orElse(null);
    if (name == null) return;

    if (model.mode() == ScenarioBrowserMode.SAVE) {
      if (model.wouldOverwrite()) {
        askThen("Overwrite scenario '" + name + "'?", () -> saveScenario(name));
      } else {
        saveScenario(name);
      }
      return;
    }
    if (context.missionContext().getMissions().isEmpty()) {
      openScenario(name);
    } else {
      askThen("Replace the current missions?", () -> openScenario(name));
    }
  }

  private void askThen(String question, Runnable action) {
    if (confirmDialog != null) return;

    confirmDialog = new ConfirmDialog(question);
    confirmDialog.setOnCancel(this::closeConfirmDialog);
    confirmDialog.setOnConfirm(
        () -> {
          closeConfirmDialog();
          action.run();
        });
    confirmDialog.attachTo(context.guiGraph().getModalNode());
    // Registered only now: before attachTo, isVisible() would report open with nothing yet on
    // screen, which HudSurface's contract does not allow.
    confirmDialogHandle =
        context
            .hudSurfaces()
            .register(
                new HudSurface(
                    HudSurface.SCENARIO_DIALOG,
                    UiLayers.DIALOG,
                    confirmDialog::isVisible,
                    this::closeConfirmDialog));
  }

  private void closeConfirmDialog() {
    if (confirmDialog != null) {
      confirmDialog.close();
      confirmDialog = null;
    }
    HudSurfaces.closeQuietly(confirmDialogHandle, logger);
    confirmDialogHandle = null;
  }

  private void saveScenario(String name) {
    try {
      ScenarioFile file =
          ScenarioSession.capture(
              context.missionContext().getMissions(),
              WizardPrefill::fromEntry,
              context.clock().now());
      store.write(name, file);
      logger.info("Scenario '{}' saved with {} mission(s)", name, file.missions().size());
      closeBrowser();
    } catch (RuntimeException e) {
      // Caught wide, and on purpose: this runs on the render thread, where an escaping exception
      // takes the frame loop with it. The window stays open on a failure — the user still has the
      // name they typed, and closing would leave them with nothing but a log line.
      logger.error("Scenario '{}' could not be saved", name, e);
    }
  }

  /**
   * Replaces the session with the one the file describes.
   *
   * <p>The order is the invariant of §6.3 and not a matter of taste: the file is read and converted
   * <b>first</b>, and only a report in hand authorises destroying anything. The clock follows,
   * because the orchestrator hides a mission whose ephemeris starts after the current instant — a
   * scenario launching in six months would otherwise restore into a black screen.
   */
  private void openScenario(String name) {
    ScenarioLoadReport report;
    EventBus eventBus = context.eventBus();
    eventBus.publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
    try {
      report = ScenarioSession.restore(store.read(name));
    } catch (RuntimeException e) {
      // Nothing has been destroyed at this point, and nothing will be: a file that cannot be read
      // costs a log line and leaves the session exactly as it was.
      logger.error("Scenario '{}' could not be opened", name, e);
      return;
    }

    MissionContext missionContext = context.missionContext();
    // Through the event bus rather than by hand: the orchestrator owns the renderers, and it is the
    // only place that can dispose one and drop a camera that was following it.
    for (MissionEntry entry : List.copyOf(missionContext.getMissions())) {
      eventBus.publishMissionAction(entry.id(), EventBus.MissionAction.DELETE);
    }
    missionContext.setSelectedMissionId(null);
    missionContext.setTelemetryFocusMissionId(null);

    for (MissionEntry entry : report.missions()) {
      missionContext.addMission(entry);
    }
    if (report.hasClockDate()) {
      context.clock().seek(report.clockDate());
    }
    // Submitted last, once every mission is registered: the orchestrator resolves the request
    // against the context, and a mission not yet in it would simply be dropped.
    for (MissionEntry entry : report.missions()) {
      if (entry.getPendingSolutions().isPresent()) {
        eventBus.publishMissionAction(entry.id(), EventBus.MissionAction.OPTIMIZE);
      }
    }

    logger.info(
        "Scenario '{}' opened: {} mission(s) restored, {} rejected",
        name,
        report.missions().size(),
        report.rejections().size());
    for (ScenarioLoadReport.Rejection rejection : report.rejections()) {
      logger.warn("Mission '{}' was not restored: {}", rejection.missionName(), rejection.reason());
    }
    closeBrowser();
  }
}
