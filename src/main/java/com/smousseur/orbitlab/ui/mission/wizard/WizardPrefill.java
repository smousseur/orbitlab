package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hipparchus.util.FastMath;

/**
 * Turns a mission back into the wizard form values it came from, so the wizard can reopen on an
 * existing mission. The exact inverse of {@code MissionFactory.specFromWizardValues}: altitudes go
 * back to kilometers, catalog models back to their ids, and the launch date back to the text format
 * the parameters step accepts.
 *
 * <p>Only what the wizard owns is reproduced. Everything the spec derived on its own — propellant
 * loads sized by {@code PropellantBudget}, the AKM load, the composed stages — is deliberately left
 * out: validating the edit sizes it all again from the values on screen.
 */
public final class WizardPrefill {

  private WizardPrefill() {}

  /**
   * Builds the prefill values for a mission.
   *
   * @param entry the mission entry to reopen the wizard on
   * @return the values keyed by {@link FormField#key()}, ready for {@link
   *     MissionWizardWidget#MissionWizardWidget(com.smousseur.orbitlab.app.ApplicationContext,
   *     Map)}
   * @throws IllegalArgumentException if the entry carries no spec — a legacy entry has no wizard
   *     values to go back to, which is why the roster does not offer to edit it
   */
  public static Map<String, Object> fromEntry(MissionEntry entry) {
    MissionSpec spec =
        entry
            .spec()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Mission ["
                            + entry.id().shortForm()
                            + "] carries no spec to prefill from"));

    Map<String, Object> values = new LinkedHashMap<>();
    values.put(FormField.MISSION_TYPE.key(), spec.type().name());
    // Derived, not read back: the profile is a way of offering parameters, so no spec component
    // carries it (spec docs/earth-orbit/02-wizard-orbites-terrestres.md §2.1).
    values.put(FormField.MISSION_PROFILE.key(), MissionProfile.of(spec).name());
    values.put(FormField.MISSION_NAME.key(), spec.name());
    // Absent when the mission was never scheduled: the field then keeps its "now" default rather
    // than showing a date the mission does not have.
    entry
        .getScheduledDate()
        .ifPresent(date -> values.put(FormField.LAUNCH_DATE.key(), TimeConverter.formatDate(date)));

    if (spec.hasSiteName()) {
      values.put(FormField.LAUNCH_SITE_NAME.key(), spec.siteName());
    }
    values.put(FormField.LAUNCH_SITE_LAT.key(), spec.latitude());
    values.put(FormField.LAUNCH_SITE_LONG.key(), spec.longitude());
    values.put(FormField.LAUNCH_SITE_ALT.key(), spec.altitude());

    LaunchConfiguration configuration = spec.configuration();
    values.put(FormField.LAUNCHER_TYPE.key(), configuration.launcher().id());
    if (configuration.hasPayloadId()) {
      values.put(FormField.PAYLOAD_TYPE.key(), configuration.payloadId());
    }
    values.put(FormField.PAYLOAD_MASS.key(), configuration.payload().dryMass());

    if (spec.horizon() instanceof MissionHorizon.FixedDuration(double seconds)) {
      values.put(FormField.MISSION_HORIZON_DAYS.key(), seconds / MissionHorizon.SECONDS_PER_DAY);
    }
    switch (spec) {
      case MissionSpec.EarthOrbit earthOrbit -> {
        values.put(FormField.LEO_PERIGEE_ALT.key(), toKilometers(earthOrbit.perigeeAltitude()));
        values.put(FormField.LEO_APOGEE_ALT.key(), toKilometers(earthOrbit.apogeeAltitude()));
        putInclinationIfCommanded(values, earthOrbit);
      }
      case MissionSpec.Geo geo ->
          values.put(FormField.GTO_PARKING_ALT.key(), toKilometers(geo.parkingAltitude()));
      // MIS-4 / L5 §6.2. Only the perilune: the parking altitude is the mission's own constant and
      // no field carries it, so writing it back would be a second truth about the same number.
      case MissionSpec.Lunar lunar ->
          values.put(FormField.LUNAR_PERILUNE_ALT.key(), toKilometers(lunar.periluneAltitude()));
      // MIS-5 / L7 §4. Only the orbit altitude, on the flyby's reasoning: the parking altitude is
      // the mission's own constant and no field carries it.
      case MissionSpec.LunarOrbit lunarOrbit ->
          values.put(FormField.LUNAR_ORBIT_ALT.key(), toKilometers(lunarOrbit.orbitAltitude()));
    }
    return values;
  }

  /**
   * Writes the target inclination back — but only when the mission actually asked for one.
   *
   * <p>The predicate is {@code LaunchPlane.commands}, the very one the model uses to decide whether
   * the ascent is flown to a commanded plane. A mission left on its site's free plane must come
   * back with <b>no</b> inclination key, so that revalidating an untouched edit rebuilds {@code
   * dueEast(latitude)} from the latitude rather than from the degrees printed in a field:
   * publishing the derived value would move the azimuth, the launch assist and every propellant
   * load (spec {@code docs/earth-orbit/02-wizard-orbites-terrestres.md} §2.0).
   */
  private static void putInclinationIfCommanded(
      Map<String, Object> values, MissionSpec.EarthOrbit spec) {
    LaunchPlane plane = spec.launchPlane();
    if (plane.commands(FastMath.toRadians(spec.latitude()))) {
      values.put(FormField.TARGET_INCLINATION.key(), plane.targetInclinationDeg());
    }
    // The node has no derived form to be confused with, so it comes back whenever it was asked for.
    if (spec.hasTargetRaan()) {
      values.put(FormField.TARGET_RAAN.key(), spec.targetRaan());
    }
  }

  /** Specs hold altitudes in meters, every wizard altitude widget works in kilometers. */
  private static double toKilometers(double meters) {
    return meters / 1000.0;
  }
}
