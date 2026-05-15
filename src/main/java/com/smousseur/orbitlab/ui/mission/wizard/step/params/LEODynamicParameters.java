package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;

import java.util.Map;

import static com.smousseur.orbitlab.ui.mission.wizard.step.StepParameters.*;

public class LEODynamicParameters extends DynamicParameters {
  private static final float FIELD_W = 752f;
  private static final float SLIDER_ROW_H = 32f;
  private static final float SLIDER_TRACK_H = 2f;
  private static final float VALUE_FIELD_W = 80f;
  private static final float KM_LABEL_W = 22f;
  private static final float SLIDER_TEXT_GAP = 8f;
  private static final float SLIDER_W = FIELD_W - VALUE_FIELD_W - KM_LABEL_W - 2 * SLIDER_TEXT_GAP;

  private final double altitudeMin;
  private final double altitudeMax;

  private final Slider perigeeSlider;
  private final TextField perigeeField;
  private final VersionedReference<Double> perigeeSliderRef;
  private boolean perigeeFieldWasFocused;

  private final Slider apogeeSlider;
  private final TextField apogeeField;
  private final VersionedReference<Double> apogeeSliderRef;
  private boolean apogeeFieldWasFocused;

  public LEODynamicParameters(double altitudeMin, double altitudeMax) {
    super();
    this.altitudeMin = altitudeMin;
    this.altitudeMax = altitudeMax;

    perigeeField = new TextField(Long.toString(Math.round((double) 550)), FormStyles.STYLE);
    perigeeSlider = buildSlider();
    perigeeSliderRef = perigeeSlider.getModel().createReference();
    apogeeField = new TextField(Long.toString(Math.round((double) 550)), FormStyles.STYLE);
    apogeeSlider = buildSlider();
    apogeeSliderRef = apogeeSlider.getModel().createReference();

    this.container = createContainer();
  }

  @Override
  protected Container createContainer() {
    Container parameters = new Container(new BoxLayout(Axis.Y, FillMode.Even));
    parameters.addChild(getPeriapsisContainer("PERIGEE ALTITUDE", perigeeSlider, perigeeField));
    parameters.addChild(getPeriapsisContainer("APOGEE ALTITUDE", apogeeSlider, apogeeField));
    return parameters;
  }

  private Container getPeriapsisContainer(
      String label, Slider periapsisSlider, TextField periapsisfield) {
    Container perigeeContainer = new Container();
    perigeeContainer.addChild(
        UiKit.fieldLabelRow(label, "lbl-ruler", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    perigeeContainer.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    perigeeContainer.addChild(buildSliderRow(periapsisSlider, periapsisfield));
    perigeeContainer.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    perigeeContainer.addChild(rangeBoundsRow());
    perigeeContainer.addChild(UiKit.vSpacer(ROW_GAP));
    return perigeeContainer;
  }

  @Override
  public void update(float tpf) {
    perigeeFieldWasFocused =
        updateSlider(perigeeSlider, perigeeSliderRef, perigeeField, perigeeFieldWasFocused);
    apogeeFieldWasFocused =
        updateSlider(apogeeSlider, apogeeSliderRef, apogeeField, apogeeFieldWasFocused);
  }

  private boolean updateSlider(
      Slider slider,
      VersionedReference<Double> sliderRef,
      TextField field,
      boolean isFieldFocused) {
    if (slider == null || field == null) return false;

    if (sliderRef.update()) {
      field.setText(Long.toString(Math.round(slider.getModel().getValue())));
    }

    Spatial focused = GuiGlobals.getInstance().getFocusManagerState().getFocus();
    boolean isFocused = focused == field;
    if (isFieldFocused && !isFocused) {
      commitAltitudeFieldToSlider(slider, sliderRef, field);
    }

    return isFocused;
  }

  private void commitAltitudeFieldToSlider(
      Slider slider, VersionedReference<Double> sliderRef, TextField field) {
    try {
      double v = Double.parseDouble(field.getText().trim());
      v = Math.max(altitudeMin, Math.min(altitudeMax, v));
      slider.getModel().setValue(v);
      sliderRef.update();
      field.setText(Long.toString(Math.round(v)));
    } catch (NumberFormatException e) {
      field.setText(Long.toString(Math.round(slider.getModel().getValue())));
    }
  }

  private Container rangeBoundsRow() {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    Label min = row.addChild(new Label("160 km", FormStyles.STYLE));
    min.setFont(UiKit.ibmPlexMono(11));
    min.setColor(FormStyles.TEXT_LO);

    Label max = new Label("2 000 km", FormStyles.STYLE);
    max.setFont(UiKit.ibmPlexMono(11));
    max.setColor(FormStyles.TEXT_LO);

    float used = min.getPreferredSize().x + max.getPreferredSize().x;
    float trailing = Math.max(0f, SLIDER_W - used);
    row.addChild(UiKit.hSpacer(trailing));
    row.addChild(max);
    return row;
  }

  @Override
  public Map<String, Object> getDynamicValues() {
    return Map.of(
        FormField.LEO_PERIGEE_ALT.key(), Math.round(perigeeSlider.getModel().getValue()),
        FormField.LEO_APOGEE_ALT.key(), Math.round(apogeeSlider.getModel().getValue()));
  }

  private Container buildSliderRow(Slider slider, TextField perigeeField) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.setPreferredSize(new Vector3f(FIELD_W, SLIDER_ROW_H, 0));

    float vPad = (SLIDER_ROW_H - SLIDER_TRACK_H) * 0.5f;
    Container sliderWrap = new Container(new BoxLayout(Axis.Y, FillMode.None));
    sliderWrap.setBackground(null);
    sliderWrap.setPreferredSize(new Vector3f(SLIDER_W, SLIDER_ROW_H, 0));
    sliderWrap.addChild(UiKit.vSpacer(vPad));
    sliderWrap.addChild(slider);
    sliderWrap.addChild(UiKit.vSpacer(vPad));
    row.addChild(sliderWrap);

    row.addChild(UiKit.hSpacer(SLIDER_TEXT_GAP));

    perigeeField.setFont(UiKit.ibmPlexMono(11));
    perigeeField.setPreferredSize(new Vector3f(VALUE_FIELD_W, SLIDER_ROW_H, 0));
    perigeeField.setInsets(new Insets3f(-7, 0, 10, 0));
    perigeeField.setInsetsComponent(new InsetsComponent(new Insets3f(3, 10, 0, 0)));
    row.addChild(perigeeField);

    row.addChild(UiKit.hSpacer(SLIDER_TEXT_GAP));

    Label km = row.addChild(new Label("km", FormStyles.STYLE));
    km.setFont(UiKit.ibmPlexMono(11));
    km.setColor(FormStyles.TEXT_LO);

    return row;
  }
}
