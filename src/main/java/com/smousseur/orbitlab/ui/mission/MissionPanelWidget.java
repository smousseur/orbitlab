package com.smousseur.orbitlab.ui.mission;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.core.GuiComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.form.ModalBackdrop;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Modal mission panel: lists all missions in {@link MissionContext}, exposes per-row actions
 * (Edit / Compute / Delete / Show-Hide) and a footer summary of the selected mission.
 *
 * <p>Reuses the wizard's shell / header / footer textures through {@link FormStyles} and is
 * attached to the shared modal layer through a {@link ModalBackdrop}.
 */
public class MissionPanelWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionPanelWidget.class);

  private static final float WINDOW_WIDTH = 720f;
  private static final float WINDOW_HEIGHT = 520f;
  private static final float HEADER_HEIGHT = 88f;
  private static final float FOOTER_HEIGHT = 72f;
  private static final float HEADER_PAD_X = 32f;
  private static final float HEADER_PAD_Y = 20f;
  private static final float CONTENT_PAD_X = 32f;
  private static final float CONTENT_PAD_Y = 20f;
  private static final float HEADER_INNER_WIDTH = WINDOW_WIDTH - 2 * HEADER_PAD_X;
  private static final float CONTENT_INNER_WIDTH = WINDOW_WIDTH - 2 * CONTENT_PAD_X;

  private static final float COL_NAME = 300f;
  private static final float COL_STATUS = 120f;
  private static final float COL_ACTIONS = CONTENT_INNER_WIDTH - COL_NAME - COL_STATUS;
  private static final float ROW_HEIGHT = 30f;

  private final MissionContext missionContext;
  private final EventBus eventBus;

  private final ModalBackdrop backdrop;
  private final Container root;
  private Container listContainer;
  private Container footerSummary;

  private String selectedMissionName;
  private List<String> lastSnapshot = List.of();
  private boolean visible = false;

  private Runnable onClose = () -> {};
  private Runnable onNewMission = () -> {};

  public MissionPanelWidget(ApplicationContext context) {
    Objects.requireNonNull(context, "context");
    this.missionContext = context.missionContext();
    this.eventBus = context.eventBus();
    this.selectedMissionName = missionContext.getSelectedMissionName();

    backdrop = new ModalBackdrop();
    backdrop.setOnClick(() -> onClose.run());

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.getInsetsComponent().setInsets(new Insets3f(0, 0, 0, 0));
    root.setBorder(null);
    clearMargin(root.getBackground());

    root.addChild(buildHeader());
    root.addChild(buildContent());
    root.addChild(buildFooter());

    // Mouse on the modal shell must not leak through to the backdrop.
    MouseEventControl.addListenersToSpatial(
        root,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }
        });

    refreshMissionList();
    refreshFooterSummary();
  }

  // -------------------------------------------------------------------------
  // Public lifecycle
  // -------------------------------------------------------------------------

  public void attachTo(Node modalNode) {
    modalNode.attachChild(backdrop.getNode());
    modalNode.attachChild(root);
    visible = true;
  }

  @Override
  public void close() {
    backdrop.getNode().removeFromParent();
    root.removeFromParent();
    visible = false;
  }

  public boolean isVisible() {
    return visible;
  }

  public void update(float tpf, Camera cam) {
    if (!visible) return;
    backdrop.update(cam);
    centerOnScreen(cam.getWidth(), cam.getHeight());

    List<String> snapshot = buildSnapshot();
    if (selectedMissionName != null && missionContext.findMission(selectedMissionName).isEmpty()) {
      selectedMissionName = null;
      missionContext.setSelectedMissionName(null);
    }
    if (!snapshot.equals(lastSnapshot)) {
      lastSnapshot = snapshot;
      refreshMissionList();
      refreshFooterSummary();
    }
  }

  public void setOnClose(Runnable action) {
    this.onClose = action != null ? action : () -> {};
  }

  public void setOnNewMission(Runnable action) {
    this.onNewMission = action != null ? action : () -> {};
  }

  // -------------------------------------------------------------------------
  // Layout builders
  // -------------------------------------------------------------------------

  private Container buildHeader() {
    Container header = new Container(new BoxLayout(Axis.Y, FillMode.None));
    header.setBackground(FormStyles.headerBg());
    header.setPreferredSize(new Vector3f(WINDOW_WIDTH, HEADER_HEIGHT, 0));
    header.setInsetsComponent(
        new InsetsComponent(new Insets3f(HEADER_PAD_Y, HEADER_PAD_X, HEADER_PAD_Y, HEADER_PAD_X)));

    Container brandRow = header.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    brandRow.setBackground(null);
    brandRow.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, 18f, 0));

    brandRow.addChild(UiKit.wizardIcon("icon-brand-globe", 18, 18));
    brandRow.addChild(UiKit.hSpacer(8));

    Label brandName = brandRow.addChild(new Label("ORBITLAB", FormStyles.STYLE));
    brandName.setFont(UiKit.orbitron(13));
    brandName.setColor(FormStyles.ACCENT_BRIGHT);

    Label brandSep = brandRow.addChild(new Label("  /  ", FormStyles.STYLE));
    brandSep.setFont(UiKit.ibmPlexMono(11));
    brandSep.setColor(FormStyles.TEXT_LO);

    Label brandSub = brandRow.addChild(new Label("MISSIONS", FormStyles.STYLE));
    brandSub.setFont(UiKit.ibmPlexMono(11));
    brandSub.setColor(FormStyles.TEXT_LO);

    header.addChild(UiKit.vSpacer(10));

    Container titleRow = header.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    titleRow.setBackground(null);
    titleRow.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, 26f, 0));

    Label title = titleRow.addChild(new Label("MISSION ROSTER", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(16));
    title.setColor(FormStyles.TEXT_PRIMARY);

    float reservedRightWidth = 160f + 12f + 28f;
    float titleColWidth = HEADER_INNER_WIDTH - reservedRightWidth;
    title.setPreferredSize(new Vector3f(titleColWidth, 22f, 0));

    Button newMissionButton = titleRow.addChild(new Button("+ New mission", FormStyles.STYLE));
    newMissionButton.setFont(UiKit.sora(12));
    newMissionButton.setColor(FormStyles.ACCENT_BRIGHT);
    newMissionButton.setInsetsComponent(new InsetsComponent(new Insets3f(6, 14, 6, 14)));
    newMissionButton.setPreferredSize(new Vector3f(160f, 26f, 0));
    newMissionButton.addClickCommands(src -> onNewMission.run());

    titleRow.addChild(UiKit.hSpacer(12));

    Button closeButton = titleRow.addChild(new Button("X", FormStyles.STYLE));
    closeButton.setFont(UiKit.sora(12));
    closeButton.setColor(FormStyles.TEXT_SECONDARY);
    closeButton.setInsetsComponent(new InsetsComponent(new Insets3f(6, 10, 6, 10)));
    closeButton.setPreferredSize(new Vector3f(28f, 26f, 0));
    closeButton.addClickCommands(src -> onClose.run());

    return header;
  }

  private Container buildContent() {
    Container content = new Container(new BoxLayout(Axis.Y, FillMode.None));
    content.setBackground(null);
    content.setPreferredSize(
        new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT, 0));
    content.setInsetsComponent(
        new InsetsComponent(new Insets3f(CONTENT_PAD_Y, CONTENT_PAD_X, CONTENT_PAD_Y, CONTENT_PAD_X)));

    Container columnHeader = content.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    columnHeader.setBackground(null);
    columnHeader.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 20f, 0));

    columnHeader.addChild(columnHeaderLabel("NAME", COL_NAME, HAlignment.Left));
    columnHeader.addChild(columnHeaderLabel("STATUS", COL_STATUS, HAlignment.Left));
    columnHeader.addChild(columnHeaderLabel("ACTIONS", COL_ACTIONS, HAlignment.Right));

    content.addChild(divider(CONTENT_INNER_WIDTH));
    content.addChild(UiKit.vSpacer(4));

    listContainer = content.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    listContainer.setBackground(null);
    listContainer.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 0, 0));

    return content;
  }

  private Container buildFooter() {
    Container footer = new Container(new BoxLayout(Axis.Y, FillMode.None));
    footer.setBackground(FormStyles.footerBg());
    footer.setPreferredSize(new Vector3f(WINDOW_WIDTH, FOOTER_HEIGHT, 0));
    footer.setInsetsComponent(new InsetsComponent(new Insets3f(16, HEADER_PAD_X, 16, HEADER_PAD_X)));

    footerSummary = footer.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    footerSummary.setBackground(null);
    footerSummary.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, FOOTER_HEIGHT - 32, 0));

    return footer;
  }

  private Label columnHeaderLabel(String text, float width, HAlignment align) {
    Label l = new Label(text, FormStyles.STYLE);
    l.setFont(UiKit.ibmPlexMono(10));
    l.setColor(FormStyles.TEXT_LO);
    l.setPreferredSize(new Vector3f(width, 16, 0));
    l.setTextHAlignment(align);
    return l;
  }

  private Container divider(float width) {
    Container d = new Container();
    d.setPreferredSize(new Vector3f(width, 1, 0));
    d.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    return d;
  }

  // -------------------------------------------------------------------------
  // Mission list rendering
  // -------------------------------------------------------------------------

  private List<String> buildSnapshot() {
    List<MissionEntry> entries = missionContext.getMissions();
    List<String> snapshot = new ArrayList<>(entries.size() + 1);
    snapshot.add("sel=" + (selectedMissionName == null ? "" : selectedMissionName));
    for (MissionEntry entry : entries) {
      snapshot.add(
          entry.mission().getName() + ":" + entry.mission().getStatus() + ":" + entry.isVisible());
    }
    return snapshot;
  }

  private void refreshMissionList() {
    listContainer.clearChildren();
    List<MissionEntry> entries = missionContext.getMissions();
    if (entries.isEmpty()) {
      Label empty = listContainer.addChild(new Label("No missions yet", FormStyles.STYLE));
      empty.setFont(UiKit.sora(12));
      empty.setColor(FormStyles.TEXT_SECONDARY);
      empty.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 24, 0));
      return;
    }

    for (MissionEntry entry : entries) {
      listContainer.addChild(buildRow(entry));
    }
  }

  private Container buildRow(MissionEntry entry) {
    String name = entry.mission().getName();
    MissionStatus status = entry.mission().getStatus();
    boolean selected = name.equals(selectedMissionName);

    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, ROW_HEIGHT, 0));
    row.setInsetsComponent(new InsetsComponent(new Insets3f(4, 8, 4, 8)));
    row.setBackground(
        selected
            ? UiKit.gradientBackground(new ColorRGBA(FormStyles.ACCENT_BRIGHT.r,
                FormStyles.ACCENT_BRIGHT.g, FormStyles.ACCENT_BRIGHT.b, 0.18f))
            : null);

    Label nameLabel = row.addChild(new Label(name, FormStyles.STYLE));
    nameLabel.setFont(UiKit.sora(12));
    nameLabel.setColor(FormStyles.TEXT_PRIMARY);
    nameLabel.setPreferredSize(new Vector3f(COL_NAME - 16, ROW_HEIGHT - 8, 0));

    Label statusLabel = row.addChild(new Label(status.name(), FormStyles.STYLE));
    statusLabel.setFont(UiKit.ibmPlexMono(11));
    statusLabel.setColor(statusColor(status));
    statusLabel.setPreferredSize(new Vector3f(COL_STATUS, ROW_HEIGHT - 8, 0));
    statusLabel.setTextHAlignment(HAlignment.Left);

    Container actions = row.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    actions.setBackground(null);
    actions.setPreferredSize(new Vector3f(COL_ACTIONS, ROW_HEIGHT - 8, 0));
    populateRowActions(actions, name, status, entry.isVisible());

    // Click on name or status selects the row.
    MouseEventControl.addListenersToSpatial(
        nameLabel,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            selectMission(name);
            event.setConsumed();
          }
        });
    MouseEventControl.addListenersToSpatial(
        statusLabel,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            selectMission(name);
            event.setConsumed();
          }
        });

    return row;
  }

  private void populateRowActions(
      Container actions, String missionName, MissionStatus status, boolean visibleInScene) {
    float btnH = ROW_HEIGHT - 10f;

    if (status == MissionStatus.COMPUTING) {
      Label computing = actions.addChild(new Label("computing...", FormStyles.STYLE));
      computing.setFont(UiKit.ibmPlexMono(10));
      computing.setColor(FormStyles.WARNING);
      computing.setTextHAlignment(HAlignment.Right);
      computing.setPreferredSize(new Vector3f(COL_ACTIONS, btnH, 0));
      return;
    }

    // Right-align: spacer first, then action buttons.
    actions.addChild(hFlex());

    boolean showToggle = status == MissionStatus.READY;
    if (showToggle) {
      actions.addChild(
          rowActionButton(
              visibleInScene ? "Hide" : "Show",
              FormStyles.SUCCESS,
              () ->
                  eventBus.publishMissionAction(
                      missionName, EventBus.MissionAction.TOGGLE_VISIBLE)));
      actions.addChild(UiKit.hSpacer(6));
    }

    actions.addChild(rowActionButton("Edit", FormStyles.ACCENT_BRIGHT, () -> onEdit(missionName)));
    actions.addChild(UiKit.hSpacer(6));
    actions.addChild(
        rowActionButton(
            status == MissionStatus.DRAFT ? "Compute" : "Re-compute",
            FormStyles.WARNING,
            () ->
                eventBus.publishMissionAction(missionName, EventBus.MissionAction.OPTIMIZE)));
    actions.addChild(UiKit.hSpacer(6));
    actions.addChild(
        rowActionButton(
            "Delete",
            FormStyles.DANGER,
            () -> {
              if (missionName.equals(selectedMissionName)) {
                selectedMissionName = null;
                missionContext.setSelectedMissionName(null);
              }
              eventBus.publishMissionAction(missionName, EventBus.MissionAction.DELETE);
            }));
  }

  private Container hFlex() {
    // Absorbs free width so action buttons float to the right.
    Container c = new Container();
    c.setBackground(null);
    c.setPreferredSize(new Vector3f(0, 0, 0));
    return c;
  }

  private Button rowActionButton(String label, ColorRGBA color, Runnable action) {
    Button b = new Button(label, FormStyles.STYLE);
    b.setFont(UiKit.sora(11));
    b.setColor(color);
    b.setInsetsComponent(new InsetsComponent(new Insets3f(3, 9, 3, 9)));
    b.addClickCommands(src -> action.run());
    return b;
  }

  // -------------------------------------------------------------------------
  // Footer summary
  // -------------------------------------------------------------------------

  private void refreshFooterSummary() {
    footerSummary.clearChildren();
    MissionEntry entry =
        selectedMissionName == null
            ? null
            : missionContext.findMission(selectedMissionName).orElse(null);

    if (entry == null) {
      Label hint =
          footerSummary.addChild(
              new Label("Select a mission to see details", FormStyles.STYLE));
      hint.setFont(UiKit.ibmPlexMono(11));
      hint.setColor(FormStyles.TEXT_LO);
      return;
    }

    Container row1 = footerSummary.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row1.setBackground(null);

    Label name = row1.addChild(new Label(entry.mission().getName(), FormStyles.STYLE));
    name.setFont(UiKit.orbitron(13));
    name.setColor(FormStyles.TEXT_PRIMARY);

    row1.addChild(UiKit.hSpacer(12));

    Label status =
        row1.addChild(new Label("[ " + entry.mission().getStatus().name() + " ]", FormStyles.STYLE));
    status.setFont(UiKit.ibmPlexMono(11));
    status.setColor(statusColor(entry.mission().getStatus()));

    footerSummary.addChild(UiKit.vSpacer(6));

    String vehicleName =
        entry.mission().getVehicle() != null
            ? entry.mission().getVehicle().getClass().getSimpleName()
            : "—";
    String schedule =
        entry.getScheduledDate().map(Object::toString).orElse("unscheduled");
    String line = "vehicle: " + vehicleName + "   •   launch: " + schedule;

    Label meta = footerSummary.addChild(new Label(line, FormStyles.STYLE));
    meta.setFont(UiKit.ibmPlexMono(11));
    meta.setColor(FormStyles.TEXT_SECONDARY);
  }

  // -------------------------------------------------------------------------
  // Selection / actions
  // -------------------------------------------------------------------------

  private void selectMission(String name) {
    if (name.equals(selectedMissionName)) {
      selectedMissionName = null;
    } else {
      selectedMissionName = name;
    }
    missionContext.setSelectedMissionName(selectedMissionName);
    refreshMissionList();
    refreshFooterSummary();
  }

  private void onEdit(String missionName) {
    logger.info("Edit not yet implemented for mission '{}'", missionName);
  }

  // -------------------------------------------------------------------------
  // Utilities
  // -------------------------------------------------------------------------

  private void centerOnScreen(int screenWidth, int screenHeight) {
    float x = Math.round((screenWidth - WINDOW_WIDTH) / 2f);
    float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
    root.setLocalTranslation(x, y, 101f);
  }

  private static ColorRGBA statusColor(MissionStatus status) {
    return switch (status) {
      case DRAFT -> FormStyles.TEXT_SECONDARY;
      case COMPUTING -> FormStyles.WARNING;
      case READY -> FormStyles.SUCCESS;
      case FAILED -> FormStyles.DANGER;
    };
  }

  private static void clearMargin(GuiComponent bg) {
    if (bg instanceof TbtQuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    } else if (bg instanceof QuadBackgroundComponent quad) {
      quad.setMargin(0f, 0f);
    }
  }
}
