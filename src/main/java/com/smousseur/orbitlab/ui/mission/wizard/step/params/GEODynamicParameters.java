package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import static com.smousseur.orbitlab.ui.UiKit.fieldLabelRow;
import static com.smousseur.orbitlab.ui.UiKit.newInputField;
import static com.smousseur.orbitlab.ui.mission.wizard.step.StepParameters.*;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.event.CursorEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;

import java.util.Map;

public class GEODynamicParameters extends DynamicParameters {
  public static final int GEO_ALTITUDE = 35_786;
  private static final int DEFAULT_PARKING = 400;

  private final double altitudeMin;
  private final double altitudeMax;

  private final Slider parkingSlider;
  private final TextField parkingField;
  private final VersionedReference<Double> parkingSliderRef;
  private boolean parkingFieldWasFocused;

  private TextField geoAltitudeField;

  public GEODynamicParameters(double altitudeMin, double altitudeMax) {
    this.altitudeMin = altitudeMin;
    this.altitudeMax = altitudeMax;
    parkingField =
        new TextField(Long.toString(Math.round((double) DEFAULT_PARKING)), FormStyles.STYLE);
    parkingSlider = buildSlider(altitudeMin, altitudeMax, DEFAULT_PARKING);
    parkingSliderRef = parkingSlider.getModel().createReference();
    this.container = createContainer();
  }

  @Override
  protected Container createContainer() {
    Container parameters = new Container(new BoxLayout(Axis.Y, FillMode.Even));
    parameters.addChild(
        getSliderContainer(
            "PARKING ALTITUDE", parkingSlider, parkingField, altitudeMin, altitudeMax));

    parameters.addChild(
        fieldLabelRow("GEO ALTITUDE", "lbl-globe", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    parameters.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    geoAltitudeField = newInputField(GEO_ALTITUDE + " km", 100, FIELD_H);
    UiKit.makeReadOnly(geoAltitudeField);
    parameters.addChild(geoAltitudeField);

    return parameters;
  }

  @Override
  public void update(float tpf) {
    parkingFieldWasFocused =
        updateSlider(
            parkingSlider,
            parkingSliderRef,
            parkingField,
            parkingFieldWasFocused,
            altitudeMin,
            altitudeMax);
  }

  @Override
  public Map<String, Object> getDynamicValues() {
    return Map.of(FormField.GTO_PARKING_ALT.key(), Math.round(parkingSlider.getModel().getValue()));
  }
}
