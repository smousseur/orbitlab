package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.LabeledField;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import java.util.List;

public class StepLaunchSite {

  private static final float COSMODROME_W = 520f;
  private static final float COL3_W = 140f;
  private static final float COL_GAP = 16f;
  private static final float ROW_GAP = 12f;

  private final Container root;

  public StepLaunchSite() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            MissionWizardStyles.WIZARD_CONTENT_WIDTH,
            MissionWizardStyles.WIZARD_CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("LAUNCH SITE", MissionWizardStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Label subtitle = root.addChild(new Label("// cosmodrome selection", MissionWizardStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    PopupList cosmodrome =
        new PopupList(
            COSMODROME_W,
            List.of(
                "Kourou (CSG) \u2014 French Guiana",
                "Cape Canaveral (CCSFS) \u2014 Florida, USA",
                "Baikonur \u2014 Kazakhstan"),
            "Kourou (CSG) \u2014 French Guiana");
    root.addChild(
        widthBoundedRow(
            new LabeledField("COSMODROME", cosmodrome.getNode(), null, "lbl-factory")
                .getNode(),
            COSMODROME_W));

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Container row2 = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);
    row2.addChild(
        fieldCol(
            COL3_W,
            "LATITUDE",
            "5.236",
            "decimal degrees \u00b7 N positive",
            "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(
        fieldCol(
            COL3_W,
            "LONGITUDE",
            "-52.769",
            "decimal degrees \u00b7 E positive",
            "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(
        fieldCol(COL3_W, "GROUND ALTITUDE", "14", "meters MSL", "lbl-mountain"));
    float trailing = MissionWizardStyles.WIZARD_CONTENT_WIDTH - 3 * COL3_W - 2 * COL_GAP;
    if (trailing > 0f) {
      row2.addChild(UiKit.hSpacer(trailing));
    }
  }

  public Container getNode() {
    return root;
  }

  private Container fieldCol(float w, String label, String value, String helper, String iconName) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(w, 0, 0));
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(UiKit.ibmPlexMono(11));
    f.setPreferredSize(new Vector3f(w, 0, 0));
    col.addChild(new LabeledField(label, f, helper, iconName).getNode());
    return col;
  }

  /** Wraps a child in an X-row with a trailing invisible spacer so it keeps its fixed width. */
  private Container widthBoundedRow(Container child, float childWidth) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(child);
    float trailing = MissionWizardStyles.WIZARD_CONTENT_WIDTH - childWidth;
    if (trailing > 0f) {
      row.addChild(UiKit.hSpacer(trailing));
    }
    return row;
  }
}
