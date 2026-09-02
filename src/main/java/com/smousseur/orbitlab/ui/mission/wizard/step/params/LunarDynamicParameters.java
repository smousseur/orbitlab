package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.simulation.mission.operation.LunarFlybyMission;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowRequest;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import com.smousseur.orbitlab.ui.mission.wizard.SiteCoordinates;
import com.smousseur.orbitlab.ui.mission.wizard.step.planning.PlanningInputs;
import java.util.Map;

/**
 * The lunar panel: <b>one slider</b>, the perilune altitude (MIS-4 / L5 §3).
 *
 * <p>The shortest panel of the package, and the only one with no perigee/apogee pair. There is no
 * inclination field either — the chain flies {@code i = φ}, where the two azimuth branches merge,
 * and {@code MissionSpec.Lunar} carries no inclination — and no parking altitude, which is the
 * mission's own constant rather than a choice.
 */
public class LunarDynamicParameters extends DynamicParameters {

  /**
   * The derived mission duration, in days. <b>Written out rather than derived, and that is an
   * inherited constraint rather than a shortcut</b>: {@link DynamicParameters#revolutionDays}
   * carries an Earth µ, and MIS-4 / L0 §7 pt 4 recorded that it stays off this path <em>only</em>
   * while the lunar profile keeps a fixed-duration horizon. Seven days is what {@code
   * MissionHorizon.defaultFor(LUNAR_FLYBY)} holds, and what the closure flight of L4 flew.
   */
  private static final double HORIZON_DAYS = 7.0;

  private final double altitudeMin;
  private final double altitudeMax;

  private final Slider periluneSlider;
  private final TextField periluneField;
  private final VersionedReference<Double> periluneSliderRef;
  private boolean periluneFieldWasFocused;

  /**
   * Builds the panel on a profile's own band.
   *
   * @param band the perilune band, in kilometres
   */
  public LunarDynamicParameters(MissionProfile.AltitudeRange band) {
    this.altitudeMin = band.minKm();
    this.altitudeMax = band.maxKm();
    periluneField = new TextField(Long.toString(Math.round(band.defaultKm())), FormStyles.STYLE);
    periluneSlider = buildSlider(altitudeMin, altitudeMax, band.defaultKm());
    periluneSliderRef = periluneSlider.getModel().createReference();
    this.container = createContainer();
  }

  @Override
  protected final Container createContainer() {
    Container parameters = new Container(new BoxLayout(Axis.Y, FillMode.Even));
    parameters.addChild(
        getSliderContainer(
            "PERILUNE ALTITUDE", periluneSlider, periluneField, altitudeMin, altitudeMax));
    return parameters;
  }

  @Override
  public void update(float tpf) {
    periluneFieldWasFocused =
        updateSlider(
            periluneSlider,
            periluneSliderRef,
            periluneField,
            periluneFieldWasFocused,
            altitudeMin,
            altitudeMax);
  }

  @Override
  public double defaultHorizonDays() {
    return HORIZON_DAYS;
  }

  /**
   * The screening window this panel describes (MIS-4 / L5 §4.1).
   *
   * <p><b>The node is ignored, and it is not a field of this card.</b> A lunar mission has a launch
   * window without having a target node: what it waits for is a direction its parking plane must
   * contain, not a plane whose ascending node it must meet.
   */
  @Override
  public PlanningInputs windowInputs(SiteCoordinates site, Double raanDeg) {
    return PlanningInputs.of(
        new LunarLaunchWindowRequest(
            site.latitude(),
            site.longitude(),
            site.altitude(),
            LunarFlybyMission.DEFAULT_PARKING_ALTITUDE,
            periluneSlider.getModel().getValue() * 1000.0));
  }

  @Override
  public Map<String, Object> getDynamicValues() {
    return Map.of(
        FormField.LUNAR_PERILUNE_ALT.key(), Math.round(periluneSlider.getModel().getValue()));
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    setSliderValue(
        periluneSlider,
        periluneSliderRef,
        periluneField,
        FormValues.number(
            values, FormField.LUNAR_PERILUNE_ALT, periluneSlider.getModel().getValue()),
        altitudeMin,
        altitudeMax);
  }
}
