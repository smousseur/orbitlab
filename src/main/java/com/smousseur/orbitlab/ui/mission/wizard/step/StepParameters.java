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

  private final Container root;
  private final MissionContext missionContext;
  private final Label titleLabel;

  private final TextField missionNameField;
  private final TextField launchDateField;
  private final Label launchDateHelper;

  /** Entry that was refused, kept so the error state clears as soon as it is edited. */
  private String rejectedLaunchDate;

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
