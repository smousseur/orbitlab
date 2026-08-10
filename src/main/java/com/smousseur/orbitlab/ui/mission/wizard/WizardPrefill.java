package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;

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

    switch (spec) {
      case MissionSpec.Leo leo -> {
        values.put(FormField.LEO_PERIGEE_ALT.key(), toKilometers(leo.perigeeAltitude()));
        values.put(FormField.LEO_APOGEE_ALT.key(), toKilometers(leo.apogeeAltitude()));
      }
      case MissionSpec.Geo geo ->
          values.put(FormField.GTO_PARKING_ALT.key(), toKilometers(geo.parkingAltitude()));
    }
    return values;
  }

  /** Specs hold altitudes in meters, every wizard altitude widget works in kilometers. */
  private static double toKilometers(double meters) {
    return meters / 1000.0;
  }
}
