package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.HudSurface;
import com.smousseur.orbitlab.app.HudSurfaces;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.ConfirmDialog;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.form.WindowDragHandler;
import com.smousseur.orbitlab.ui.mission.detail.MissionDetailView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Non-modal mission management window: orchestrates a {@link PanelHeader}, a {@link
 * MissionListView} and a {@link PanelFooter} on the {@link UiLayers#WINDOW} layer. Per-row actions
 * (Edit / Compute / Visualize / Delete) are routed to {@link MissionContext} and the {@link
 * EventBus}.
 *
 * <p>It carries no backdrop: the scene below stays live and only the window's own bounds swallow
 * clicks. Its delete confirmation is another matter — a {@link ConfirmDialog} is blocking by nature
 * and keeps its own veil on the modal layer, above this window.
 */
public class MissionPanelWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionPanelWidget.class);

  private static final float WINDOW_WIDTH = 720f;
  // 640 rather than 520, for two reasons that stack. The footer's result line costs 22 px and the
  // list needs its full 345 px for five rows, which alone would call for 545. On top of that the
  // detail screen has to hold a GEO mission's stage chain: 12 stages (vertical ascent, three
  // gravity-turn phases, then parking through the final coast) is 234 px of table under 158 px of
  // header, orbit block and totals, so 392 px of content against the 412 this height leaves once
  // the detail view's own 20 px margins are taken. BoxLayout in FillMode.None neither shrinks nor
  // clips its children, so a content area too short does not scroll — it draws over the footer.
  private static final float WINDOW_HEIGHT = 640f;
  private static final float CONTENT_HEIGHT =
      WINDOW_HEIGHT - PanelHeader.HEIGHT - PanelFooter.HEIGHT;

  private static final String DELETE_CONFIRM_MESSAGE = "Are you sure ?";

  private final MissionContext missionContext;
  private final EventBus eventBus;
  private final HudSurfaces hudSurfaces;

  private final Container root;
  private final MissionListView listView;
  private final PanelFooter footer;

  /** Modal node the delete confirmation is attached to; resolved once, at construction. */
  private final Node modalNode;

  private ConfirmDialog confirmDialog;
  private AutoCloseable confirmDialogHandle;

  /** Position the window opens at; null means "centre it". Set by the app state on reopening. */
  private Vector3f initialPosition;

  private boolean placed = false;
  private int lastWidth;
  private int lastHeight;

  private MissionId selectedMissionId;

  /** Which of the two content screens the panel is showing. */
  private enum Screen {
    LIST,
    DETAIL
  }

  private Screen screen = Screen.LIST;
  private MissionId detailMissionId;
  private Container detailNode;

  private List<String> lastSnapshot = List.of();
  private boolean visible = false;

  private Runnable onClose = () -> {};
  private Runnable onNewMission = () -> {};

  public MissionPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();
    this.eventBus = context.eventBus();
    this.hudSurfaces = context.hudSurfaces();
    this.modalNode = context.guiGraph().getModalNode();
    this.selectedMissionId = missionContext.getSelectedMissionId();

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.getInsetsComponent().setInsets(new Insets3f(0, 0, 0, 0));
    root.setBorder(null);
    FormStyles.clearMargin(root.getBackground());

    PanelHeader header = new PanelHeader(WINDOW_WIDTH);
    header.setOnClose(() -> onClose.run());

    // The window moves by its header. The close icon keeps its own listener: button events are
    // delivered to the picked spatial, and the icon is picked before the band behind it.
    CursorEventControl.addListenersToSpatial(
        header.getNode(), new WindowDragHandler(root, PanelHeader.HEIGHT));

    listView = new MissionListView(WINDOW_WIDTH, CONTENT_HEIGHT);
    listView.setOnNewMission(() -> onNewMission.run());
    listView.setRowListener(buildRowListener());

    footer = new PanelFooter(WINDOW_WIDTH);
    footer.setOnShowDetails(entry -> showDetail(entry.id()));

    root.addChild(header.getNode());
    root.addChild(listView.getNode());
    root.addChild(footer.getNode());

    // The window has no backdrop of its own, so this is what stops a click inside it from reaching
    // the 3D scene and selecting a body: only its own bounds swallow clicks.
    MouseEventControl.addListenersToSpatial(
        root,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }
        });

    refresh();
  }

  /**
   * Attaches the window to the mission panel layer. It is not modal: the scene below stays live,
   * and only the window's own bounds swallow clicks.
   *
   * @param parentNode the node this window lives in — {@code guiGraph().getMissionPanelNode()}
   */
  public void attachTo(Node parentNode) {
    parentNode.attachChild(root);
    visible = true;
  }

  @Override
  public void close() {
    closeConfirmDialog();
    root.removeFromParent();
    visible = false;
  }

  public boolean isVisible() {
    return visible;
  }

  public void update(float tpf, Camera cam) {
    if (!visible) return;
    place(cam.getWidth(), cam.getHeight());
    if (confirmDialog != null) {
      confirmDialog.update(cam);
    }

    List<String> snapshot = buildSnapshot();
    if (selectedMissionId != null && missionContext.findMission(selectedMissionId).isEmpty()) {
      selectedMissionId = null;
    }
    if (!snapshot.equals(lastSnapshot)) {
      lastSnapshot = snapshot;
      refresh();
    }

    // Advancement is deliberately absent from the snapshot: a computation leaves the status alone,
    // so the list is not rebuilt while it runs and the two widgets below mutate their own spinner
    // and label in place. Putting an evaluation counter in the snapshot would rebuild every row at
    // the counter's own rate, for as long as the computation lasts.
    if (screen == Screen.LIST) {
      listView.update(tpf);
    }
    footer.update(tpf);
  }

  public void setOnClose(Runnable action) {
    this.onClose = action != null ? action : () -> {};
  }

  public void setOnNewMission(Runnable action) {
    this.onNewMission = action != null ? action : () -> {};
  }

  private MissionListView.RowListener buildRowListener() {
    return new MissionListView.RowListener() {
      @Override
      public void onSelect(MissionId missionId) {
        selectMission(missionId);
      }

      @Override
      public void onEdit(MissionId missionId) {
        logger.info("Editing mission [{}]", missionId.shortForm());
        eventBus.publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard(missionId));
      }

      @Override
      public void onCompute(MissionId missionId) {
        eventBus.publishMissionAction(missionId, EventBus.MissionAction.OPTIMIZE);
      }

      @Override
      public void onSetMode(MissionId missionId, OptimizationType type) {
        missionContext.findMission(missionId).ifPresent(e -> e.setOptimizationType(type));
        refresh();
      }

      @Override
      public void onDelete(MissionId missionId) {
        confirmDelete(missionId);
      }
    };
  }

  /**
   * Opens the confirmation dialog for a deletion. Nothing is removed until the user clicks OK; the
   * dialog's own backdrop shields this window meanwhile, so no second confirmation can stack.
   *
   * <p>The dialog registers itself as a surface, so ESC cancels it the same way the Cancel button
   * does — and, being the higher layer, it is sent away before the window underneath it.
   */
  private void confirmDelete(MissionId missionId) {
    if (confirmDialog != null) return;

    confirmDialog = new ConfirmDialog(DELETE_CONFIRM_MESSAGE);
    confirmDialog.setOnCancel(this::closeConfirmDialog);
    confirmDialog.setOnConfirm(
        () -> {
          closeConfirmDialog();
          deleteMission(missionId);
        });
    confirmDialog.attachTo(modalNode);
    // Registered only now: before attachTo, isVisible() would report open with nothing yet on
    // screen, which HudSurface's contract does not allow.
    confirmDialogHandle =
        hudSurfaces.register(
            new HudSurface(
                HudSurface.DELETE_DIALOG,
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

  private void deleteMission(MissionId missionId) {
    if (missionId.equals(selectedMissionId)) {
      selectedMissionId = null;
    }
    if (missionId.equals(detailMissionId)) {
      showList();
    }
    missionContext.removeMission(missionId);
    eventBus.publishMissionAction(missionId, EventBus.MissionAction.DELETE);
  }

  private void selectMission(MissionId missionId) {
    if (missionId.equals(selectedMissionId)) {
      selectedMissionId = null;
    } else {
      selectedMissionId = missionId;
    }
    refresh();
  }

  private void showDetail(MissionId missionId) {
    detailMissionId = missionId;
    screen = Screen.DETAIL;
    refresh();
  }

  private void showList() {
    detailMissionId = null;
    screen = Screen.LIST;
    refresh();
  }

  /**
   * Detaches whichever content screen is currently mounted between the header and the footer. Both
   * screens are rebuilt from scratch on every refresh, so nothing is lost by removing them.
   */
  private void clearContent() {
    listView.getNode().removeFromParent();
    if (detailNode != null) {
      detailNode.removeFromParent();
      detailNode = null;
    }
  }

  private void refresh() {
    List<MissionEntry> entries = missionContext.getMissions();
    MissionEntry selected = missionContext.findMission(selectedMissionId).orElse(null);
    footer.setSelectedMission(selected);

    MissionEntry detailed =
        screen == Screen.DETAIL ? missionContext.findMission(detailMissionId).orElse(null) : null;
    if (screen == Screen.DETAIL
        && (detailed == null || detailed.mission().getStatus() == MissionStatus.COMPUTING)) {
      screen = Screen.LIST;
      detailMissionId = null;
      detailed = null;
    }

    clearContent();

    if (screen == Screen.DETAIL) {
      MissionDetailView detail = new MissionDetailView(detailed, WINDOW_WIDTH, CONTENT_HEIGHT);
      detail.setOnBack(this::showList);
      detailNode = detail.getNode();
      root.addChild(detailNode);
    } else {
      listView.refresh(entries, selectedMissionId);
      root.addChild(listView.getNode());
    }

    // Lemur appends children, so re-adding the content would leave it below the footer.
    footer.getNode().removeFromParent();
    root.addChild(footer.getNode());
  }

  private List<String> buildSnapshot() {
    List<MissionEntry> entries = missionContext.getMissions();
    MissionId telemetered = missionContext.getTelemetryFocusMissionId();
    List<String> snapshot = new ArrayList<>(entries.size() + 2);
    snapshot.add("sel=" + (selectedMissionId == null ? "" : selectedMissionId));
    snapshot.add("tel=" + (telemetered == null ? "" : telemetered));
    snapshot.add("screen=" + screen + ":" + (detailMissionId == null ? "" : detailMissionId));
    for (MissionEntry entry : entries) {
      // Keyed on the id so two homonymous missions produce distinct snapshot lines. The
      // optimization type is part of the snapshot because the footer's details line shows it: a
      // mode set outside this panel must still redraw the selection details. The result markers
      // are there for the same reason at the result line: a FAILED -> FAILED recomputation with a
      // different message, or a result landing while the status is already READY, changes what the
      // footer and the detail view must say without changing the status.
      //
      // The composition revision stands for everything a wizard edit rewrites at once — the name in
      // the row, the type and the site in the footer, the stage chain in the detail view — none of
      // which moves the status or the result markers. Listing those fields here instead would go
      // stale the next time one of the three surfaces renders one more of them.
      snapshot.add(
          entry.id()
              + ":"
              + entry.mission().getStatus()
              + ":"
              + entry.isVisible()
              + ":"
              + entry.getOptimizationType()
              + ":"
              + entry.getAchievedOrbit().isPresent()
              + ":"
              + entry.getLastError().orElse("")
              + ":"
              + entry.compositionRevision()
              // Set beside applySpec() on the edit path, and on its own by a launch-window
              // computation; the footer's schedule line shows it either way.
              + ":"
              + entry.getScheduledDate().map(AbsoluteDate::toString).orElse(""));
    }
    return snapshot;
  }

  /**
   * Sets where the window appears when it is next shown. Passing {@code null} centres it.
   *
   * @param position a translation previously read from {@link #getPosition()}
   */
  public void setInitialPosition(Vector3f position) {
    this.initialPosition = position == null ? null : position.clone();
  }

  /**
   * Returns the window's current translation, so the app state can reopen it where the user left
   * it. The window itself keeps no memory across instances.
   *
   * @return a copy of the current local translation
   */
  public Vector3f getPosition() {
    return root.getLocalTranslation().clone();
  }

  /**
   * Places the window on its first frame, then leaves it alone — {@link WindowDragHandler} owns its
   * translation from that point on. A resize is the one event that moves it again, and it only
   * clamps: the window is pulled back inside the new bounds without cancelling where the user put
   * it.
   *
   * <p>Both first-placement branches are clamped. A remembered position was recorded against
   * whatever surface the window was last closed on, and resizing while the window is closed changes
   * the bounds without this method ever seeing the resize — {@code lastWidth} and {@code
   * lastHeight} are seeded on the first call, so the resize branch never fires for it. The centred
   * position needs it too: at the application's own 1280×720 it lands above the top bound, and the
   * first pixel of the first drag would then snap the window away from the cursor. Centring a
   * window taller than the bounds allow therefore yields a position that is horizontally centred
   * and vertically pinned to the top bound, which is what the user can actually reach.
   */
  private void place(int screenWidth, int screenHeight) {
    if (!placed) {
      placed = true;
      lastWidth = screenWidth;
      lastHeight = screenHeight;
      if (initialPosition != null) {
        // z comes from the layer, never from the remembered position: a position captured before
        // the window was ever placed would carry z = 0 and reopen behind the display panel.
        root.setLocalTranslation(initialPosition.x, initialPosition.y, UiLayers.WINDOW);
      } else {
        root.setLocalTranslation(
            Math.round((screenWidth - WINDOW_WIDTH) / 2f),
            Math.round((screenHeight + WINDOW_HEIGHT) / 2f),
            UiLayers.WINDOW);
      }
      WindowDragHandler.clamp(root, screenWidth, screenHeight, PanelHeader.HEIGHT);
      return;
    }
    if (screenWidth != lastWidth || screenHeight != lastHeight) {
      lastWidth = screenWidth;
      lastHeight = screenHeight;
      WindowDragHandler.clamp(root, screenWidth, screenHeight, PanelHeader.HEIGHT);
    }
  }
}
