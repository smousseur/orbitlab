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
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;

class MissionRow {

  static final float HEIGHT = 46f;

  private static final ColorRGBA ROW_IDLE_TINT = new ColorRGBA(1f, 1f, 1f, 0f);
  private static final ColorRGBA ROW_HOVER_TINT = new ColorRGBA(1f, 1f, 1f, 0.18f);
  private static final ColorRGBA ROW_SELECT_TINT = new ColorRGBA(1f, 1f, 1f, 0.45f);

  private final Container root;

  MissionRow(
      MissionEntry entry,
      MissionListView.ColumnLayout cols,
      boolean selected,
      MissionListView.RowListener listener) {
    String name = entry.mission().getName();
    MissionStatus status = entry.mission().getStatus();

    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(cols.totalWidth(), HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(6, 12, 6, 12)));
    TbtQuadBackgroundComponent rowBg = UiKit.wizardBg9("btn-primary", 8);
    rowBg.setMargin(0f, 0f);
    rowBg.setColor(selected ? ROW_SELECT_TINT : ROW_IDLE_TINT);
    root.setBackground(rowBg);

    Label nameLabel = root.addChild(new Label(name, FormStyles.STYLE));
    nameLabel.setFont(UiKit.sora(13));
    nameLabel.setColor(FormStyles.TEXT_PRIMARY);
    nameLabel.setTextHAlignment(HAlignment.Left);
    nameLabel.setTextVAlignment(VAlignment.Center);
    nameLabel.setPreferredSize(new Vector3f(cols.name(), HEIGHT, 0));

    Label typeLabel = root.addChild(new Label(MissionTypes.label(entry), FormStyles.STYLE));
    typeLabel.setFont(UiKit.ibmPlexMono(11));
    typeLabel.setColor(FormStyles.TEXT_SECONDARY);
    typeLabel.setTextHAlignment(HAlignment.Left);
    typeLabel.setTextVAlignment(VAlignment.Center);
    typeLabel.setPreferredSize(new Vector3f(cols.type(), HEIGHT, 0));

    Label statusLabel = root.addChild(new Label(status.name(), FormStyles.STYLE));
    statusLabel.setFont(UiKit.ibmPlexMono(11));
    statusLabel.setColor(PanelFooter.statusColor(status));
    statusLabel.setTextHAlignment(HAlignment.Left);
    statusLabel.setTextVAlignment(VAlignment.Center);
    statusLabel.setPreferredSize(new Vector3f(cols.status(), HEIGHT, 0));

    Container actions = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    actions.setBackground(null);
    actions.setPreferredSize(new Vector3f(cols.actions(), HEIGHT, 0));
    populateActions(actions, name, status, entry.isVisible(), listener);

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
            listener.onSelect(name);
            event.setConsumed();
          }
        });
  }

  Container getNode() {
    return root;
  }

  private static void populateActions(
      Container actions,
      String missionName,
      MissionStatus status,
      boolean visible,
      MissionListView.RowListener listener) {
    boolean computing = status == MissionStatus.COMPUTING;
    boolean ready = status == MissionStatus.READY;

    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.actionIconButton(
                "edit", !computing, () -> listener.onEdit(missionName)),
            HEIGHT));
    actions.addChild(UiKit.hSpacer(RowActionIcons.ICON_GAP));
    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.actionIconButton(
                "compute", !computing, () -> listener.onCompute(missionName)),
            HEIGHT));
    actions.addChild(UiKit.hSpacer(RowActionIcons.ICON_GAP));
    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.visualizeIconButton(
                ready, visible, () -> listener.onToggleVisible(missionName)),
            HEIGHT));
    actions.addChild(UiKit.hSpacer(RowActionIcons.ICON_GAP));
    actions.addChild(
        RowActionIcons.vCenter(
            RowActionIcons.actionIconButton(
                "delete", !computing, () -> listener.onDelete(missionName)),
            HEIGHT));
  }
}
