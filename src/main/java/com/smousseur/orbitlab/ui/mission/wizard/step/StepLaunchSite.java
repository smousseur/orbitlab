package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.LabeledField;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import java.util.List;

public class StepLaunchSite {

  private static final float COL3_W = 260f;

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
        new LabeledField(
                "COSMODROME",
                cosmodrome.getNode(),
                null,
                "icons/wizard/field-building.png")
            .getNode());

    Container row2 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);
    row2.addChild(
        fieldCol(
            COL3_W,
            "LATITUDE",
            "5.236",
            "decimal degrees \u00b7 N positive",
            "icons/wizard/field-globe-lat.png"));
    row2.addChild(
        fieldCol(
            COL3_W,
            "LONGITUDE",
            "-52.769",
            "decimal degrees \u00b7 E positive",
            "icons/wizard/field-globe-lon.png"));
    row2.addChild(
        fieldCol(
            COL3_W,
            "GROUND ALTITUDE",
            "14",
            "meters MSL",
            "icons/wizard/field-mountain.png"));
  }

  public Container getNode() {
    return root;
  }

  private Container fieldCol(
      float w, String label, String value, String helper, String iconPath) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(w, 0, 0));
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(MissionWizardStyles.mono(14));
    col.addChild(new LabeledField(label, f, helper, iconPath).getNode());
    return col;
  }
}
