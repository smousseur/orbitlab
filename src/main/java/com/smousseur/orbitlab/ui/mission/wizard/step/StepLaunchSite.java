package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
import com.smousseur.orbitlab.ui.mission.wizard.SiteCoordinates;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class StepLaunchSite implements StepValues {

  private static final float FIELD_W = 752f;
  private static final float FIELD_H = 36f;
  private static final float COL_GAP = 16f;
  private static final float COL3_W = (FIELD_W - (2 * COL_GAP)) / 3f;
  private static final float ROW_GAP = 16f;
  private static final float LABEL_FIELD_GAP = 6f;

  private final Container root;

  private final PopupList cosmodrome;
  private final TextField latField;
  private final TextField lonField;
  private final TextField altField;
  private String selectedSiteName;

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
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

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

    cosmodrome = new PopupList(FIELD_W, 40, 12, siteNames, siteNames.getFirst());
    root.addChild(cosmodrome.getNode());

    root.addChild(UiKit.vSpacer(ROW_GAP));

    SiteData defaultSite = sites.getFirst();
    selectedSiteName = defaultSite.name;
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
                      selectedSiteName = site.name;
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

  /**
   * The latitude currently entered, in degrees.
   *
   * <p>Read off the field rather than off the selected cosmodrome, because the coordinates stay
   * editable after a site is picked. The parameters step calls this on every frame to bound its
   * inclination field, so a hand-typed latitude moves the bound with it (spec {@code
   * docs/earth-orbit/02-wizard-orbites-terrestres.md} §5).
   *
   * @return the launch latitude in degrees, or 0 while the field holds something unreadable
   */
  public double currentLatitude() {
    return parseDoubleOrZero(latField.getText());
  }

  /**
   * The pad's three coordinates, or empty when any of the three fields does not read as a number.
   *
   * <p><b>Empty rather than zero, and that is a deliberate departure from {@link
   * #currentLatitude()}.</b> A latitude falling back to 0 only widens the inclination band the
   * parameters step allows, which is harmless; a launch window computed at latitude 0 is a wrong
   * answer presented as a right one. The two conventions differ because what they feed differs.
   *
   * @return the coordinates, or empty while any field is unreadable
   */
  public Optional<SiteCoordinates> currentSite() {
    Optional<Double> latitude = parseDouble(latField.getText());
    Optional<Double> longitude = parseDouble(lonField.getText());
    Optional<Double> altitude = parseDouble(altField.getText());
    if (latitude.isEmpty() || longitude.isEmpty() || altitude.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SiteCoordinates(latitude.get(), longitude.get(), altitude.get()));
  }

  private static Optional<Double> parseDouble(String text) {
    try {
      return Optional.of(Double.parseDouble(text.trim()));
    } catch (NumberFormatException | NullPointerException e) {
      return Optional.empty();
    }
  }

  @Override
  public Map<String, Object> getValues() {
    return Map.of(
        FormField.LAUNCH_SITE_NAME.key(), selectedSiteName,
        FormField.LAUNCH_SITE_LAT.key(), parseDoubleOrZero(latField.getText()),
        FormField.LAUNCH_SITE_LONG.key(), parseDoubleOrZero(lonField.getText()),
        FormField.LAUNCH_SITE_ALT.key(), parseDoubleOrZero(altField.getText()));
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    String siteName = FormValues.string(values, FormField.LAUNCH_SITE_NAME);
    if (siteName != null) {
      selectedSiteName = siteName;
      // A spec assembled outside the wizard may name a site the cosmodrome list does not offer. The
      // coordinates applied below still describe it exactly, so the trigger just keeps its label
      // rather than claiming a site the mission does not launch from.
      sites.stream()
          .filter(site -> site.name.equals(siteName))
          .findFirst()
          .ifPresent(site -> cosmodrome.setSelectedValue(site.name));
    }
    applyCoordinate(values, FormField.LAUNCH_SITE_LAT, latField);
    applyCoordinate(values, FormField.LAUNCH_SITE_LONG, lonField);
    applyCoordinate(values, FormField.LAUNCH_SITE_ALT, altField);
  }

  private void applyCoordinate(
      Map<String, Object> values, FormField<Double> field, TextField target) {
    if (values.get(field.key()) == null) {
      return;
    }
    target.setText(formatCoordinate(FormValues.number(values, field, 0d)));
  }

  /**
   * Formats a coordinate the way the cosmodrome table writes them: plain decimals, no trailing
   * zeros, and no exponent or locale comma that {@link #parseDoubleOrZero(String)} would reject.
   */
  private static String formatCoordinate(double value) {
    String text = String.format(Locale.ROOT, "%.6f", value);
    text = text.replaceAll("0+$", "");
    return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
  }

  private static double parseDoubleOrZero(String text) {
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return 0d;
    }
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

    col.addChild(field);

    col.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    Label helper = col.addChild(new Label(helperText, FormStyles.STYLE));
    helper.setFont(UiKit.ibmPlexMono(11));
    helper.setColor(FormStyles.TEXT_LO);

    return col;
  }
}
