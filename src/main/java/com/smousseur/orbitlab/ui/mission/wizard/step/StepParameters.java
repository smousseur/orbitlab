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
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionContext;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.DynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.GEODynamicParameters;
import com.smousseur.orbitlab.ui.mission.wizard.step.params.LEODynamicParameters;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StepParameters implements StepValues {

  private static final float FIELD_W = 752f;
  private static final float FIELD_H = 36f;
  public static final float ROW_GAP = 16f;
  public static final float LABEL_FIELD_GAP = 6f;
  public static final float LABEL_ICON_SIZE = 14f;

  private final Container root;
  private final MissionContext missionContext;
  private final Label titleLabel;

  private final TextField missionNameField;
  private final TextField launchDateField;

  private DynamicParameters dynamicParameters;
  private final EnumMap<MissionType, DynamicParameters> dynamicParametersMap =
      new EnumMap<>(MissionType.class);

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
    dynamicParametersMap.put(MissionType.LEO, leoParams);
    dynamicParametersMap.put(MissionType.GEO, new GEODynamicParameters());
    dynamicParameters = leoParams;
    for (DynamicParameters params : dynamicParametersMap.values()) {
      params.setVisible(false);
      root.addChild(params.getContainer());
    }

    // --- Launch Date ---
    root.addChild(
        UiKit.fieldLabelRow("LAUNCH DATE", "lbl-clock", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    launchDateField = newInputField("2026-06-15T06:00:00Z", FIELD_W, FIELD_H);
    root.addChild(launchDateField);
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    Label helper = root.addChild(new Label("UTC · Orekit epoch", FormStyles.STYLE));
    helper.setFont(UiKit.ibmPlexMono(11));
    helper.setColor(FormStyles.TEXT_LO);

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

  public void update(float tpf) {
    MissionType selectedMissionType = missionContext.getSelectedMissionType();
    titleLabel.setText("PARAMETERS " + selectedMissionType);
    updateDynamicParameters(tpf);
  }

  private void updateDynamicParameters(float tpf) {
    MissionType selectedMissionType = missionContext.getSelectedMissionType();
    dynamicParametersMap.values().forEach(params -> params.setVisible(false));
    dynamicParameters =
        Optional.ofNullable(dynamicParametersMap.get(selectedMissionType))
            .orElseThrow(
                () ->
                    new OrbitlabException(
                        "No dynamic parameters for mission type " + selectedMissionType));
    dynamicParameters.update(tpf);
    dynamicParameters.setVisible(true);
  }
}
