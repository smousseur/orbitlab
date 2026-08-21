package com.smousseur.orbitlab.ui.mission.scenario;

import com.jme3.font.BitmapFont;
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
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.engine.events.ScenarioBrowserMode;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.UiLayers;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.form.ModalBackdrop;
import com.smousseur.orbitlab.ui.form.WindowDragHandler;
import com.smousseur.orbitlab.ui.mission.component.PaginationBar;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The scenario browser: one modal window in two modes (spec {@code
 * docs/scenario/01-persistance-missions.md} §6.2).
 *
 * <p>Placement and skinning only, and it does not even hold the model: which row is selected,
 * whether the confirm button is live and whether confirming would overwrite something are decided
 * by {@code ScenarioBrowserModel} and <b>pushed in</b> by the app state, the way {@code AppMenu} is
 * driven by {@code AppMenuModel}. That is what keeps the rules in a class a test can reach without
 * a render loop — and what keeps the {@code ui} package from depending on {@code states}.
 *
 * <p>It is modal, unlike the mission management window: replacing the session is not something to
 * do with half an eye on a list that keeps moving underneath. The backdrop shields the scene and
 * the window still drags by its header, so it can be moved off whatever the user wants to look at
 * before confirming.
 */
public class ScenarioBrowserWidget implements AutoCloseable {

  private static final float WIDTH = 620f;
  private static final float HEIGHT = 500f;
  private static final float PAD_X = 28f;
  private static final float FOOTER_PAD_Y = 17f;
  private static final float HEADER_HEIGHT = 72f;
  private static final float FOOTER_HEIGHT = 76f;
  private static final float ROW_HEIGHT = 34f;
  private static final float FIELD_HEIGHT = 38f;
  private static final float BUTTON_WIDTH = 150f;
  private static final float BUTTON_HEIGHT = 42f;
  private static final float BUTTON_GAP = 12f;
  private static final float PAGINATION_WIDTH = 150f;
  private static final float CLOSE_ICON_SIZE = 14f;

  private static final float GAP = 6f;
  private static final float COLUMN_HEADER_HEIGHT = 14f;
  private static final float DIVIDER_HEIGHT = 1f;

  /** Height of the icon-and-label row above the name field, measured a little generously. */
  private static final float FIELD_LABEL_HEIGHT = 18f;

  /** Air between the list and the name-field block below it. */
  private static final float FIELD_BLOCK_GAP = 14f;

  private static final float BODY_PAD_TOP = 16f;
  private static final float BODY_PAD_BOTTOM = 12f;

  private static final float COL_SAVED = 170f;
  private static final float COL_MISSIONS = 90f;

  private static final ColorRGBA ROW_IDLE_TINT = new ColorRGBA(1f, 1f, 1f, 0f);
  private static final ColorRGBA ROW_HOVER_TINT = new ColorRGBA(1f, 1f, 1f, 0.18f);
  private static final ColorRGBA ROW_SELECT_TINT = new ColorRGBA(1f, 1f, 1f, 0.45f);

  private final ScenarioBrowserMode mode;
  private final ModalBackdrop backdrop;
  private final Container root;
  private final Container listContainer;
  private final Container paginationSlot;
  private final PaginationBar pagination;
  private final Button confirmButton;
  private final float innerWidth;
  private final int pageSize;

  /** Null in {@link ScenarioBrowserMode#OPEN}, where there is no name to type. */
  private final TextField nameField;

  private Runnable onCancel = () -> {};
  private Runnable onConfirm = () -> {};
  private Consumer<String> onRowClicked = name -> {};
  private Consumer<String> onNameChanged = name -> {};

  private List<Row> rows = List.of();
  private String selectedName;
  private boolean confirmEnabled;

  /** What the field held when it was last read, so a change is a change the user made. */
  private String lastReadName = "";

  private int pageIndex = 0;
  private boolean visible = false;
  private boolean placed = false;
  private int lastWidth;
  private int lastHeight;

  /**
   * One line of the list. The window draws it and reports clicks by name; what a name means is the
   * app state's business.
   *
   * @param name the scenario name, without extension
   * @param savedAt when it was written, or {@code null} when the file says nothing
   * @param missionCount how many missions it holds
   */
  public record Row(String name, String savedAt, int missionCount) {
    public Row {
      Objects.requireNonNull(name, "name");
    }
  }

  /**
   * @param mode which mode the window opens in; it never changes afterwards
   */
  public ScenarioBrowserWidget(ScenarioBrowserMode mode) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.innerWidth = WIDTH - 2 * PAD_X;
    this.pageSize = pageSizeFor(mode);

    backdrop = new ModalBackdrop();

    root = new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(WIDTH, HEIGHT, 0));
    root.setBackground(FormStyles.shellBg());
    root.getInsetsComponent().setInsets(new Insets3f(0, 0, 0, 0));
    root.setBorder(null);
    FormStyles.clearMargin(root.getBackground());

    Container header = buildHeader();
    root.addChild(header);
    // Same grab rule as the management window: the band moves the window, the close icon keeps its
    // own listener because button events go to the picked spatial first.
    CursorEventControl.addListenersToSpatial(header, new WindowDragHandler(root, HEADER_HEIGHT));

    Container body = root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    body.setBackground(null);
    body.setPreferredSize(new Vector3f(WIDTH, HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT, 0));
    body.setInsetsComponent(
        new InsetsComponent(new Insets3f(BODY_PAD_TOP, PAD_X, BODY_PAD_BOTTOM, PAD_X)));

    body.addChild(columnHeader());
    body.addChild(UiKit.vSpacer(GAP));
    body.addChild(divider());
    body.addChild(UiKit.vSpacer(GAP));

    listContainer = body.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    listContainer.setBackground(null);
    // Reserved, not measured from its children. Without this the block below rides up whenever the
    // list is short — an empty folder put the name field directly under the column header, halfway
    // up an otherwise empty window.
    listContainer.setPreferredSize(new Vector3f(innerWidth, pageSize * ROW_HEIGHT, 0));

    if (mode == ScenarioBrowserMode.SAVE) {
      body.addChild(UiKit.vSpacer(FIELD_BLOCK_GAP));
      body.addChild(UiKit.fieldLabelRow("SCENARIO NAME", "lbl-edit"));
      body.addChild(UiKit.vSpacer(GAP));
      nameField = UiKit.newInputField("", innerWidth, FIELD_HEIGHT);
      body.addChild(nameField);
    } else {
      nameField = null;
    }

    Container footer = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    footer.setBackground(FormStyles.footerBg());
    footer.setPreferredSize(new Vector3f(WIDTH, FOOTER_HEIGHT, 0));
    footer.setInsetsComponent(
        new InsetsComponent(new Insets3f(FOOTER_PAD_Y, PAD_X, FOOTER_PAD_Y, PAD_X)));

    // The pager lives in the footer rather than above the list: a band reserved at the top for a
    // control that only appears past one page is an empty stripe the rest of the time, and this
    // window has no second control to put beside it.
    paginationSlot = new Container(new BoxLayout(Axis.X, FillMode.None));
    paginationSlot.setBackground(null);
    paginationSlot.setPreferredSize(new Vector3f(PAGINATION_WIDTH, BUTTON_HEIGHT, 0));
    footer.addChild(paginationSlot);

    pagination = new PaginationBar(PAGINATION_WIDTH, BUTTON_HEIGHT);
    pagination.setOnPrev(
        () -> {
          if (pageIndex > 0) {
            pageIndex--;
            rebuildList();
          }
        });
    pagination.setOnNext(
        () -> {
          pageIndex++;
          rebuildList();
        });

    float clusterWidth = 2 * BUTTON_WIDTH + BUTTON_GAP;
    footer.addChild(UiKit.hSpacer(Math.max(0f, innerWidth - PAGINATION_WIDTH - clusterWidth)));
    Button cancelButton =
        footer.addChild(newButton("Cancel", FormStyles.TEXT_SECONDARY, "btn-ghost"));
    cancelButton.addClickCommands(src -> onCancel.run());
    footer.addChild(UiKit.hSpacer(BUTTON_GAP));
    confirmButton =
        footer.addChild(
            newButton(
                mode == ScenarioBrowserMode.OPEN ? "Open" : "Save",
                FormStyles.TEXT_PRIMARY,
                "btn-primary"));
    confirmButton.addClickCommands(
        src -> {
          if (confirmEnabled) {
            onConfirm.run();
          }
        });

    // Modal or not, the window's own bounds must swallow clicks: the backdrop below consumes
    // everything else, and a click landing between the two would reach the 3D scene.
    MouseEventControl.addListenersToSpatial(
        root,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            event.setConsumed();
          }
        });

    rebuildList();
    syncConfirmButton();
  }

  /**
   * How many rows fit, derived from what is left rather than chosen.
   *
   * <p>{@code BoxLayout} in {@code FillMode.None} neither shrinks nor clips its children: a list
   * one row too tall does not scroll, it draws over the footer. Computing the count from the space
   * that remains — instead of writing two numbers down and keeping them in step with every change
   * of padding — is what makes that impossible to get wrong later. Save mode gives up the room the
   * name field and its label take, which is worth about two rows.
   */
  private static int pageSizeFor(ScenarioBrowserMode mode) {
    float bodyInner = HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - BODY_PAD_TOP - BODY_PAD_BOTTOM;
    float aboveList = COLUMN_HEADER_HEIGHT + GAP + DIVIDER_HEIGHT + GAP;
    float belowList =
        mode == ScenarioBrowserMode.SAVE
            ? FIELD_BLOCK_GAP + FIELD_LABEL_HEIGHT + GAP + FIELD_HEIGHT
            : 0f;
    return (int) ((bodyInner - aboveList - belowList) / ROW_HEIGHT);
  }

  /**
   * @param modalNode the node modals live in — {@code guiGraph().getModalNode()}
   */
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

  /**
   * Advances the window: places it on the first frame, and reports what the user typed.
   *
   * <p>The field is polled rather than listened to. Lemur delivers no change event a caller can
   * hang a predicate on, and the confirm button has to follow the name letter by letter — a poll of
   * one string per frame is the honest price of that.
   */
  public void update(float tpf, Camera cam) {
    if (!visible) return;
    backdrop.update(cam);
    place(cam.getWidth(), cam.getHeight());

    if (nameField != null) {
      String typed = nameField.getText() == null ? "" : nameField.getText();
      if (!typed.equals(lastReadName)) {
        lastReadName = typed;
        onNameChanged.accept(typed);
      }
    }
  }

  /**
   * Replaces the list and the highlighted row.
   *
   * @param rows the scenarios to draw, in listing order
   * @param selectedName the highlighted row, or {@code null} when none is
   */
  public void setRows(List<Row> rows, String selectedName) {
    this.rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    this.selectedName = selectedName;
    rebuildList();
  }

  /**
   * Greys the confirm button out, or lights it up.
   *
   * @param enabled whether confirming would do anything
   */
  public void setConfirmEnabled(boolean enabled) {
    this.confirmEnabled = enabled;
    syncConfirmButton();
  }

  /**
   * Writes the name field, which is how clicking a row fills it in save mode. A no-op in open mode,
   * where there is no field.
   *
   * @param name the text to show
   */
  public void setName(String name) {
    if (nameField == null) return;
    String value = name == null ? "" : name;
    lastReadName = value;
    nameField.setText(value);
  }

  /** Called with the name of a clicked row. */
  public void setOnRowClicked(Consumer<String> action) {
    this.onRowClicked = action != null ? action : name -> {};
  }

  /** Called with the field's content whenever the user changes it. */
  public void setOnNameChanged(Consumer<String> action) {
    this.onNameChanged = action != null ? action : name -> {};
  }

  /** Action run on Cancel, on the close icon and on ESC. The caller closes the window. */
  public void setOnCancel(Runnable action) {
    this.onCancel = action != null ? action : () -> {};
  }

  /**
   * Action run on the confirm button, and only while it is enabled. What the confirmation acts on
   * is the caller's business — the window never decided it.
   */
  public void setOnConfirm(Runnable action) {
    this.onConfirm = action != null ? action : () -> {};
  }

  private void place(int screenWidth, int screenHeight) {
    if (placed && screenWidth == lastWidth && screenHeight == lastHeight) {
      return;
    }
    lastWidth = screenWidth;
    lastHeight = screenHeight;
    placed = true;
    root.setLocalTranslation(
        Math.round((screenWidth - WIDTH) / 2f),
        Math.round((screenHeight + HEIGHT) / 2f),
        UiLayers.MODAL);
  }

  private void rebuildList() {
    listContainer.clearChildren();

    int total = rows.size();
    int pageCount = Math.max(1, (total + pageSize - 1) / pageSize);
    pageIndex = Math.min(Math.max(0, pageIndex), pageCount - 1);

    boolean attached = pagination.getNode().getParent() != null;
    if (pageCount > 1) {
      if (!attached) paginationSlot.addChild(pagination.getNode());
      pagination.refresh(pageIndex, pageCount);
    } else if (attached) {
      paginationSlot.clearChildren();
    }

    if (total == 0) {
      Label empty = listContainer.addChild(new Label("No saved scenario", FormStyles.STYLE));
      empty.setFont(UiKit.sora(13));
      empty.setColor(FormStyles.TEXT_LO);
      empty.setTextHAlignment(HAlignment.Center);
      empty.setTextVAlignment(VAlignment.Center);
      // Sized to the whole reserved area rather than to one line: the message then sits in the
      // middle of the empty list instead of hanging off the divider above it.
      empty.setPreferredSize(new Vector3f(innerWidth, pageSize * ROW_HEIGHT, 0));
      return;
    }

    int from = pageIndex * pageSize;
    int to = Math.min(from + pageSize, total);
    for (int i = from; i < to; i++) {
      listContainer.addChild(buildRow(rows.get(i)));
    }
  }

  private Container buildRow(Row entry) {
    boolean selected = entry.name().equals(selectedName);

    Container row = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    row.setPreferredSize(new Vector3f(innerWidth, ROW_HEIGHT, 0));
    row.setInsetsComponent(new InsetsComponent(new Insets3f(4, 12, 4, 12)));

    TbtQuadBackgroundComponent rowBg = UiKit.textureBg("row-hover", 8);
    rowBg.setMargin(0f, 0f);
    rowBg.setColor(selected ? ROW_SELECT_TINT : ROW_IDLE_TINT);
    row.setBackground(rowBg);

    float nameWidth = innerWidth - COL_SAVED - COL_MISSIONS - 24f;
    row.addChild(cell(entry.name(), nameWidth, UiKit.sora(13), FormStyles.TEXT_PRIMARY));
    row.addChild(
        cell(
            entry.savedAt() == null ? "—" : entry.savedAt(),
            COL_SAVED,
            UiKit.ibmPlexMono(11),
            FormStyles.TEXT_SECONDARY));
    row.addChild(
        cell(
            String.valueOf(entry.missionCount()),
            COL_MISSIONS,
            UiKit.ibmPlexMono(11),
            FormStyles.TEXT_SECONDARY));

    MouseEventControl.addListenersToSpatial(
        row,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            if (!selected) {
              rowBg.setColor(ROW_HOVER_TINT);
            }
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            rowBg.setColor(selected ? ROW_SELECT_TINT : ROW_IDLE_TINT);
          }

          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onRowClicked.accept(entry.name());
            event.setConsumed();
          }
        });
    return row;
  }

  private void syncConfirmButton() {
    confirmButton.setColor(confirmEnabled ? FormStyles.TEXT_PRIMARY : FormStyles.TEXT_LO);
  }

  private Container buildHeader() {
    Container header = new Container(new BoxLayout(Axis.Y, FillMode.None));
    header.setBackground(FormStyles.headerBg());
    header.setPreferredSize(new Vector3f(WIDTH, HEADER_HEIGHT, 0));
    header.setInsetsComponent(new InsetsComponent(new Insets3f(16, PAD_X, 16, PAD_X)));

    Container brandRow = header.addChild(new Container(new BorderLayout()));
    brandRow.setBackground(null);
    brandRow.setPreferredSize(new Vector3f(innerWidth, 16f, 0));

    Container brandLeft = new Container(new BoxLayout(Axis.X, FillMode.None));
    brandLeft.setBackground(null);
    brandLeft.addChild(UiKit.wizardIcon("icon-brand-globe", 16, 16));
    brandLeft.addChild(UiKit.hSpacer(8));
    Label brand = brandLeft.addChild(new Label("ORBITLAB", FormStyles.STYLE));
    brand.setFont(UiKit.orbitron(13));
    brand.setColor(FormStyles.ACCENT_BRIGHT);
    Label separator = brandLeft.addChild(new Label("  /  ", FormStyles.STYLE));
    separator.setFont(UiKit.ibmPlexMono(11));
    separator.setColor(FormStyles.TEXT_LO);
    Label section = brandLeft.addChild(new Label("SCENARIOS", FormStyles.STYLE));
    section.setFont(UiKit.ibmPlexMono(11));
    section.setColor(FormStyles.TEXT_LO);

    brandRow.addChild(brandLeft, BorderLayout.Position.West);
    brandRow.addChild(buildCloseButton(), BorderLayout.Position.East);

    header.addChild(UiKit.vSpacer(8));

    Label title =
        header.addChild(
            new Label(
                mode == ScenarioBrowserMode.OPEN ? "OPEN SCENARIO" : "SAVE SCENARIO",
                FormStyles.STYLE));
    title.setFont(UiKit.orbitron(16));
    title.setColor(FormStyles.TEXT_PRIMARY);
    title.setPreferredSize(new Vector3f(innerWidth, 22f, 0));
    return header;
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
            onCancel.run();
            event.setConsumed();
          }
        });
    return icon;
  }

  private Container columnHeader() {
    Container header = new Container(new BoxLayout(Axis.X, FillMode.None));
    header.setBackground(null);
    header.setPreferredSize(new Vector3f(innerWidth, 14f, 0));
    header.setInsetsComponent(new InsetsComponent(new Insets3f(0, 12, 0, 12)));
    float nameWidth = innerWidth - COL_SAVED - COL_MISSIONS - 24f;
    header.addChild(columnLabel("NAME", nameWidth));
    header.addChild(columnLabel("SAVED", COL_SAVED));
    header.addChild(columnLabel("MISSIONS", COL_MISSIONS));
    return header;
  }

  private static Label columnLabel(String text, float width) {
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(UiKit.mono(10));
    ColorRGBA color = FormStyles.TEXT_LO.clone();
    color.a = 0.6f;
    label.setColor(color);
    label.setPreferredSize(new Vector3f(width, 12f, 0));
    label.setTextHAlignment(HAlignment.Left);
    return label;
  }

  private static Label cell(String text, float width, BitmapFont font, ColorRGBA c) {
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(font);
    label.setColor(c);
    label.setTextHAlignment(HAlignment.Left);
    label.setTextVAlignment(VAlignment.Center);
    label.setPreferredSize(new Vector3f(width, ROW_HEIGHT, 0));
    return label;
  }

  private Container divider() {
    Container line = new Container();
    line.setPreferredSize(new Vector3f(innerWidth, 1f, 0));
    line.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    return line;
  }

  private static Button newButton(String text, ColorRGBA color, String skin) {
    Button button = new Button(text, FormStyles.STYLE);
    button.setFont(UiKit.sora(13));
    button.setColor(color);
    button.setTextHAlignment(HAlignment.Center);
    button.setTextVAlignment(VAlignment.Center);
    button.setPreferredSize(new Vector3f(BUTTON_WIDTH, BUTTON_HEIGHT, 0));
    TbtQuadBackgroundComponent bg = UiKit.wizardBg9(skin, 8);
    bg.setMargin(0f, 0f);
    button.setBackground(bg);
    MouseEventControl.addListenersToSpatial(
        button,
        new DefaultMouseListener() {
          @Override
          public void mouseEntered(MouseMotionEvent evt, Spatial t, Spatial c) {
            TbtQuadBackgroundComponent hover = UiKit.wizardBg9(skin + "-hover", 8);
            hover.setMargin(0f, 0f);
            button.setBackground(hover);
          }

          @Override
          public void mouseExited(MouseMotionEvent evt, Spatial t, Spatial c) {
            TbtQuadBackgroundComponent idle = UiKit.wizardBg9(skin, 8);
            idle.setMargin(0f, 0f);
            button.setBackground(idle);
          }
        });
    return button;
  }
}
