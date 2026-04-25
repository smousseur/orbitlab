package com.smousseur.orbitlab.ui.mission;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
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
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BorderLayout;
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
 * Modal mission panel: lists all missions in {@link MissionContext}, exposes per-row icon actions
 * (Edit / Compute / Delete) and a footer summary of the selected mission. Shares the wizard shell /
 * header / footer textures through {@link FormStyles} and is attached to the shared modal layer
 * through a {@link ModalBackdrop}.
 */
public class MissionPanelWidget implements AutoCloseable {
  private static final Logger logger = LogManager.getLogger(MissionPanelWidget.class);

  private static final float WINDOW_WIDTH = 720f;
  private static final float WINDOW_HEIGHT = 520f;
  private static final float HEADER_HEIGHT = 88f;
  private static final float FOOTER_HEIGHT = 78f;
  private static final float HEADER_PAD_X = 32f;
  private static final float HEADER_PAD_Y = 20f;
  private static final float CONTENT_PAD_X = 32f;
  private static final float CONTENT_PAD_Y = 20f;
  private static final float HEADER_INNER_WIDTH = WINDOW_WIDTH - 2 * HEADER_PAD_X;
  private static final float CONTENT_INNER_WIDTH = WINDOW_WIDTH - 2 * CONTENT_PAD_X;

  private static final float COL_NAME = 220f;
  private static final float COL_TYPE = 90f;
  private static final float COL_STATUS = 130f;
  private static final float COL_ACTIONS = CONTENT_INNER_WIDTH - COL_NAME - COL_TYPE - COL_STATUS;
  private static final float ROW_HEIGHT = 46f;
  private static final float ACTION_ICON_SIZE = 20f;
  private static final float ACTION_ICON_GAP = 8f;
  private static final float COL_HEADER_ALPHA = 0.6f;
  private static final float CLOSE_ICON_SIZE = 14f;
  private static final float CLOSE_BTN_INSET = 12f;
  private static final ColorRGBA ROW_IDLE_TINT = new ColorRGBA(1f, 1f, 1f, 0f);
  private static final ColorRGBA ROW_HOVER_TINT = new ColorRGBA(1f, 1f, 1f, 0.18f);
  private static final ColorRGBA ROW_SELECT_TINT = new ColorRGBA(1f, 1f, 1f, 0.45f);

  /** Placeholder mission type displayed in the Type column until Mission exposes one. */
  private static final String DEFAULT_MISSION_TYPE = "LEO";

  // Dummy value shown in the selection details footer until real metadata is plumbed.
  private static final String DUMMY_ALTITUDE = "380 km";

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

    Container brandRow = header.addChild(new Container(new BorderLayout()));
    brandRow.setBackground(null);
    brandRow.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, 18f, 0));

    Container brandLeft = new Container(new BoxLayout(Axis.X, FillMode.None));
    brandLeft.setBackground(null);
    brandLeft.addChild(UiKit.wizardIcon("icon-brand-globe", 18, 18));
    brandLeft.addChild(UiKit.hSpacer(8));

    Label brandName = brandLeft.addChild(new Label("ORBITLAB", FormStyles.STYLE));
    brandName.setFont(UiKit.orbitron(13));
    brandName.setColor(FormStyles.ACCENT_BRIGHT);

    Label brandSep = brandLeft.addChild(new Label("  /  ", FormStyles.STYLE));
    brandSep.setFont(UiKit.ibmPlexMono(11));
    brandSep.setColor(FormStyles.TEXT_LO);

    Label brandSub = brandLeft.addChild(new Label("MISSIONS", FormStyles.STYLE));
    brandSub.setFont(UiKit.ibmPlexMono(11));
    brandSub.setColor(FormStyles.TEXT_LO);

    brandRow.addChild(brandLeft, BorderLayout.Position.West);
    brandRow.addChild(buildCloseButton(), BorderLayout.Position.East);

    header.addChild(UiKit.vSpacer(10));

    Container titleRow = header.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    titleRow.setBackground(null);
    titleRow.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, 32f, 0));

    Label title = titleRow.addChild(new Label("MISSION ROSTER", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(16));
    title.setColor(FormStyles.TEXT_PRIMARY);
    title.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, 26f, 0));

    return header;
  }

  private Button buildNewMissionButton() {
    Button btn = new Button("+ New mission", FormStyles.STYLE);
    btn.setFont(UiKit.sora(13));
    btn.setColor(FormStyles.TEXT_PRIMARY);
    TbtQuadBackgroundComponent bg = UiKit.wizardBg9("btn-primary", 8);
    bg.setMargin(0f, 0f);
    btn.setBackground(bg);
    TbtQuadBackgroundComponent hoverBg = UiKit.wizardBg9("btn-primary-hover", 8);
    hoverBg.setMargin(0f, 0f);
    btn.setInsetsComponent(new InsetsComponent(new Insets3f(6, 16, 6, 16)));
    btn.setPreferredSize(new Vector3f(160f, 32f, 0));
    btn.addClickCommands(src -> onNewMission.run());
    MouseEventControl.addListenersToSpatial(
        btn,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            TbtQuadBackgroundComponent h = UiKit.wizardBg9("btn-primary-hover", 8);
            h.setMargin(0f, 0f);
            btn.setBackground(h);
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            TbtQuadBackgroundComponent n = UiKit.wizardBg9("btn-primary", 8);
            n.setMargin(0f, 0f);
            btn.setBackground(n);
          }
        });
    return btn;
  }

  private Container buildCloseButton() {
    Container icon = new Container();
    icon.setBackground(UiKit.wizardFlat("icon-close-lo"));
    icon.setPreferredSize(new Vector3f(CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, 0));
    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            icon.setBackground(UiKit.wizardFlat("icon-close-red"));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            icon.setBackground(UiKit.wizardFlat("icon-close-lo"));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onClose.run();
            event.setConsumed();
          }
        });
    return icon;
  }

  private Container buildContent() {
    Container content = new Container(new BoxLayout(Axis.Y, FillMode.None));
    content.setBackground(null);
    content.setPreferredSize(
        new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT, 0));
    content.setInsetsComponent(
        new InsetsComponent(
            new Insets3f(CONTENT_PAD_Y, CONTENT_PAD_X, CONTENT_PAD_Y, CONTENT_PAD_X)));

    Container actionsBar = content.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    actionsBar.setBackground(null);
    actionsBar.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 32f, 0));
    actionsBar.addChild(UiKit.hSpacer(CONTENT_INNER_WIDTH - 160f));
    actionsBar.addChild(buildNewMissionButton());
    content.addChild(UiKit.vSpacer(12));

    Container columnHeader = content.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    columnHeader.setBackground(null);
    columnHeader.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 14f, 0));
    columnHeader.setInsetsComponent(new InsetsComponent(new Insets3f(0, 8, 0, 8)));

    columnHeader.addChild(columnHeaderLabel("NAME", COL_NAME));
    columnHeader.addChild(columnHeaderLabel("TYPE", COL_TYPE));
    columnHeader.addChild(columnHeaderLabel("STATUS", COL_STATUS));
    columnHeader.addChild(columnHeaderLabel("ACTIONS", COL_ACTIONS));

    content.addChild(UiKit.vSpacer(6));
    content.addChild(divider(CONTENT_INNER_WIDTH));
    content.addChild(UiKit.vSpacer(6));

    listContainer = content.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    listContainer.setBackground(null);
    listContainer.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 0, 0));

    return content;
  }

  private Container buildFooter() {
    Container footer = new Container(new BoxLayout(Axis.Y, FillMode.None));
    footer.setBackground(FormStyles.footerBg());
    footer.setPreferredSize(new Vector3f(WINDOW_WIDTH, FOOTER_HEIGHT, 0));
    footer.setInsetsComponent(
        new InsetsComponent(new Insets3f(16, HEADER_PAD_X, 16, HEADER_PAD_X)));

    footerSummary = footer.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    footerSummary.setBackground(null);
    footerSummary.setPreferredSize(new Vector3f(HEADER_INNER_WIDTH, FOOTER_HEIGHT - 32, 0));

    return footer;
  }

  private Label columnHeaderLabel(String text, float width) {
    Label l = new Label(text, FormStyles.STYLE);
    l.setFont(UiKit.mono(10));
    ColorRGBA c = FormStyles.TEXT_LO.clone();
    c.a = COL_HEADER_ALPHA;
    l.setColor(c);
    l.setPreferredSize(new Vector3f(width, 12, 0));
    l.setTextHAlignment(HAlignment.Left);
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
      empty.setFont(UiKit.sora(13));
      empty.setColor(FormStyles.TEXT_SECONDARY);
      empty.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, 24, 0));
      return;
    }

    for (int i = 0; i < entries.size(); i++) {
      listContainer.addChild(buildRow(entries.get(i)));
      if (i < entries.size() - 1) {
        listContainer.addChild(divider(CONTENT_INNER_WIDTH));
      }
    }
  }

  private Container buildRow(MissionEntry entry) {
    String name = entry.mission().getName();
    MissionStatus status = entry.mission().getStatus();
    boolean selected = name.equals(selectedMissionName);

    Container row = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    row.setPreferredSize(new Vector3f(CONTENT_INNER_WIDTH, ROW_HEIGHT, 0));
    row.setInsetsComponent(new InsetsComponent(new Insets3f(6, 12, 6, 12)));
    TbtQuadBackgroundComponent rowBg = UiKit.wizardBg9("btn-primary", 8);
    rowBg.setMargin(0f, 0f);
    rowBg.setColor(selected ? ROW_SELECT_TINT : ROW_IDLE_TINT);
    row.setBackground(rowBg);

    float rowInnerH = ROW_HEIGHT; // - 12;

    Label nameLabel = row.addChild(new Label(name, FormStyles.STYLE));
    nameLabel.setFont(UiKit.sora(13));
    nameLabel.setColor(FormStyles.TEXT_PRIMARY);
    nameLabel.setTextHAlignment(HAlignment.Left);
    nameLabel.setTextVAlignment(VAlignment.Center);
    nameLabel.setPreferredSize(new Vector3f(COL_NAME, rowInnerH, 0));

    Label typeLabel = row.addChild(new Label(missionType(entry), FormStyles.STYLE));
    typeLabel.setFont(UiKit.ibmPlexMono(11));
    typeLabel.setColor(FormStyles.TEXT_SECONDARY);
    typeLabel.setTextHAlignment(HAlignment.Left);
    typeLabel.setTextVAlignment(VAlignment.Center);
    typeLabel.setPreferredSize(new Vector3f(COL_TYPE, rowInnerH, 0));

    Label statusLabel = row.addChild(new Label(status.name(), FormStyles.STYLE));
    statusLabel.setFont(UiKit.ibmPlexMono(11));
    statusLabel.setColor(statusColor(status));
    statusLabel.setTextHAlignment(HAlignment.Left);
    statusLabel.setTextVAlignment(VAlignment.Center);
    statusLabel.setPreferredSize(new Vector3f(COL_STATUS, rowInnerH, 0));

    Container actions = row.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    actions.setBackground(null);
    actions.setPreferredSize(new Vector3f(COL_ACTIONS, rowInnerH, 0));
    populateRowActions(actions, name, status, entry.isVisible(), rowInnerH);

    // Hover + selection follow the PopupList pattern (white tint over btn-primary).
    // Action icons consume their own clicks so clicks on icons don't trigger row selection.
    MouseEventControl.addListenersToSpatial(
        row,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            if (!name.equals(selectedMissionName)) {
              rowBg.setColor(ROW_HOVER_TINT);
            }
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            rowBg.setColor(name.equals(selectedMissionName) ? ROW_SELECT_TINT : ROW_IDLE_TINT);
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            selectMission(name);
            event.setConsumed();
          }
        });

    return row;
  }

  private void populateRowActions(
      Container actions,
      String missionName,
      MissionStatus status,
      boolean visible,
      float rowInnerH) {
    boolean computing = status == MissionStatus.COMPUTING;
    boolean ready = status == MissionStatus.READY;

    actions.addChild(
        vCenter(actionIconButton("edit", !computing, () -> onEdit(missionName)), rowInnerH));
    actions.addChild(UiKit.hSpacer(ACTION_ICON_GAP));
    actions.addChild(
        vCenter(
            actionIconButton(
                "compute",
                !computing,
                () -> eventBus.publishMissionAction(missionName, EventBus.MissionAction.OPTIMIZE)),
            rowInnerH));
    actions.addChild(UiKit.hSpacer(ACTION_ICON_GAP));
    actions.addChild(
        vCenter(
            visualizeIconButton(
                ready,
                visible,
                () ->
                    eventBus.publishMissionAction(
                        missionName, EventBus.MissionAction.TOGGLE_VISIBLE)),
            rowInnerH));
    actions.addChild(UiKit.hSpacer(ACTION_ICON_GAP));
    actions.addChild(
        vCenter(
            actionIconButton(
                "delete",
                !computing,
                () -> {
                  if (missionName.equals(selectedMissionName)) {
                    selectedMissionName = null;
                    missionContext.setSelectedMissionName(null);
                  }
                  eventBus.publishMissionAction(missionName, EventBus.MissionAction.DELETE);
                }),
            rowInnerH));
  }

  private static Container vCenter(Container child, float containerHeight) {
    float vPad = Math.max(0f, (containerHeight - child.getPreferredSize().y) * 0.5f);
    Container wrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    wrap.setBackground(null);
    wrap.setPreferredSize(new Vector3f(child.getPreferredSize().x, containerHeight, 0));
    wrap.addChild(UiKit.vSpacer(vPad));
    wrap.addChild(child);
    wrap.addChild(UiKit.vSpacer(vPad));
    return wrap;
  }

  private Container actionIconButton(String iconKey, boolean enabled, Runnable onClick) {
    final String normalTex = "icon-action-" + iconKey;
    final String hoverTex = "icon-action-" + iconKey + "-hover";
    final String disabledTex = "icon-action-" + iconKey + "-disabled";

    Container icon = new Container();
    icon.setPreferredSize(new Vector3f(ACTION_ICON_SIZE, ACTION_ICON_SIZE, 0));

    if (!enabled) {
      icon.setBackground(UiKit.wizardFlat(disabledTex));
      return icon;
    }

    icon.setBackground(tintedFlat(normalTex, FormStyles.ACCENT_BRIGHT));
    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.wizardFlat(hoverTex));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(tintedFlat(normalTex, FormStyles.ACCENT_BRIGHT));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onClick.run();
            event.setConsumed();
          }
        });
    return icon;
  }

  private Container visualizeIconButton(boolean ready, boolean on, Runnable onClick) {
    final String normalTex = "icon_visualize_normal";
    final String hoverTex = "icon_visualize_hover";
    final String disabledTex = "icon_visualize_disabled";

    Container icon = new Container();
    icon.setPreferredSize(new Vector3f(ACTION_ICON_SIZE, ACTION_ICON_SIZE, 0));

    if (!ready) {
      icon.setBackground(UiKit.wizardFlat(disabledTex));
      return icon;
    }

    final String idleTex = on ? normalTex : disabledTex;
    icon.setBackground(UiKit.wizardFlat(idleTex));

    MouseEventControl.addListenersToSpatial(
        icon,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.wizardFlat(hoverTex));
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial target, Spatial capture) {
            icon.setBackground(UiKit.wizardFlat(idleTex));
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onClick.run();
            event.setConsumed();
          }
        });
    return icon;
  }

  private static QuadBackgroundComponent tintedFlat(String tex, ColorRGBA tint) {
    QuadBackgroundComponent q = UiKit.wizardFlat(tex);
    q.setColor(tint);
    return q;
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
          footerSummary.addChild(new Label("Select a mission to see details", FormStyles.STYLE));
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
        row1.addChild(
            new Label("[ " + entry.mission().getStatus().name() + " ]", FormStyles.STYLE));
    status.setFont(UiKit.ibmPlexMono(11));
    status.setColor(statusColor(entry.mission().getStatus()));

    footerSummary.addChild(UiKit.vSpacer(6));

    String vehicleName =
        entry.mission().getVehicle() != null
            ? entry.mission().getVehicle().getClass().getSimpleName()
            : "—";
    String schedule = entry.getScheduledDate().map(Object::toString).orElse("unscheduled");

    addDetailLine(
        "type: "
            + missionType(entry)
            + "   •   vehicle: "
            + vehicleName
            + "   •   alt: "
            + DUMMY_ALTITUDE
            + "   •   launch: "
            + schedule);
  }

  private void addDetailLine(String text) {
    Label line = footerSummary.addChild(new Label(text, FormStyles.STYLE));
    line.setFont(UiKit.ibmPlexMono(11));
    line.setColor(FormStyles.TEXT_SECONDARY);
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

  private static String missionType(MissionEntry entry) {
    // TODO: pull the actual mission type from the wizard once stored on Mission.
    return DEFAULT_MISSION_TYPE;
  }

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
