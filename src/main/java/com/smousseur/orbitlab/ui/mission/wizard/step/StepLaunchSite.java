package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import java.util.List;
import java.util.stream.Collectors;

public class StepLaunchSite {

  private static final float FIELD_W = 752f;
  private static final float FIELD_H = 36f;
  private static final float COL_GAP = 16f;
  private static final float COL3_W = (FIELD_W - (2 * COL_GAP)) / 3f;
  private static final float ROW_GAP = 16f;
  private static final float LABEL_FIELD_GAP = 6f;

  private final Container root;

  // FIX 1 : On promeut les champs textes en attributs pour pouvoir les mettre à jour
  private final TextField latField;
  private final TextField lonField;
  private final TextField altField;

  // FIX 2 : Structure de données pour lier le nom aux coordonnées
  private static class SiteData {
    String name, lat, lon, alt;

    SiteData(String name, String lat, String lon, String alt) {
      this.name = name;
      this.lat = lat;
      this.lon = lon;
      this.alt = alt;
    }
  }

  private final List<SiteData> sites =
      List.of(
          new SiteData("Kourou (CSG) — French Guiana", "5.236", "-52.769", "14"),
          new SiteData("Cape Canaveral (SLC-40) — USA", "28.562", "-80.577", "3"),
          new SiteData("Baikonur — Kazakhstan", "45.965", "63.305", "105"),
          new SiteData("Vandenberg SFB (SLC-4E) — USA", "34.632", "-120.611", "112"),
          new SiteData("Tanegashima — Japan", "30.400", "130.970", "16"));

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

    root.addChild(fieldLabelRow("COSMODROME", "lbl-factory"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    List<String> siteNames = sites.stream().map(s -> s.name).collect(Collectors.toList());

    PopupList cosmodrome = new PopupList(FIELD_W, siteNames, siteNames.getFirst());
    root.addChild(cosmodrome.getNode());

    root.addChild(UiKit.vSpacer(ROW_GAP));

    SiteData defaultSite = sites.get(0);
    latField = createTextField(defaultSite.lat, COL3_W);
    lonField = createTextField(defaultSite.lon, COL3_W);
    altField = createTextField(defaultSite.alt, COL3_W);

    cosmodrome.setOnSelect(
        selectedName -> {
          for (SiteData site : sites) {
            if (site.name.equals(selectedName)) {
              latField.setText(site.lat);
              lonField.setText(site.lon);
              altField.setText(site.alt);
              break;
            }
          }
        });

    // --- 3. Coordonnées (3 Colonnes) ---
    Container row2 = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);

    row2.addChild(
        fieldCol(COL3_W, "LATITUDE", latField, "decimal degrees N positive", "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(
        fieldCol(COL3_W, "LONGITUDE", lonField, "decimal degrees E positive", "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(fieldCol(COL3_W, "ALTITUDE", altField, "meters MSL", "lbl-mountain"));
  }

  public Container getNode() {
    return root;
  }

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

  // Méthode utilitaire pour générer le champ texte formaté
  private TextField createTextField(String value, float w) {
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(UiKit.ibmPlexMono(13));
    f.setPreferredSize(new Vector3f(w, FIELD_H, 0));
    f.setInsets(new Insets3f(0, 0, 10, 0));
    f.setInsetsComponent(new InsetsComponent(new Insets3f(3, 10, 0, 0)));
    return f;
  }

  // FIX 4 : La méthode prend désormais le TextField pré-instancié en paramètre
  private Container fieldCol(
      float w, String labelText, TextField field, String helperText, String iconName) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(w, 0, 0));

    col.addChild(fieldLabelRow(labelText, iconName));
    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    // On attache le champ qui a été passé
    col.addChild(field);

    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    Label helper = col.addChild(new Label(helperText, MissionWizardStyles.STYLE));
    helper.setFont(UiKit.ibmPlexMono(11));
    helper.setColor(MissionWizardStyles.WIZARD_TEXT_LO);

    return col;
  }
}
