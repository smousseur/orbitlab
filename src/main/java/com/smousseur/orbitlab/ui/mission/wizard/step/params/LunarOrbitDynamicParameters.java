package com.smousseur.orbitlab.ui.mission.wizard.step.params;

import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.operation.LunarOrbitMission;
import com.smousseur.orbitlab.simulation.mission.window.problem.LunarLaunchWindowRequest;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import com.smousseur.orbitlab.ui.mission.wizard.SiteCoordinates;
import com.smousseur.orbitlab.ui.mission.wizard.step.planning.PlanningInputs;
import java.util.Map;

/**
 * The lunar orbit panel: <b>one slider</b>, the circular orbit altitude (MIS-5 / L7 §3).
 *
 * <p>Shaped after {@link LunarDynamicParameters}, and for the same reasons: no perigee/apogee pair
 * on a target that is circular by construction, no inclination — the chain flies {@code i = φ} and
 * the plane reached around the Moon is undergone rather than aimed at — and no parking altitude,
 * which is {@link LunarOrbitMission#DEFAULT_PARKING_ALTITUDE} rather than a choice.
 */
public class LunarOrbitDynamicParameters extends DynamicParameters {

  private final double altitudeMin;
  private final double altitudeMax;

  private final Slider altitudeSlider;
  private final TextField altitudeField;
  private final VersionedReference<Double> altitudeSliderRef;
  private boolean altitudeFieldWasFocused;

  /**
   * Builds the panel on a profile's own band.
   *
   * @param band the lunar orbit band, in kilometres
   */
  public LunarOrbitDynamicParameters(MissionProfile.AltitudeRange band) {
    this.altitudeMin = band.minKm();
    this.altitudeMax = band.maxKm();
    altitudeField = new TextField(Long.toString(Math.round(band.defaultKm())), FormStyles.STYLE);
    altitudeSlider = buildSlider(altitudeMin, altitudeMax, band.defaultKm());
    altitudeSliderRef = altitudeSlider.getModel().createReference();
    this.container = createContainer();
  }

  @Override
  protected final Container createContainer() {
    Container parameters = new Container(new BoxLayout(Axis.Y, FillMode.Even));
    parameters.addChild(
        getSliderContainer(
            "LUNAR ORBIT ALTITUDE", altitudeSlider, altitudeField, altitudeMin, altitudeMax));
    return parameters;
  }

  @Override
  public void update(float tpf) {
    altitudeFieldWasFocused =
        updateSlider(
            altitudeSlider,
            altitudeSliderRef,
            altitudeField,
            altitudeFieldWasFocused,
            altitudeMin,
            altitudeMax);
  }

  @Override
  public double defaultHorizonDays() {
    return horizonDays(altitudeSlider.getModel().getValue() * 1000.0);
  }

  /**
   * The mission duration a lunar orbit at {@code altitudeMeters} implies, in days: the transfer
   * plus the twelve revolutions of the profile.
   *
   * <p><b>The transfer is added, and the two Earth panels add nothing.</b> That is not an
   * inconsistency but the same rule applied where it bites: what the duration field writes is a
   * {@code MissionHorizon.FixedDuration}, whose contract is {@code seconds − ascent} — a
   * <em>total</em> measured from lift-off. For a low orbit the ascent lasts minutes and the
   * distinction is invisible; here the transfer lasts four days against one day of final coast, so
   * publishing the revolutions alone would show 0.98 where the mission runs 4.98 — and a user
   * confirming that number would create a mission that ends before reaching the Moon.
   *
   * <p>Static and package-private so the arithmetic can be pinned without an initialised {@code
   * AssetManager}, the same split {@code RefusedPage} takes out of its own step.
   *
   * @param altitudeMeters the circular lunar orbit altitude, in metres above the lunar surface
   * @return the derived mission duration in days
   */
  static double horizonDays(double altitudeMeters) {
    return TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS / MissionHorizon.SECONDS_PER_DAY
        + revolutionDays(
            MissionHorizon.DEFAULT_LUNAR_ORBIT_REVOLUTIONS,
            altitudeMeters,
            GravitationalContext.moon());
  }

  /**
   * The screening window this panel describes.
   *
   * <p>The flyby's own criterion, on the flyby's own reasoning: what a lunar mission waits for is a
   * direction its parking plane must contain, not a plane whose ascending node it must meet, so the
   * node is ignored and is not a field of this card. The aimed perilune <em>is</em> the lunar orbit
   * altitude — the insertion burns at the periapsis of the arrival hyperbola (MIS-5 / L5 §5.2).
   */
  @Override
  public PlanningInputs windowInputs(SiteCoordinates site, Double raanDeg) {
    return PlanningInputs.of(
        new LunarLaunchWindowRequest(
            site.latitude(),
            site.longitude(),
            site.altitude(),
            LunarOrbitMission.DEFAULT_PARKING_ALTITUDE,
            altitudeSlider.getModel().getValue() * 1000.0));
  }

  @Override
  public Map<String, Object> getDynamicValues() {
    return Map.of(
        FormField.LUNAR_ORBIT_ALT.key(), Math.round(altitudeSlider.getModel().getValue()));
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    setSliderValue(
        altitudeSlider,
        altitudeSliderRef,
        altitudeField,
        FormValues.number(values, FormField.LUNAR_ORBIT_ALT, altitudeSlider.getModel().getValue()),
        altitudeMin,
        altitudeMax);
  }
}
