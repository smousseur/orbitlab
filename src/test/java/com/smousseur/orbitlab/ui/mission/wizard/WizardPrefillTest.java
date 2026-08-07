package com.smousseur.orbitlab.ui.mission.wizard;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * Round-trip guard for the wizard's edit mode: reopening a mission and validating it unchanged must
 * yield the same spec. Anything the prefill drops silently re-defaults on the way back, which is how
 * an edit would quietly change a mission the user did not touch.
 */
class WizardPrefillTest {

  private static final String LAUNCH_DATE = "2030-03-01 12:00:00";

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Map<String, Object> leoValues() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Wizard mission");
    values.put("LAUNCH_DATE", LAUNCH_DATE);
    values.put("LAUNCH_SITE_NAME", "Kourou - French Guiana");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.769);
    values.put("LAUNCH_SITE_ALT", 14.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", 8_000.0);
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 550.0);
    return values;
  }

  private static Map<String, Object> geoValues() {
    Map<String, Object> values = leoValues();
    values.put("PAYLOAD_TYPE", "GEO_SAT");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("GTO_PARKING_ALT", 300.0);
    return values;
  }

  /** Builds the entry a mission created through the wizard would produce. */
  private static MissionEntry entryFor(Map<String, Object> values, MissionType type) {
    MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
    entry.setScheduledDate(TimeConverter.parseUtcDate(LAUNCH_DATE).orElseThrow());
    return entry;
  }

  private static MissionSpec reopen(MissionEntry entry, MissionType type) {
    return MissionFactory.specFromWizardValues(WizardPrefill.fromEntry(entry), type);
  }

  private static void assertSameVehicle(MissionSpec expected, MissionSpec actual) {
    LaunchConfiguration original = expected.configuration();
    LaunchConfiguration reopened = actual.configuration();
    assertEquals(original.launcher().id(), reopened.launcher().id(), "launcher");
    assertEquals(original.payloadId(), reopened.payloadId(), "payload catalog id");
    assertEquals(original.payload().dryMass(), reopened.payload().dryMass(), 1e-9, "payload mass");
    assertEquals(
        original.payload().propellantLoad(),
        reopened.payload().propellantLoad(),
        1e-9,
        "AKM load");
    assertArrayEquals(original.propellantLoads(), reopened.propellantLoads(), 1e-9, "loads");
  }

  private static void assertSameSite(MissionSpec expected, MissionSpec actual) {
    assertEquals(expected.name(), actual.name(), "name");
    assertEquals(expected.siteName(), actual.siteName(), "site name");
    assertEquals(expected.latitude(), actual.latitude(), 1e-9, "latitude");
    assertEquals(expected.longitude(), actual.longitude(), 1e-9, "longitude");
    assertEquals(expected.altitude(), actual.altitude(), 1e-9, "altitude");
  }

  @Test
  void leoSpec_survivesTheRoundTrip() {
    MissionSpec.Leo original = (MissionSpec.Leo) MissionFactory.specFromWizardValues(
        leoValues(), MissionType.LEO);
    MissionSpec.Leo reopened =
        (MissionSpec.Leo) reopen(entryFor(leoValues(), MissionType.LEO), MissionType.LEO);

    assertSameSite(original, reopened);
    assertSameVehicle(original, reopened);
    assertEquals(original.perigeeAltitude(), reopened.perigeeAltitude(), 1e-6, "perigee");
    assertEquals(original.apogeeAltitude(), reopened.apogeeAltitude(), 1e-6, "apogee");
  }

  @Test
  void geoSpec_survivesTheRoundTrip() {
    MissionSpec.Geo original = (MissionSpec.Geo) MissionFactory.specFromWizardValues(
        geoValues(), MissionType.GEO);
    MissionSpec.Geo reopened =
        (MissionSpec.Geo) reopen(entryFor(geoValues(), MissionType.GEO), MissionType.GEO);

    assertSameSite(original, reopened);
    assertSameVehicle(original, reopened);
    assertEquals(original.parkingAltitude(), reopened.parkingAltitude(), 1e-6, "parking altitude");
    assertEquals(original.targetAltitude(), reopened.targetAltitude(), 1e-6, "GEO altitude");
  }

  /** The date field is prefilled in the format the parameters step parses back. */
  @Test
  void scheduledDate_comesBackParsable() {
    MissionEntry entry = entryFor(leoValues(), MissionType.LEO);
    Object prefilled = WizardPrefill.fromEntry(entry).get("LAUNCH_DATE");

    AbsoluteDate parsed =
        TimeConverter.parseUtcDate(String.valueOf(prefilled))
            .orElseThrow(() -> new AssertionError("unparsable prefilled date: " + prefilled));
    assertEquals(0.0, parsed.durationFrom(entry.getScheduledDate().orElseThrow()), 1e-6);
  }

  /** An unscheduled mission leaves the field on its "now" default rather than on a made-up date. */
  @Test
  void unscheduledMission_omitsTheLaunchDate() {
    MissionEntry entry =
        new MissionEntry(MissionFactory.specFromWizardValues(leoValues(), MissionType.LEO));
    assertFalse(WizardPrefill.fromEntry(entry).containsKey("LAUNCH_DATE"));
  }

  /** Legacy entries carry no spec, which is exactly why the roster does not offer to edit them. */
  @Test
  void legacyEntry_hasNothingToPrefillFrom() {
    MissionEntry legacy =
        new MissionEntry(MissionFactory.fromWizardValues(leoValues(), MissionType.LEO));
    assertThrows(IllegalArgumentException.class, () -> WizardPrefill.fromEntry(legacy));
  }
}
