package com.smousseur.orbitlab.ui.mission;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.ui.AppStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A Lemur-based GUI widget for mission management. Provides a toggle button that reveals a panel
 * listing all missions from the {@link MissionContext}, with selection support and contextual action
 * buttons (Edit, Optimize, Delete, Launch) that appear or hide based on the selected mission's
 * status.
 *
 * <p>Actions are published asynchronously to the {@link EventBus} and consumed by the mission
 * orchestrator on the JME update thread.
 *
 * <p>Implements {@link AutoCloseable} to detach itself from the scene graph when no longer needed.
 */
public class MissionPanelWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionPanelWidget.class);

  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;
  private static final float PANEL_WIDTH = 260f;

  private final MissionContext missionContext;
  private final EventBus eventBus;

  private final Container root;
  private final Container mainPanel;
  private final Container listContainer;
  private final Container actionBar;

  private boolean panelVisible = false;
  private String selectedMissionName;
  private List<String> lastSnapshot = List.of();

  /**
   * Creates and attaches the mission panel widget to the GUI scene graph.
   *
   * @param context the application context providing mission context, event bus, and GUI graph
   */
  public MissionPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();
    this.eventBus = context.eventBus();

    Node missionPanelNode = context.guiGraph().getMissionPanelNode();

    // Root container
    this.root = new Container(new BoxLayout(Axis.Y, FillMode.None), MissionPanelStyles.STYLE);
    missionPanelNode.attachChild(root);

    // Toggle button — always visible
    Button toggleButton = root.addChild(new Button("Missions", MissionPanelStyles.STYLE));
    toggleButton.setBackground(new QuadBackgroundComponent(AppStyles.ICE_ACCENT));
    toggleButton.addClickCommands(source -> togglePanel());

    // Main panel — hidden by default
    this.mainPanel =
        root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None), MissionPanelStyles.STYLE));
    mainPanel.setPreferredSize(new Vector3f(PANEL_WIDTH, 0, 0));
    mainPanel.setCullHint(Spatial.CullHint.Always);

    // Header row: title + create button
    Container headerRow =
        mainPanel.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.Even), MissionPanelStyles.STYLE));
    Label titleLabel = headerRow.addChild(new Label("MISSIONS", MissionPanelStyles.STYLE));
    titleLabel.setColor(AppStyles.ICE_ACCENT);
    Button createButton = headerRow.addChild(new Button("+ Creer", MissionPanelStyles.STYLE));
    createButton.setBackground(new QuadBackgroundComponent(AppStyles.ICE_ACCENT));
    createButton.addClickCommands(source -> onCreate());

    // Separator
    Container separator =
        mainPanel.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    separator.setBackground(new QuadBackgroundComponent(AppStyles.ICE_BORDER));
    separator.setPreferredSize(new Vector3f(PANEL_WIDTH, 1, 0));

    // Mission list
    this.listContainer =
        mainPanel.addChild(
            new Container(new BoxLayout(Axis.Y, FillMode.None), MissionPanelStyles.STYLE));
    listContainer.setBackground(new QuadBackgroundComponent(AppStyles.ICE_PANEL_BG));

    // Action bar
    this.actionBar =
        mainPanel.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.Even), MissionPanelStyles.STYLE));
  }

  /**
   * Updates the widget each frame. Detects changes in the mission list and refreshes the UI only
   * when needed.
   *
   * @param tpf time per frame in seconds
   */
  public void update(float tpf) {
    if (!panelVisible) {
      return;
    }

    List<String> currentSnapshot = buildSnapshot();
    boolean selectionStillValid = selectedMissionName != null
        && missionContext.findMission(selectedMissionName).isPresent();

    if (!selectionStillValid && selectedMissionName != null) {
      selectedMissionName = null;
    }

    if (!currentSnapshot.equals(lastSnapshot)) {
      lastSnapshot = currentSnapshot;
      refreshMissionList();
      rebuildActionBar();
    }
  }

  /**
   * Positions the widget at the top-left corner of the screen.
   *
   * @param screenWidth the screen width in pixels
   * @param screenHeight the screen height in pixels
   */
  public void layoutTopLeft(int screenWidth, int screenHeight) {
    float x = MARGIN_PX;
    float y = screenHeight - MARGIN_PX;
    root.setLocalTranslation(x, y, 0f);
  }

  @Override
  public void close() {
    root.removeFromParent();
  }

  // -------------------------------------------------------------------------
  // Internal
  // -------------------------------------------------------------------------

  private void togglePanel() {
    panelVisible = !panelVisible;
    if (panelVisible) {
      mainPanel.setCullHint(Spatial.CullHint.Inherit);
      lastSnapshot = List.of(); // force refresh
      refreshMissionList();
      rebuildActionBar();
    } else {
      mainPanel.setCullHint(Spatial.CullHint.Always);
    }
  }

  private List<String> buildSnapshot() {
    List<MissionEntry> missions = missionContext.getMissions();
    List<String> snapshot = new ArrayList<>(missions.size());
    for (MissionEntry entry : missions) {
      snapshot.add(entry.mission().getName() + ":" + entry.mission().getStatus());
    }
    return snapshot;
  }

  private void refreshMissionList() {
    listContainer.clearChildren();
    List<MissionEntry> missions = missionContext.getMissions();

    if (missions.isEmpty()) {
      Label emptyLabel = listContainer.addChild(new Label("No missions", MissionPanelStyles.STYLE));
      emptyLabel.setColor(AppStyles.ICE_TEXT_SECONDARY);
      return;
    }

    for (MissionEntry entry : missions) {
      String name = entry.mission().getName();
      MissionStatus status = entry.mission().getStatus();
      boolean isSelected = name.equals(selectedMissionName);

      Button row = listContainer.addChild(new Button("", MissionPanelStyles.STYLE));
      row.setText(formatRow(name, status));
      row.setTextHAlignment(com.simsilica.lemur.HAlignment.Left);
      row.setBackground(
          new QuadBackgroundComponent(
              isSelected ? AppStyles.ICE_ROW_SELECTED : AppStyles.ICE_PANEL_BG_LIGHT));
      row.setColor(isSelected ? AppStyles.ICE_TEXT_PRIMARY : statusColor(status));
      row.addClickCommands(source -> selectMission(name));
    }
  }

  private void selectMission(String name) {
    if (name.equals(selectedMissionName)) {
      selectedMissionName = null; // deselect on second click
    } else {
      selectedMissionName = name;
    }
    refreshMissionList();
    rebuildActionBar();
  }

  private void rebuildActionBar() {
    actionBar.clearChildren();

    if (selectedMissionName == null) {
      return;
    }

    MissionEntry entry = missionContext.findMission(selectedMissionName).orElse(null);
    if (entry == null) {
      selectedMissionName = null;
      return;
    }

    MissionStatus status = entry.mission().getStatus();

    switch (status) {
      case DRAFT -> {
        addActionButton("Editer", AppStyles.ICE_ACCENT, this::onEdit);
        addActionButton("Optimiser", AppStyles.ICE_WARNING, this::onOptimize);
        addActionButton("Supprimer", AppStyles.ICE_DANGER, this::onDelete);
      }
      case OPTIMIZING -> {
        Label label = actionBar.addChild(new Label("Optimisation...", MissionPanelStyles.STYLE));
        label.setColor(AppStyles.ICE_WARNING);
      }
      case READY -> {
        addActionButton("Editer", AppStyles.ICE_ACCENT, this::onEdit);
        addActionButton("Optimiser", AppStyles.ICE_WARNING, this::onOptimize);
        addActionButton("Supprimer", AppStyles.ICE_DANGER, this::onDelete);
        addActionButton("Lancer", AppStyles.ICE_SUCCESS, this::onStart);
      }
      case RUNNING -> {
        Label label = actionBar.addChild(new Label("En cours...", MissionPanelStyles.STYLE));
        label.setColor(AppStyles.ICE_SUCCESS);
      }
      case COMPLETED -> {
        addActionButton("Supprimer", AppStyles.ICE_DANGER, this::onDelete);
      }
      case FAILED -> {
        addActionButton("Editer", AppStyles.ICE_ACCENT, this::onEdit);
        addActionButton("Optimiser", AppStyles.ICE_WARNING, this::onOptimize);
        addActionButton("Supprimer", AppStyles.ICE_DANGER, this::onDelete);
      }
    }
  }

  private void addActionButton(String label, com.jme3.math.ColorRGBA bgColor, Runnable action) {
    Button button = actionBar.addChild(new Button(label, MissionPanelStyles.STYLE));
    button.setBackground(new QuadBackgroundComponent(bgColor));
    button.setColor(AppStyles.ICE_TEXT_PRIMARY);
    button.addClickCommands(source -> action.run());
  }

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------

  private void onEdit() {
    logger.info("Edit not yet implemented for mission '{}'", selectedMissionName);
  }

  private void onCreate() {
    logger.info("Create mission not yet implemented");
  }

  private void onOptimize() {
    if (selectedMissionName != null) {
      eventBus.publishMissionAction(selectedMissionName, EventBus.MissionAction.OPTIMIZE);
    }
  }

  private void onStart() {
    if (selectedMissionName != null) {
      eventBus.publishMissionAction(selectedMissionName, EventBus.MissionAction.START);
    }
  }

  private void onDelete() {
    if (selectedMissionName != null) {
      String name = selectedMissionName;
      selectedMissionName = null;
      eventBus.publishMissionAction(name, EventBus.MissionAction.DELETE);
    }
  }

  // -------------------------------------------------------------------------
  // Formatting
  // -------------------------------------------------------------------------

  private static String formatRow(String name, MissionStatus status) {
    return name + "  [" + status + "]";
  }

  private static com.jme3.math.ColorRGBA statusColor(MissionStatus status) {
    return switch (status) {
      case DRAFT -> AppStyles.ICE_TEXT_SECONDARY;
      case OPTIMIZING -> AppStyles.ICE_WARNING;
      case READY -> AppStyles.ICE_SUCCESS;
      case RUNNING -> AppStyles.ICE_ACCENT;
      case COMPLETED -> AppStyles.ICE_TEXT_SECONDARY;
      case FAILED -> AppStyles.ICE_DANGER;
    };
  }
}
