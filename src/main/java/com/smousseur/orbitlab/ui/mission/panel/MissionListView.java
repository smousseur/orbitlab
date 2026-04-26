package com.smousseur.orbitlab.ui.mission.panel;

import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
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
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.MissionEntry;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import java.util.List;

public class MissionListView {

  public interface RowListener {
    void onSelect(String missionName);

    void onEdit(String missionName);

    void onCompute(String missionName);

    void onToggleVisible(String missionName);

    void onDelete(String missionName);
  }

  private static final float PAD_X = 32f;
  private static final float PAD_Y = 20f;
  private static final float COL_NAME = 220f;
  private static final float COL_TYPE = 90f;
  private static final float COL_STATUS = 130f;
  private static final float COL_HEADER_ALPHA = 0.6f;
  private static final float NEW_MISSION_BTN_W = 160f;
  private static final float ACTIONS_BAR_H = 32f;

  record ColumnLayout(float name, float type, float status, float actions) {
    float totalWidth() {
      return name + type + status + actions;
    }
  }

  private final Container root;
  private final Container listContainer;
  private final ColumnLayout columns;
  private final float innerWidth;

  private Runnable onNewMission = () -> {};
  private MissionListView.RowListener rowListener = noopRowListener();

  public MissionListView(float width, float height) {
    this.innerWidth = width - 2 * PAD_X;
    float actionsW = innerWidth - COL_NAME - COL_TYPE - COL_STATUS;
    this.columns = new ColumnLayout(COL_NAME, COL_TYPE, COL_STATUS, actionsW);

    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(new Vector3f(width, height, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(PAD_Y, PAD_X, PAD_Y, PAD_X)));

    Container actionsBar = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    actionsBar.setBackground(null);
    actionsBar.setPreferredSize(new Vector3f(innerWidth, ACTIONS_BAR_H, 0));
    actionsBar.addChild(UiKit.hSpacer(innerWidth - NEW_MISSION_BTN_W));
    actionsBar.addChild(buildNewMissionButton());
    root.addChild(UiKit.vSpacer(12));

    Container columnHeader = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    columnHeader.setBackground(null);
    columnHeader.setPreferredSize(new Vector3f(innerWidth, 14f, 0));
    columnHeader.setInsetsComponent(new InsetsComponent(new Insets3f(0, 8, 0, 8)));
    columnHeader.addChild(columnHeaderLabel("NAME", COL_NAME));
    columnHeader.addChild(columnHeaderLabel("TYPE", COL_TYPE));
    columnHeader.addChild(columnHeaderLabel("STATUS", COL_STATUS));
    columnHeader.addChild(columnHeaderLabel("ACTIONS", actionsW));

    root.addChild(UiKit.vSpacer(6));
    root.addChild(divider());
    root.addChild(UiKit.vSpacer(6));

    listContainer = root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    listContainer.setBackground(null);
    listContainer.setPreferredSize(new Vector3f(innerWidth, 0, 0));
  }

  public Container getNode() {
    return root;
  }

  public void setOnNewMission(Runnable action) {
    this.onNewMission = action != null ? action : () -> {};
  }

  public void setRowListener(MissionListView.RowListener listener) {
    this.rowListener = listener != null ? listener : noopRowListener();
  }

  public void refresh(List<MissionEntry> entries, String selectedMissionName) {
    listContainer.clearChildren();
    if (entries.isEmpty()) {
      Label empty = listContainer.addChild(new Label("No missions yet", FormStyles.STYLE));
      empty.setFont(UiKit.sora(13));
      empty.setColor(FormStyles.TEXT_SECONDARY);
      empty.setPreferredSize(new Vector3f(innerWidth, 24, 0));
      return;
    }

    for (int i = 0; i < entries.size(); i++) {
      MissionEntry entry = entries.get(i);
      boolean selected = entry.mission().getName().equals(selectedMissionName);
      MissionRow row = new MissionRow(entry, columns, selected, rowListener);
      listContainer.addChild(row.getNode());
      if (i < entries.size() - 1) {
        listContainer.addChild(divider());
      }
    }
  }

  private Button buildNewMissionButton() {
    Button btn = new Button("+ New mission", FormStyles.STYLE);
    btn.setFont(UiKit.sora(13));
    btn.setColor(FormStyles.TEXT_PRIMARY);
    TbtQuadBackgroundComponent bg = UiKit.wizardBg9("btn-primary", 8);
    bg.setMargin(0f, 0f);
    btn.setBackground(bg);
    btn.setInsetsComponent(new InsetsComponent(new Insets3f(6, 16, 6, 16)));
    btn.setPreferredSize(new Vector3f(NEW_MISSION_BTN_W, ACTIONS_BAR_H, 0));
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

  private Container divider() {
    Container d = new Container();
    d.setPreferredSize(new Vector3f(innerWidth, 1, 0));
    d.setBackground(new QuadBackgroundComponent(FormStyles.BORDER));
    return d;
  }

  private static MissionListView.RowListener noopRowListener() {
    return new MissionListView.RowListener() {
      @Override
      public void onSelect(String missionName) {}

      @Override
      public void onEdit(String missionName) {}

      @Override
      public void onCompute(String missionName) {}

      @Override
      public void onToggleVisible(String missionName) {}

      @Override
      public void onDelete(String missionName) {}
    };
  }
}
