package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import java.util.List;

public class StepLaunchSite {

  private static final float FIELD_W = 752f;
  private static final float FIELD_H = 36f;
  private static final float COL_GAP = 16f;
  // Calcul dynamique : (752 - 32) / 3 = 240 pixels par colonne
  private static final float COL3_W = (FIELD_W - (2 * COL_GAP)) / 3f;
  private static final float ROW_GAP = 16f;
  private static final float LABEL_FIELD_GAP = 6f;

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

    root.addChild(UiKit.vSpacer(6f));

    Label subtitle = root.addChild(new Label("// cosmodrome selection", MissionWizardStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- 1. Cosmodrome (Select) ---
    root.addChild(fieldLabelRow("COSMODROME", "lbl-factory"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    PopupList cosmodrome =
        new PopupList(
            FIELD_W,
            List.of(
                "Kourou (CSG) — French Guiana",
                "Cape Canaveral (CCSFS) — Florida, USA",
                "Baikonur — Kazakhstan"),
            "Kourou (CSG) — French Guiana");

    root.addChild(cosmodrome.getNode());

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- 2. Coordonnées (3 Colonnes) ---
    Container row2 = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);

    row2.addChild(
        fieldCol(COL3_W, "LATITUDE", "5.236", "decimal degrees · N positive", "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(
        fieldCol(COL3_W, "LONGITUDE", "-52.769", "decimal degrees · E positive", "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(fieldCol(COL3_W, "ALTITUDE", "14", "meters MSL", "lbl-mountain"));
  }

  public Container getNode() {
    return root;
  }

  // Identique à StepParameters
  private Container fieldLabelRow(String text, String iconName) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(UiKit.wizardIcon(iconName, 14f, 14f));
    row.addChild(UiKit.hSpacer(6f));
    Label label = row.addChild(new Label(text, MissionWizardStyles.STYLE));
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    return row;
  }

  // Assemble la colonne avec les vraies insets de StepParameters
  private Container fieldCol(
      float w, String labelText, String value, String helperText, String iconName) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(w, 0, 0));

    // A. Label et Icone
    col.addChild(fieldLabelRow(labelText, iconName));
    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    // B. Champ texte
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(UiKit.ibmPlexMono(13));
    f.setPreferredSize(new Vector3f(w, FIELD_H, 0)); // Hauteur cible respectée
    f.setInsets(new Insets3f(0, 0, 10, 0)); // Centre verticalement
    f.setInsetsComponent(new InsetsComponent(new Insets3f(3, 10, 0, 0))); // Marge texte à gauche
    col.addChild(f);

    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    // C. Hint
    Label helper = col.addChild(new Label(helperText, MissionWizardStyles.STYLE));
    helper.setFont(UiKit.ibmPlexMono(11));
    helper.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

    return col;
  }
}
