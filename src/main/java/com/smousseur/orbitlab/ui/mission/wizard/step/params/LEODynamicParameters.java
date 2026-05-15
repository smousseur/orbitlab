package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import java.util.Map;

public class LEODynamicParameters extends DynamicParameters {
  private static final int DEFAULT_ATITUDE = 550;

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

    perigeeField =
        new TextField(Long.toString(Math.round((double) DEFAULT_ATITUDE)), FormStyles.STYLE);
    perigeeSlider = buildSlider(altitudeMin, altitudeMax, DEFAULT_ATITUDE);
    perigeeSliderRef = perigeeSlider.getModel().createReference();
    apogeeField =
        new TextField(Long.toString(Math.round((double) DEFAULT_ATITUDE)), FormStyles.STYLE);
    apogeeSlider = buildSlider(altitudeMin, altitudeMax, DEFAULT_ATITUDE);
    apogeeSliderRef = apogeeSlider.getModel().createReference();

    this.container = createContainer();
  }

  @Override
  protected Container createContainer() {
    Container parameters = new Container(new BoxLayout(Axis.Y, FillMode.Even));
    parameters.addChild(getSliderContainer("PERIGEE ALTITUDE", perigeeSlider, perigeeField));
    parameters.addChild(getSliderContainer("APOGEE ALTITUDE", apogeeSlider, apogeeField));
    return parameters;
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
    apogeeFieldWasFocused =
        updateSlider(
            apogeeSlider,
            apogeeSliderRef,
            apogeeField,
            apogeeFieldWasFocused,
            altitudeMin,
            altitudeMax);
  }

  @Override
  public Map<String, Object> getDynamicValues() {
    return Map.of(
        FormField.LEO_PERIGEE_ALT.key(), Math.round(perigeeSlider.getModel().getValue()),
        FormField.LEO_APOGEE_ALT.key(), Math.round(apogeeSlider.getModel().getValue()));
  }
}
