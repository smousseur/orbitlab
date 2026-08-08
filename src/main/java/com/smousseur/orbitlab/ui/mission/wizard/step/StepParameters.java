package com.smousseur.orbitlab.ui.mission.wizard.step;

import static com.smousseur.orbitlab.ui.UiKit.fieldLabelRow;
import static com.smousseur.orbitlab.ui.UiKit.newInputField;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.*;
import com.smousseur.orbitlab.app.OrekitTime;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.ui.EphemerisWindow;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.DynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.GEODynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.LEODynamicParameters;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

public class StepParameters implements StepValues {

  private static final float FIELD_W = 752f;
  public static final float FIELD_H = 36f;
  public static final float ROW_GAP = 16f;
  public static final float LABEL_FIELD_GAP = 6f;
  public static final float LABEL_ICON_SIZE = 14f;

  private static final String LAUNCH_DATE_HELPER = "UTC · Orekit epoch";
  private static final String LAUNCH_DATE_FORMAT_HELPER =
      "format attendu : yyyy-MM-dd HH:mm:ss (UTC)";

  private static final float HORIZON_FIELD_W = 120f;
  private static final float HORIZON_BUTTON_W = 76f;

  /** Shortest mission the horizon field accepts: one minute, expressed in days. */
  private static final double HORIZON_MIN_DAYS = 1.0 / 1440.0;

  /** Longest mission the horizon field accepts — the cap the model enforces anyway. */
  private static final double HORIZON_MAX_DAYS =
      MissionHorizon.MAX_COAST_SECONDS / MissionHorizon.SECONDS_PER_DAY;

  private static final String HORIZON_FORMAT_HELPER =
      "durée attendue : un nombre de jours entre 1 min et " + (long) HORIZON_MAX_DAYS + " j";

  private static final String HORIZON_MANUAL_HELPER = "durée totale, depuis le décollage";

  private final Container root;
  private final MissionContext missionContext;
  private final Label titleLabel;

  private final TextField missionNameField;
  private final TextField launchDateField;
  private final Label launchDateHelper;

  private final TextField horizonField;
  private final Label horizonHelper;

  /**
   * Whether the duration field still shows the derived default. This is the whole of the "auto"
   * state: while it holds, {@link #getValues()} omits the key entirely, which is what makes the
   * wizard's auto mode survive a round-trip through the raw value map without a flag of its own
   * (spec {@code specs/mission-horizon/01-horizon-explicite.md} §7).
   */
  private boolean horizonAuto = true;

  /** The last text this step wrote into the duration field, to tell a prefill from a user edit. */
  private String lastAutoHorizonText = "";

  /** Entry that was refused, kept so the error state clears as soon as it is edited. */
  private String rejectedLaunchDate;

  /** Same, for the duration field. */
  private String rejectedHorizon;

  private DynamicParameters dynamicParameters;
  private final EnumMap<MissionType, DynamicParameters> dynamicParametersMap =
      new EnumMap<>(MissionType.class);
  private final Container dynamicParametersContainer;
  private MissionType shownMissionType;

  public StepParameters(MissionContext missionContext) {
    this.missionContext = missionContext;
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0)));
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

    titleLabel =
        new Label("PARAMETERS " + missionContext.getSelectedMissionType(), FormStyles.STYLE);
    Label title = root.addChild(titleLabel);
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(6));

    Label subtitle = root.addChild(new Label("// target orbit configuration", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Mission Name ---
    root.addChild(fieldLabelRow("MISSION NAME", "lbl-edit", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    missionNameField = newInputField("ORBITLAB-LEO-001", FIELD_W, FIELD_H);
    root.addChild(missionNameField);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Dynamic parameters ---
    LEODynamicParameters leoParams = new LEODynamicParameters(200, 2000);
    GEODynamicParameters geoParams = new GEODynamicParameters(200, 2000);
    dynamicParametersMap.put(MissionType.LEO, leoParams);
    dynamicParametersMap.put(MissionType.GEO, geoParams);
    dynamicParameters = leoParams;
    dynamicParametersContainer = new Container(new BoxLayout(Axis.Y, FillMode.None));
    dynamicParametersContainer.setBackground(null);
    root.addChild(dynamicParametersContainer);

    // --- Launch Date ---
    root.addChild(
        UiKit.fieldLabelRow("LAUNCH DATE", "lbl-clock", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    launchDateField = newInputField(OrekitTime.utcNowString(), FIELD_W, FIELD_H);
    root.addChild(launchDateField);
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    launchDateHelper = root.addChild(new Label(LAUNCH_DATE_HELPER, FormStyles.STYLE));
    launchDateHelper.setFont(UiKit.ibmPlexMono(11));
    launchDateHelper.setColor(FormStyles.TEXT_LO);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // --- Mission duration (restitution horizon) ---
    root.addChild(
        UiKit.fieldLabelRow("MISSION DURATION", "lbl-clock", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    horizonField = newInputField("", HORIZON_FIELD_W, FIELD_H);
    root.addChild(buildHorizonRow());
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    horizonHelper = root.addChild(new Label("", FormStyles.STYLE));
    horizonHelper.setFont(UiKit.ibmPlexMono(11));
    horizonHelper.setColor(FormStyles.TEXT_LO);

    for (DynamicParameters params : dynamicParametersMap.values()) {
      CursorEventControl.addListenersToSpatial(
          params.getContainer(),
          new DefaultCursorListener() {
            @Override
            public void cursorButtonEvent(
                CursorButtonEvent event, Spatial target, Spatial capture) {
              if (event.getButtonIndex() == 0 && event.isPressed()) {
                Spatial currentFocus = GuiGlobals.getInstance().getFocusManagerState().getFocus();
                if (currentFocus != null && target != currentFocus) {
                  if (!(target instanceof TextField)) {
                    GuiGlobals.getInstance().requestFocus(null);
                  }
                }
              }
            }
          });
    }
    updateDynamicParameters(0);
    refreshHorizonFromDerived();
  }

  /** The duration field, its unit and the button that hands the value back to the derived default. */
  private Container buildHorizonRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(horizonField);
    row.addChild(UiKit.hSpacer(8f));

    Label unit = row.addChild(new Label("jours", FormStyles.STYLE));
    unit.setFont(UiKit.ibmPlexMono(11));
    unit.setColor(FormStyles.TEXT_LO);

    row.addChild(UiKit.hSpacer(16f));

    Button auto = new Button("AUTO", FormStyles.STYLE);
    auto.setPreferredSize(new Vector3f(HORIZON_BUTTON_W, FIELD_H, 0));
    auto.setFont(UiKit.sora(12));
    auto.setBackground(UiKit.wizardBg9("btn-ghost", 8));
    auto.addClickCommands(src -> resetHorizonToDerived());
    row.addChild(auto);

    return row;
  }

  /** Hands the duration back to the derived policy, clearing any refused entry. */
  private void resetHorizonToDerived() {
    horizonAuto = true;
    clearHorizonRejection();
    refreshHorizonFromDerived();
  }

  /**
   * Keeps the duration field in step with the target orbit while it is on auto, and notices the
   * first keystroke that takes it off auto.
   *
   * <p>The auto state needs no widget of its own: the field's text either is what this step last
   * wrote, or it is not.
   */
  private void updateHorizon() {
    if (horizonAuto && !horizonField.getText().equals(lastAutoHorizonText)) {
      // The user typed over the prefill. From here the value is theirs until AUTO is pressed.
      horizonAuto = false;
    }
    if (horizonAuto) {
      refreshHorizonFromDerived();
      return;
    }
    if (rejectedHorizon != null) {
      if (!rejectedHorizon.equals(horizonField.getText())) {
        clearHorizonRejection();
      }
      return;
    }
    setHorizonHelper(HORIZON_MANUAL_HELPER, FormStyles.TEXT_LO);
  }

  /** Writes the derived duration into the field and describes it in the helper line. */
  private void refreshHorizonFromDerived() {
    String text = formatDays(dynamicParameters.defaultHorizonDays());
    if (!text.equals(horizonField.getText())) {
      horizonField.setText(text);
    }
    lastAutoHorizonText = text;
    setHorizonHelper(
        "auto · " + MissionHorizon.defaultFor(missionContext.getSelectedMissionType()).describe(),
        FormStyles.TEXT_LO);
  }

  private void setHorizonHelper(String text, ColorRGBA color) {
    horizonHelper.setText(text);
    horizonHelper.setColor(color);
  }

  private static String formatDays(double days) {
    return String.format(java.util.Locale.ROOT, "%.2f", days);
  }

  public Container getNode() {
    return root;
  }

  @Override
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    values.put(FormField.MISSION_NAME.key(), missionNameField.getText());
    values.putAll(dynamicParameters.getDynamicValues());
    values.put(FormField.LAUNCH_DATE.key(), launchDateField.getText());
    // Published only when overridden: an absent key IS the auto state, which is what lets a mission
    // reopened in the wizard come back on auto without a second key to carry it.
    if (!horizonAuto) {
      values.put(FormField.MISSION_HORIZON_DAYS.key(), horizonField.getText());
    }
    return values;
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    String name = FormValues.string(values, FormField.MISSION_NAME);
    if (name != null) {
      missionNameField.setText(name);
    }
    String launchDate = FormValues.string(values, FormField.LAUNCH_DATE);
    if (launchDate != null) {
      launchDateField.setText(launchDate);
      clearLaunchDateRejection();
    }
    // Applied to the parameters of the type carried by the values, not to the ones currently on
    // screen: the panel is swapped by update(), which has not necessarily run yet.
    DynamicParameters target = dynamicParametersMap.get(missionTypeOf(values));
    if (target != null) {
      target.applyValues(values);
    }
    applyHorizon(values);
  }

  /**
   * Restores the duration field from previously published values. The key's absence is meaningful —
   * it means the mission was left on the derived default — so it puts the field back on auto rather
   * than leaving whatever the previous edit had shown.
   */
  private void applyHorizon(Map<String, Object> values) {
    Object raw = values.get(FormField.MISSION_HORIZON_DAYS.key());
    if (raw == null || raw.toString().isBlank()) {
      resetHorizonToDerived();
      return;
    }
    horizonAuto = false;
    clearHorizonRejection();
    horizonField.setText(raw.toString().trim());
  }

  /** Reads the mission type out of the raw values, falling back on the one the context selects. */
  private MissionType missionTypeOf(Map<String, Object> values) {
    String raw = FormValues.string(values, FormField.MISSION_TYPE);
    if (raw == null) {
      return missionContext.getSelectedMissionType();
    }
    try {
      return MissionType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return missionContext.getSelectedMissionType();
    }
  }

  public void update(float tpf) {
    MissionType selectedMissionType = missionContext.getSelectedMissionType();
    titleLabel.setText("PARAMETERS " + selectedMissionType);
    if (rejectedLaunchDate != null && !rejectedLaunchDate.equals(launchDateField.getText())) {
      clearLaunchDateRejection();
    }
    updateDynamicParameters(tpf);
    // After the panel swap, so the derived duration is read off the parameters now on screen.
    updateHorizon();
  }

  /**
   * Checks the launch date and marks the field when it cannot be used, so a bad entry is caught
   * while the wizard is still open rather than swallowed at mission creation.
   *
   * <p>Accepts the same entries as the timeline date field, including the ISO form this very field
   * is prefilled with.
   *
   * @return the reason the date was refused, or empty when it is usable
   */
  public Optional<String> validateLaunchDate() {
    String text = launchDateField.getText();
    Optional<AbsoluteDate> parsed = TimeConverter.parseUtcDate(text);
    if (parsed.isEmpty()) {
      return Optional.of(rejectLaunchDate(text, LAUNCH_DATE_FORMAT_HELPER));
    }
    if (!EphemerisWindow.covers(parsed.get())) {
      return Optional.of(
          rejectLaunchDate(
              text, "hors couverture ephemeride : " + EphemerisWindow.rangeLabel().orElse("")));
    }
    clearLaunchDateRejection();
    return Optional.empty();
  }

  private String rejectLaunchDate(String text, String message) {
    rejectedLaunchDate = text;
    launchDateField.setColor(FormStyles.DANGER);
    launchDateHelper.setText(message);
    launchDateHelper.setColor(FormStyles.DANGER);
    return message;
  }

  private void clearLaunchDateRejection() {
    rejectedLaunchDate = null;
    launchDateField.setColor(FormStyles.TEXT_PRIMARY);
    launchDateHelper.setText(LAUNCH_DATE_HELPER);
    launchDateHelper.setColor(FormStyles.TEXT_LO);
  }

  /**
   * Checks the mission duration and marks the field when it cannot be used, on the same contract as
   * {@link #validateLaunchDate()}: caught while the wizard is still open rather than degraded
   * silently to the derived default at mission creation.
   *
   * <p>Always accepts an untouched field: the derived default is by construction within bounds.
   *
   * @return the reason the duration was refused, or empty when it is usable
   */
  public Optional<String> validateHorizon() {
    if (horizonAuto) {
      clearHorizonRejection();
      return Optional.empty();
    }
    String text = horizonField.getText().trim();
    double days;
    try {
      days = Double.parseDouble(text);
    } catch (NumberFormatException e) {
      return Optional.of(rejectHorizon(text, HORIZON_FORMAT_HELPER));
    }
    if (!Double.isFinite(days) || days < HORIZON_MIN_DAYS || days > HORIZON_MAX_DAYS) {
      return Optional.of(rejectHorizon(text, HORIZON_FORMAT_HELPER));
    }
    clearHorizonRejection();
    return Optional.empty();
  }

  private String rejectHorizon(String text, String message) {
    rejectedHorizon = text;
    horizonField.setColor(FormStyles.DANGER);
    setHorizonHelper(message, FormStyles.DANGER);
    return message;
  }

  private void clearHorizonRejection() {
    rejectedHorizon = null;
    horizonField.setColor(FormStyles.TEXT_PRIMARY);
    // The neutral line; on auto, the next updateHorizon() replaces it with the derived description.
    setHorizonHelper(HORIZON_MANUAL_HELPER, FormStyles.TEXT_LO);
  }

  private void updateDynamicParameters(float tpf) {
    MissionType selectedMissionType = missionContext.getSelectedMissionType();
    if (selectedMissionType != shownMissionType) {
      DynamicParameters next =
          Optional.ofNullable(dynamicParametersMap.get(selectedMissionType))
              .orElseThrow(
                  () ->
                      new OrbitlabException(
                          "No dynamic parameters for mission type " + selectedMissionType));
      dynamicParametersContainer.clearChildren();
      dynamicParametersContainer.addChild(next.getContainer());
      dynamicParameters = next;
      shownMissionType = selectedMissionType;
    }
    dynamicParameters.update(tpf);
  }
}
