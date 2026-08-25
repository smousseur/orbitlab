package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import static com.smousseur.orbitlab.ui.UiKit.fieldLabelRow;
import static com.smousseur.orbitlab.ui.UiKit.newInputField;
import static com.smousseur.orbitlab.ui.mission.wizard.step.StepParameters.*;

import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import org.hipparchus.util.FastMath;
import org.orekit.utils.Constants;

/**
 * The parameters of any {@code MissionSpec.EarthOrbit} target — LEO, polar, sun-synchronous or MEO
 * (spec {@code docs/earth-orbit/02-wizard-orbites-terrestres.md} §3).
 *
 * <p>One class rather than four, because the four differ only in data: the altitude band they
 * offer, whether the target is circular, and where the inclination comes from. All three are
 * carried by {@link MissionProfile}, which is what makes the profiles presets instead of types.
 *
 * <p>Replaces {@code LEODynamicParameters}, whose two altitude sliders it keeps.
 */
public class EarthOrbitDynamicParameters extends DynamicParameters {

  private static final float INCLINATION_FIELD_W = 120f;

  /** The helper line while the inclination is derived from the launch site. */
  private static final String AUTO_HELPER = "free plane of the launch site - due east";

  /** And while it is derived from the altitude. */
  private static final String DERIVED_HELPER = "sun-synchronous - derived from the altitude";

  private final MissionProfile profile;
  private final DoubleSupplier launchLatitudeDeg;

  private final double altitudeMin;
  private final double altitudeMax;

  /** The perigee slider; on a circular profile it is the one and only altitude. */
  private final Slider perigeeSlider;

  private final TextField perigeeField;
  private final VersionedReference<Double> perigeeSliderRef;
  private boolean perigeeFieldWasFocused;

  /** Null on a circular profile, where perigee and apogee are the same value by construction. */
  private final Slider apogeeSlider;

  private final TextField apogeeField;
  private final VersionedReference<Double> apogeeSliderRef;
  private boolean apogeeFieldWasFocused;

  private TextField inclinationField;
  private Label inclinationHelper;

  /**
   * Whether the inclination still shows the value this panel derived. The whole of the "auto"
   * state, exactly as {@code StepParameters} holds the mission duration's: while it holds, {@link
   * #getDynamicValues()} omits the key, and an absent key is what makes {@code MissionFactory}
   * rebuild the site's free plane from the latitude in double (spec §2.0).
   *
   * <p>Always false on an {@link MissionProfile.InclinationMode#EXPLICIT} profile: choosing the
   * POLAR or MEO card <em>is</em> the intent.
   */
  private boolean inclinationAuto;

  /** The last text this panel wrote into the field, to tell a prefill from a user edit. */
  private String lastAutoInclinationText = "";

  /** Entry that was refused, kept so the error state clears as soon as it is edited. */
  private String rejectedInclination;

  /**
   * Builds the panel of a profile.
   *
   * @param profile the target family whose bounds and inclination behaviour this panel offers
   * @param launchLatitudeDeg the <b>live</b> launch site latitude — read on every frame, not
   *     captured, because the coordinates stay editable after the cosmodrome is picked (spec §5)
   */
  public EarthOrbitDynamicParameters(MissionProfile profile, DoubleSupplier launchLatitudeDeg) {
    this.profile = profile;
    this.launchLatitudeDeg = launchLatitudeDeg;
    this.altitudeMin = profile.altitudes().minKm();
    this.altitudeMax = profile.altitudes().maxKm();
    this.inclinationAuto = profile.inclinationMode() == MissionProfile.InclinationMode.AUTO;

    double defaultKm = profile.altitudes().defaultKm();
    perigeeField = new TextField(formatKm(defaultKm), FormStyles.STYLE);
    perigeeSlider = buildSlider(altitudeMin, altitudeMax, defaultKm);
    perigeeSliderRef = perigeeSlider.getModel().createReference();

    if (profile.isCircular()) {
      apogeeField = null;
      apogeeSlider = null;
      apogeeSliderRef = null;
    } else {
      apogeeField = new TextField(formatKm(defaultKm), FormStyles.STYLE);
      apogeeSlider = buildSlider(altitudeMin, altitudeMax, defaultKm);
      apogeeSliderRef = apogeeSlider.getModel().createReference();
    }

    this.container = createContainer();
    initialiseInclination();
  }

  /**
   * Fills the inclination field with the value the profile starts on.
   *
   * <p>Not folded into {@link #refreshDerivedInclination()}, which by design does nothing on an
   * {@link MissionProfile.InclinationMode#EXPLICIT} profile — the whole point there being that the
   * value stops following anything the moment the panel exists. Without this, POLAR and MEO would
   * open on an empty field.
   */
  private void initialiseInclination() {
    if (profile.inclinationMode() == MissionProfile.InclinationMode.EXPLICIT) {
      inclinationField.setText(
          formatDegrees(
              profile.initialInclinationDeg(launchLatitudeDeg.getAsDouble(), targetAltitude())));
      refreshReachableHelper();
      return;
    }
    refreshDerivedInclination();
  }

  @Override
  protected Container createContainer() {
    Container parameters = new Container(new BoxLayout(Axis.Y, FillMode.Even));
    if (profile.isCircular()) {
      parameters.addChild(
          getSliderContainer(
              "ORBIT ALTITUDE", perigeeSlider, perigeeField, altitudeMin, altitudeMax));
    } else {
      parameters.addChild(
          getSliderContainer(
              "PERIGEE ALTITUDE", perigeeSlider, perigeeField, altitudeMin, altitudeMax));
      parameters.addChild(
          getSliderContainer(
              "APOGEE ALTITUDE", apogeeSlider, apogeeField, altitudeMin, altitudeMax));
    }
    parameters.addChild(buildInclinationRow());
    return parameters;
  }

  /**
   * The target plane: the inclination, its unit and the line that says where it comes from.
   *
   * <p><b>The helper sits beside the field, not under it</b>, and that is a constraint rather than
   * a taste: the step's root is pinned to {@code FormStyles.CONTENT_HEIGHT} and nothing in this
   * wizard clips, so an overflow lands on the footer. Two altitude sliders plus a three-storey
   * inclination block does not fit; two sliders plus this one does. The circular profiles, with a
   * single slider, would have had the room — but one layout that fits everywhere beats two that
   * each fit once.
   *
   * <p>The target node used to share this row for that same lack of height, and has since moved to
   * the planning page, where the launch window it governs is shown (spec {@code
   * docs/mission-window/02-timeline-wizard.md} §1).
   */
  private Container buildInclinationRow() {
    Container column = new Container(new BoxLayout(Axis.Y, FillMode.None));
    column.setBackground(null);
    column.addChild(
        fieldLabelRow("TARGET INCLINATION", "lbl-globe", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    column.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    inclinationField = newInputField("", INCLINATION_FIELD_W, FIELD_H);
    if (profile.inclinationMode() == MissionProfile.InclinationMode.DERIVED) {
      // The one profile whose inclination is not a choice: the altitude decides it.
      UiKit.makeReadOnly(inclinationField);
    }
    row.addChild(inclinationField);
    row.addChild(UiKit.hSpacer(8f));

    row.addChild(centeredInRow(degreesLabel()));

    row.addChild(UiKit.hSpacer(16f));

    inclinationHelper = new Label("", FormStyles.STYLE);
    inclinationHelper.setFont(UiKit.ibmPlexMono(11));
    inclinationHelper.setColor(FormStyles.TEXT_LO);
    row.addChild(centeredInRow(inclinationHelper));

    column.addChild(row);
    return column;
  }

  /** The unit that follows the inclination field. */
  private static Label degreesLabel() {
    Label unit = new Label("deg", FormStyles.STYLE);
    unit.setFont(UiKit.ibmPlexMono(11));
    unit.setColor(FormStyles.TEXT_LO);
    return unit;
  }

  /**
   * Wraps a label shorter than the field it sits beside so it lines up on the row's centre line
   * rather than on its top edge. Padding, not alignment — the same recipe {@code StepParameters}
   * uses for the duration row's unit and AUTO indicator, and for the same reason: a label sizes
   * itself to its content, so there is no box for an alignment to work inside.
   */
  private static Container centeredInRow(Label child) {
    Container wrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    wrap.setBackground(null);
    float pad = Math.max(0f, (FIELD_H - child.getPreferredSize().y) * 0.5f);
    wrap.addChild(UiKit.vSpacer(pad));
    wrap.addChild(child);
    wrap.addChild(UiKit.vSpacer(pad));
    return wrap;
  }

  @Override
  public void update(float tpf) {
    perigeeFieldWasFocused =
        updateSlider(
            perigeeSlider,
            perigeeSliderRef,
            perigeeField,
            perigeeFieldWasFocused,
            altitudeMin,
            altitudeMax);
    if (apogeeSlider != null) {
      apogeeFieldWasFocused =
          updateSlider(
              apogeeSlider,
              apogeeSliderRef,
              apogeeField,
              apogeeFieldWasFocused,
              altitudeMin,
              altitudeMax);
    }
    updateInclination();
  }

  /**
   * Keeps the inclination in step with what derives it, and notices the first keystroke that takes
   * it off the derived value.
   *
   * <p>Like the mission duration's auto state, this needs no widget of its own: the field's text
   * either is what this panel last wrote, or it is not.
   */
  private void updateInclination() {
    switch (profile.inclinationMode()) {
      case NONE -> {}
      case DERIVED -> refreshDerivedInclination();
      case AUTO -> {
        if (inclinationAuto && !inclinationField.getText().equals(lastAutoInclinationText)) {
          inclinationAuto = false;
        }
        if (inclinationAuto) {
          refreshDerivedInclination();
        } else {
          refreshReachableHelper();
        }
      }
      case EXPLICIT -> refreshReachableHelper();
    }
  }

  /** Writes the value this panel derives — the site's free plane, or the altitude's SSO. */
  private void refreshDerivedInclination() {
    if (profile.inclinationMode() != MissionProfile.InclinationMode.AUTO
        && profile.inclinationMode() != MissionProfile.InclinationMode.DERIVED) {
      return;
    }
    String text;
    try {
      text =
          formatDegrees(
              profile.initialInclinationDeg(launchLatitudeDeg.getAsDouble(), targetAltitude()));
    } catch (RuntimeException e) {
      // No sun-synchronous inclination at this altitude. The field keeps what it shows and the
      // helper carries the reason; validateTargetPlane() is what refuses the step.
      setInclinationHelper(e.getMessage(), FormStyles.DANGER);
      return;
    }
    if (!text.equals(inclinationField.getText())) {
      inclinationField.setText(text);
    }
    lastAutoInclinationText = text;
    setInclinationHelper(
        profile.inclinationMode() == MissionProfile.InclinationMode.DERIVED
            ? DERIVED_HELPER
            : AUTO_HELPER,
        FormStyles.TEXT_LO);
  }

  /**
   * Says what the launch site can reach, in the model's own words: the band is the one {@code
   * LaunchPlane.requireReachableFrom} enforces, and the minimum is the latitude itself.
   */
  private void refreshReachableHelper() {
    if (rejectedInclination != null) {
      if (!rejectedInclination.equals(inclinationField.getText())) {
        clearInclinationRejection();
      }
      return;
    }
    double minimum = FastMath.abs(launchLatitudeDeg.getAsDouble());
    setInclinationHelper(
        String.format(
            Locale.ROOT, "reachable from this site: %.2f to %.2f deg", minimum, 180.0 - minimum),
        FormStyles.TEXT_LO);
  }

  /**
   * Checks the inclination against the site, on the same contract as {@code
   * StepParameters.validateLaunchDate()}: caught while the wizard is still open rather than thrown
   * at mission creation, on a background thread, with the wizard already closed.
   *
   * <p><b>The rule is not reimplemented here.</b> The check <em>is</em> {@code
   * LaunchPlane.requireReachableFrom}, so the band the user is held to is by construction the band
   * the model enforces (spec §4).
   *
   * <p>The wording, on the other hand, is the wizard's. The model's message is a full sentence
   * naming the value, the latitude, the band and why the minimum is the latitude — right for a log
   * or a stack trace, and three times too wide for the line beside the field. So the helper carries
   * the short form and the returned reason carries the model's, which is what reaches the log.
   *
   * @return the reason the inclination was refused, or empty when it is usable
   */
  @Override
  public Optional<String> validateTargetPlane() {
    if (profile.inclinationMode() == MissionProfile.InclinationMode.NONE) {
      return Optional.empty();
    }
    String text = inclinationField.getText().trim();
    double latitude = launchLatitudeDeg.getAsDouble();
    try {
      currentPlane(latitude);
    } catch (NumberFormatException e) {
      String message = "expected an inclination in degrees";
      return Optional.of(rejectInclination(text, message, message));
    } catch (OrbitlabException e) {
      return Optional.of(rejectInclination(text, reachableBand(latitude), e.getMessage()));
    }
    clearInclinationRejection();
    return Optional.empty();
  }

  /**
   * The plane the inclination field describes, checked against the site that has to reach it.
   *
   * <p>The one construction shared by the step's two readers of this field — {@link
   * #validateTargetPlane()}, which refuses what the site cannot reach, and {@link
   * #targetOrbit(double)}, which hands the plane to the launch window. Written once so the window
   * is planned for the very plane the wizard accepted.
   *
   * @param latitudeDeg the launch site latitude in degrees
   * @return the plane
   * @throws NumberFormatException if the field does not read as a number
   * @throws OrbitlabException if the site cannot reach that inclination
   */
  private LaunchPlane currentPlane(double latitudeDeg) {
    double inclinationDeg = Double.parseDouble(inclinationField.getText().trim());
    return LaunchPlane.ofDegrees(inclinationDeg).requireReachableFrom(latitudeDeg);
  }

  /**
   * The target this panel describes: the plane of its inclination field, and the semi-major axis of
   * its two altitude sliders.
   *
   * <p>Empty on every state {@link #validateTargetPlane()} refuses — an unreadable entry or a plane
   * out of the site's reach — because a window planned on either would be an answer to a question
   * the user has not finished asking.
   */
  @Override
  public Optional<TargetOrbit> targetOrbit(double latitudeDeg) {
    try {
      return Optional.of(
          new TargetOrbit(
              currentPlane(latitudeDeg),
              // The sliders are in kilometres: a = R + 1000 × (perigee + apogee) / 2.
              Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 500.0 * (perigeeKm() + apogeeKm())));
    } catch (OrbitlabException | NumberFormatException unreachable) {
      return Optional.empty();
    }
  }

  @Override
  public boolean hasRejection() {
    return rejectedInclination != null;
  }

  /** The band this site reaches, phrased for a line that has to fit beside the field. */
  private static String reachableBand(double latitudeDeg) {
    double minimum = FastMath.abs(latitudeDeg);
    return String.format(
        Locale.ROOT, "unreachable - this site reaches %.2f to %.2f deg", minimum, 180.0 - minimum);
  }

  /**
   * @param helper the short form, shown beside the field
   * @param reason the model's own wording, returned to the caller and thence to the log
   */
  private String rejectInclination(String text, String helper, String reason) {
    rejectedInclination = text;
    inclinationField.setColor(FormStyles.DANGER);
    setInclinationHelper(helper, FormStyles.DANGER);
    return reason;
  }

  private void clearInclinationRejection() {
    rejectedInclination = null;
    inclinationField.setColor(FormStyles.TEXT_PRIMARY);
  }

  /** Writes the helper line, skipping the no-op: this runs on every frame of the wizard. */
  private void setInclinationHelper(String text, ColorRGBA color) {
    if (!text.equals(inclinationHelper.getText())) {
      inclinationHelper.setText(text);
    }
    inclinationHelper.setColor(color);
  }

  /** The altitude the sun-synchronous inclination is derived from, in meters. */
  private double targetAltitude() {
    return perigeeSlider.getModel().getValue() * 1000.0;
  }

  @Override
  public double defaultHorizonDays() {
    // Semi-major axis of the target ellipse: Earth's radius plus the mean of the two altitudes.
    double meanAltitudeKm = 0.5 * (perigeeKm() + apogeeKm());
    return revolutionDays(
        MissionHorizon.DEFAULT_LEO_REVOLUTIONS,
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS + meanAltitudeKm * 1000.0);
  }

  private double perigeeKm() {
    return perigeeSlider.getModel().getValue();
  }

  /** On a circular profile the apogee is the perigee — there is only one slider to read. */
  private double apogeeKm() {
    return apogeeSlider == null ? perigeeKm() : apogeeSlider.getModel().getValue();
  }

  @Override
  public Map<String, Object> getDynamicValues() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.LEO_PERIGEE_ALT.key(), Math.round(perigeeKm()));
    values.put(FormField.LEO_APOGEE_ALT.key(), Math.round(apogeeKm()));
    // Published only when the inclination is an intention. Its absence is what makes a due-east
    // mission rebuild the plane from the latitude rather than from this field (spec §2.0).
    if (publishesInclination()) {
      values.put(FormField.TARGET_INCLINATION.key(), parsedInclinationDeg());
    }
    return values;
  }

  private boolean publishesInclination() {
    return switch (profile.inclinationMode()) {
      case NONE -> false;
      case AUTO -> !inclinationAuto;
      case EXPLICIT, DERIVED -> true;
    };
  }

  /**
   * The field's value, or the derived one when it cannot be read. An unreadable entry never reaches
   * here in practice — {@code validateTargetPlane()} holds the step — but degrading to the derived
   * value keeps a programmatic caller from building a spec out of {@code NaN}.
   */
  private double parsedInclinationDeg() {
    try {
      return Double.parseDouble(inclinationField.getText().trim());
    } catch (NumberFormatException e) {
      return profile.initialInclinationDeg(launchLatitudeDeg.getAsDouble(), targetAltitude());
    }
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    setSliderValue(
        perigeeSlider,
        perigeeSliderRef,
        perigeeField,
        FormValues.number(values, FormField.LEO_PERIGEE_ALT, perigeeKm()),
        altitudeMin,
        altitudeMax);
    if (apogeeSlider != null) {
      setSliderValue(
          apogeeSlider,
          apogeeSliderRef,
          apogeeField,
          FormValues.number(values, FormField.LEO_APOGEE_ALT, apogeeKm()),
          altitudeMin,
          altitudeMax);
    }
    applyInclination(values);
  }

  /**
   * Restores the inclination from previously published values. The key's absence is meaningful — it
   * means the mission was left on its site's free plane — so it puts the field back on auto rather
   * than freezing the number the previous edit happened to show.
   */
  private void applyInclination(Map<String, Object> values) {
    if (profile.inclinationMode() == MissionProfile.InclinationMode.NONE) {
      return;
    }
    Object raw = values.get(FormField.TARGET_INCLINATION.key());
    if (raw == null || raw.toString().isBlank()) {
      inclinationAuto = profile.inclinationMode() == MissionProfile.InclinationMode.AUTO;
      refreshDerivedInclination();
      return;
    }
    if (profile.inclinationMode() == MissionProfile.InclinationMode.DERIVED) {
      // The altitude already applied above decides it; a stored value would only be able to
      // disagree with it.
      refreshDerivedInclination();
      return;
    }
    double inclinationDeg;
    try {
      inclinationDeg = Double.parseDouble(raw.toString().trim());
    } catch (NumberFormatException e) {
      // A value map assembled by hand can carry anything. Falling back on the profile's own start
      // keeps the panel usable; validateTargetPlane() is what refuses an entry, not this.
      initialiseInclination();
      return;
    }
    inclinationAuto = false;
    clearInclinationRejection();
    inclinationField.setText(formatDegrees(inclinationDeg));
  }

  private static String formatKm(double km) {
    return Long.toString(Math.round(km));
  }

  private static String formatDegrees(double degrees) {
    return String.format(Locale.ROOT, "%.3f", degrees);
  }
}
