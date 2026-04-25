package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import java.util.List;

public class StepLaunchSite {

  private static final float FIELD_W = 752f;
  private static final float FIELD_H = 36f;
  private static final float COL_GAP = 16f;
  private static final float COL3_W = (FIELD_W - (2 * COL_GAP)) / 3f;
  private static final float ROW_GAP = 16f;
  private static final float LABEL_FIELD_GAP = 6f;

  private final Container root;

  private final TextField latField;
  private final TextField lonField;
  private final TextField altField;

  private record SiteData(String name, String lat, String lon, String alt) {}

  private static final List<SiteData> sites =
      List.of(
          new SiteData("Kourou - French Guiana", "5.236", "-52.769", "14"),
          new SiteData("Cape Canaveral - USA", "28.562", "-80.577", "3"),
          new SiteData("Baikonur - Kazakhstan", "45.965", "63.305", "105"),
          new SiteData("Vandenberg - USA", "34.632", "-120.611", "112"),
          new SiteData("Tanegashima - Japan", "30.400", "130.970", "16"));

  public StepLaunchSite() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            FormStyles.CONTENT_WIDTH,
            FormStyles.CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("LAUNCH SITE", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(6f));

    Label subtitle = root.addChild(new Label("// cosmodrome selection", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    root.addChild(UiKit.fieldLabelRow("COSMODROME", "lbl-factory"));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    List<String> siteNames = sites.stream().map(s -> s.name).toList();

    PopupList cosmodrome = new PopupList(FIELD_W, 40, 12, siteNames, siteNames.getFirst());
    root.addChild(cosmodrome.getNode());

    root.addChild(UiKit.vSpacer(ROW_GAP));

    SiteData defaultSite = sites.getFirst();
    latField = createTextField(defaultSite.lat);
    lonField = createTextField(defaultSite.lon);
    altField = createTextField(defaultSite.alt);

    cosmodrome.setOnSelect(
        selectedName ->
            sites.stream()
                .filter(site -> site.name.equals(selectedName))
                .findFirst()
                .ifPresent(
                    site -> {
                      latField.setText(site.lat);
                      lonField.setText(site.lon);
                      altField.setText(site.alt);
                    }));

    Container row2 = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);

    row2.addChild(fieldCol("LATITUDE", latField, "decimal degrees N positive", "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(fieldCol("LONGITUDE", lonField, "decimal degrees E positive", "lbl-globe"));
    row2.addChild(UiKit.hSpacer(COL_GAP));
    row2.addChild(fieldCol("ALTITUDE", altField, "meters MSL", "lbl-mountain"));
  }

  public Container getNode() {
    return root;
  }

  private TextField createTextField(String value) {
    TextField f = new TextField(value, FormStyles.STYLE);
    f.setFont(UiKit.ibmPlexMono(13));
    f.setPreferredSize(new Vector3f(StepLaunchSite.COL3_W, FIELD_H, 0));
    f.setInsets(new Insets3f(0, 0, 10, 0));
    f.setInsetsComponent(new InsetsComponent(new Insets3f(3, 10, 0, 0)));
    return f;
  }

  private Container fieldCol(
      String labelText, TextField field, String helperText, String iconName) {
    Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
    col.setBackground(null);
    col.setPreferredSize(new Vector3f(StepLaunchSite.COL3_W, 0, 0));

    col.addChild(UiKit.fieldLabelRow(labelText, iconName));
    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    // On attache le champ qui a été passé
    col.addChild(field);

    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    Label helper = col.addChild(new Label(helperText, FormStyles.STYLE));
    helper.setFont(UiKit.ibmPlexMono(11));
    helper.setColor(FormStyles.TEXT_LO);

    return col;
  }
}
