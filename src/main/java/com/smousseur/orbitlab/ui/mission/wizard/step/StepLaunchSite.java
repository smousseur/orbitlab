package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.*;
import java.util.List;

public class StepLaunchSite {

  private static final float COL3_W = 260f;
  private static final float COL2_W = 400f;
  private static final float MAP_W = 780f;
  private static final float MAP_H = 160f;

  private final Container root;

  public StepLaunchSite() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Label title =
        root.addChild(new Label("LAUNCH SITE", MissionWizardStyles.STYLE));
    title.setFont(MissionWizardStyles.rajdhani(20));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    Label subtitle =
        root.addChild(
            new Label(
                "// cosmodrome selection", MissionWizardStyles.STYLE));
    subtitle.setFont(MissionWizardStyles.mono(12));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    PopupList cosmodrome =
        new PopupList(
            800f,
            List.of(
                "Kourou (CSG) \u2014 French Guiana",
                "Cape Canaveral (CCSFS) \u2014 Florida, USA",
                "Baikonur \u2014 Kazakhstan"),
            "Kourou (CSG) \u2014 French Guiana");
    root.addChild(
        new LabeledField("COSMODROME", cosmodrome.getNode(), null).getNode());

    Container row2 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);
    row2.addChild(
        fieldCol(COL3_W, "LATITUDE", "5.236", "decimal degrees \u00b7 N positive"));
    row2.addChild(
        fieldCol(COL3_W, "LONGITUDE", "-52.769", "decimal degrees \u00b7 E positive"));
    row2.addChild(fieldCol(COL3_W, "GROUND ALTITUDE", "14", "meters MSL"));

    Container row3 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row3.setBackground(null);
    row3.addChild(
        fieldCol(COL2_W, "LAUNCH HEADING", "90.0", "azimuth \u00b7 90\u00b0 = East"));

    Container pressCol =
        new Container(new BoxLayout(Axis.Y, FillMode.None));
    pressCol.setBackground(null);
    pressCol.setPreferredSize(new Vector3f(COL2_W, 0, 0));
    SegmentedControl pressCtrl =
        new SegmentedControl("AUTO", "ISA", "MANUAL").select(0);
    pressCol.addChild(
        new LabeledField(
                "ATMOSPHERIC PRESSURE",
                pressCtrl.getNode(),
                "ground atmospheric model")
            .getNode());
    row3.addChild(pressCol);

    // Mini-map
    Container map = root.addChild(new Container());
    map.setPreferredSize(new Vector3f(MAP_W, MAP_H, 0));
    map.setBackground(
        new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BG_DEEP));

    for (int i = 0; i < 6; i++) {
      Container vLine = new Container();
      vLine.setPreferredSize(new Vector3f(1, MAP_H, 0));
      vLine.setBackground(
          new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BORDER));
      vLine.setLocalTranslation(MAP_W / 7f * (i + 1), MAP_H, 0.1f);
      map.attachChild(vLine);
    }
    for (int i = 0; i < 3; i++) {
      Container hLine = new Container();
      hLine.setPreferredSize(new Vector3f(MAP_W, 1, 0));
      hLine.setBackground(
          new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BORDER));
      hLine.setLocalTranslation(0, MAP_H / 4f * (i + 1), 0.1f);
      map.attachChild(hLine);
    }

    Container dot = new Container();
    dot.setPreferredSize(new Vector3f(8, 8, 0));
    dot.setBackground(
        new QuadBackgroundComponent(MissionWizardStyles.WIZARD_ACCENT));
    dot.setLocalTranslation(MAP_W / 2f - 4f, MAP_H / 2f + 4f, 0.2f);
    map.attachChild(dot);

    Label caption =
        root.addChild(
            new Label(
                "CSG \u00b7 KOUROU \u00b7 5.236\u00b0N 52.769\u00b0W",
                MissionWizardStyles.STYLE));
    caption.setFont(MissionWizardStyles.mono(10));
    caption.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    caption.setTextHAlignment(HAlignment.Right);
  }

  public Container getNode() {
    return root;
  }

  private Container fieldCol(
      float w, String label, String value, String helper) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(w, 0, 0));
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(MissionWizardStyles.mono(14));
    col.addChild(new LabeledField(label, f, helper).getNode());
    return col;
  }
}
