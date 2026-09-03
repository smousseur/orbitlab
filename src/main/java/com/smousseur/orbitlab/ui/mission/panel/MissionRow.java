package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.MissionId;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgress;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.MissionProgressText;
import com.smousseur.orbitlab.ui.mission.MissionStatusColor;
import com.smousseur.orbitlab.ui.mission.component.SpinnerIcon;

class MissionRow {

  static final float HEIGHT = 46f;

  private static final float SWATCH_SIZE = 12f;
  private static final float BADGE_SIZE = 12f;
  private static final float ICON_GAP = 6f;

  private static final float SPINNER_LIFT_PX = 5f;

  private static final ColorRGBA ROW_IDLE_TINT = new ColorRGBA(1f, 1f, 1f, 0f);
  private static final ColorRGBA ROW_HOVER_TINT = new ColorRGBA(1f, 1f, 1f, 0.18f);
  private static final ColorRGBA ROW_SELECT_TINT = new ColorRGBA(1f, 1f, 1f, 0.45f);

  private static final float TEXT_REFRESH_SECONDS = 0.25f;

  private final Container root;
  private final TbtQuadBackgroundComponent rowBg;

  /** Set only on a row whose mission is computing; null on every other row. */
  private final MissionEntry computingEntry;

  private final SpinnerIcon spinner;
  private final Label statusLabel;
  private String statusText;
  private float sinceTextRefresh;

  MissionRow(
      MissionEntry entry,
      MissionListView.ColumnLayout cols,
      boolean selected,
      MissionListView.RowListener listener) {
    MissionId missionId = entry.id();
    String name = entry.mission().getName();
    MissionStatus status = entry.mission().getStatus();

    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(cols.totalWidth(), HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(6, 12, 6, 12)));

    rowBg = UiKit.textureBg("row-hover", 8);
    rowBg.setMargin(0f, 0);
    rowBg.setColor(selected ? ROW_SELECT_TINT : ROW_IDLE_TINT);
    root.setBackground(rowBg);

    ColorRGBA swatchColor = entry.getColor() != null ? entry.getColor() : ColorRGBA.Cyan;
    Container swatch = new Container();
    swatch.setPreferredSize(new Vector3f(SWATCH_SIZE, SWATCH_SIZE, 0));
    swatch.setBackground(new QuadBackgroundComponent(swatchColor));
    root.addChild(centerVertically(swatch, SWATCH_SIZE));
    root.addChild(UiKit.hSpacer(ICON_GAP));

    Container badge = new Container();
    badge.setPreferredSize(new Vector3f(BADGE_SIZE, BADGE_SIZE, 0));
    root.addChild(centerVertically(badge, BADGE_SIZE));
    root.addChild(UiKit.hSpacer(ICON_GAP));

    float nameLabelWidth = cols.name() - SWATCH_SIZE - BADGE_SIZE - 2 * ICON_GAP;
    Label nameLabel = root.addChild(new Label(name, FormStyles.STYLE));
    nameLabel.setFont(UiKit.sora(13));
    nameLabel.setColor(FormStyles.TEXT_PRIMARY);
    nameLabel.setTextHAlignment(HAlignment.Left);
    nameLabel.setTextVAlignment(VAlignment.Center);
    nameLabel.setPreferredSize(new Vector3f(nameLabelWidth, HEIGHT, 0));

    Label typeLabel = root.addChild(new Label(MissionTypes.label(entry), FormStyles.STYLE));
    typeLabel.setFont(UiKit.ibmPlexMono(11));
    typeLabel.setColor(FormStyles.TEXT_SECONDARY);
    typeLabel.setTextHAlignment(HAlignment.Left);
    typeLabel.setTextVAlignment(VAlignment.Center);
    typeLabel.setPreferredSize(new Vector3f(cols.type(), HEIGHT, 0));

    boolean computing = status == MissionStatus.COMPUTING;
    computingEntry = computing ? entry : null;
    spinner =
        computing
            ? new SpinnerIcon(SpinnerIcon.SIZE, HEIGHT, SPINNER_LIFT_PX, FormStyles.WARNING)
            : null;

    Container statusCell = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    statusCell.setBackground(null);
    // Zeroed explicitly: the status label used to be a direct child of the row, and the default
    // style's insets on this new grouping container would otherwise shift the whole column.
    statusCell.setInsets(new Insets3f(0, 0, 0, 0));
    statusCell.setPreferredSize(new Vector3f(cols.status(), HEIGHT, 0));

    float labelWidth = cols.status();
    if (spinner != null) {
      // Placed as a sibling of the label, not wrapped: both boxes are HEIGHT tall and anchored the
      // same way, which is what puts them on one optical centre.
      statusCell.addChild(spinner.getNode());
      statusCell.addChild(UiKit.hSpacer(ICON_GAP));
      labelWidth -= SpinnerIcon.SIZE + ICON_GAP;
    }

    statusText = computing ? initialStatusText(entry) : status.name();
    statusLabel = statusCell.addChild(new Label(statusText, FormStyles.STYLE));
    statusLabel.setFont(UiKit.ibmPlexMono(11));
    statusLabel.setColor(MissionStatusColor.forStatus(status));
    statusLabel.setTextHAlignment(HAlignment.Left);
    statusLabel.setTextVAlignment(VAlignment.Center);
    // Fixed, so rewriting the text never remeasures the cell and never shifts the actions column.
    statusLabel.setPreferredSize(new Vector3f(labelWidth, HEIGHT, 0));

    Container actions = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    actions.setBackground(null);
    actions.setPreferredSize(new Vector3f(cols.actions(), HEIGHT, 0));
    populateActions(actions, entry, missionId, status, listener);

    // Hover + selection follow the PopupList pattern (white tint over btn-primary).
    // Action icons consume their own clicks so clicks on icons don't trigger row selection.
    MouseEventControl.addListenersToSpatial(
        root,
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
            listener.onSelect(missionId);
            event.setConsumed();
          }
        });
  }

  Container getNode() {
    return root;
  }

  /**
   * Advances what this row animates. A no-op on a row whose mission is not computing, which is
   * every row most of the time.
   *
   * @param tpf the frame time in seconds
   */
  void update(float tpf) {
    if (spinner == null) {
      return;
    }
    MissionProgress progress = computingEntry.getProgress().orElse(null);
    spinner.setTint(
        progress != null && progress.state() == MissionProgress.State.QUEUED
            ? FormStyles.TEXT_LO
            : FormStyles.WARNING);
    spinner.update(tpf);

    sinceTextRefresh += tpf;
    if (sinceTextRefresh < TEXT_REFRESH_SECONDS) {
      return;
    }
    sinceTextRefresh = 0f;
    String text =
        progress == null
            ? MissionStatus.COMPUTING.name()
            : MissionProgressText.statusCell(progress);
    if (!text.equals(statusText)) {
      statusText = text;
      statusLabel.setText(text);
    }
  }

  private static String initialStatusText(MissionEntry entry) {
    return entry
        .getProgress()
        .map(MissionProgressText::statusCell)
        .orElse(MissionStatus.COMPUTING.name());
  }

  private static void populateActions(
      Container actions,
      MissionEntry entry,
      MissionId missionId,
      MissionStatus status,
      MissionListView.RowListener listener) {
    boolean editableStatus =
        status != MissionStatus.COMPUTING
            && status != MissionStatus.CREATING
            && status != MissionStatus.UPDATING;
    boolean editable = editableStatus && entry.spec().isPresent();

    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.actionIconButton(
                "edit", "edit", editable, () -> listener.onEdit(missionId)),
            HEIGHT));
    actions.addChild(UiKit.hSpacer(RowActionIcons.ICON_GAP));
    actions.addChild(
        RowActionIcons.vCenter(
            ModeSegmentedControl.build(
                entry, editableStatus, type -> listener.onSetMode(missionId, type)),
            HEIGHT));
    actions.addChild(UiKit.hSpacer(RowActionIcons.ICON_GAP));
    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.actionIconButton(
                "compute", "compute", editableStatus, () -> listener.onCompute(missionId)),
            HEIGHT));
    actions.addChild(UiKit.hSpacer(RowActionIcons.ICON_GAP));
    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.actionIconButton(
                "delete", "delete", editableStatus, () -> listener.onDelete(missionId)),
            HEIGHT));
  }

  private static Container centerVertically(Container child, float childHeight) {
    float vPad = Math.max(0f, (HEIGHT - childHeight) * 0.5f - 6);
    Container wrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    wrap.setBackground(null);
    wrap.setPreferredSize(new Vector3f(child.getPreferredSize().x + 5, HEIGHT, 0));
    wrap.addChild(UiKit.vSpacer(vPad));
    wrap.addChild(UiKit.hSpacer(10));
    wrap.addChild(child);
    wrap.addChild(UiKit.vSpacer(vPad));
    wrap.setInsets(new Insets3f(0, 5, 0, 0));
    return wrap;
  }
}
