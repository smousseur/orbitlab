package com.smousseur.orbitlab.ui.mission.wizard;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
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
 * yield the same spec. Anything the prefill drops silently re-defaults on the way back, which is
 * how an edit would quietly change a mission the user did not touch.
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
        original.payload().propellantLoad(), reopened.payload().propellantLoad(), 1e-9, "AKM load");
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
    MissionSpec.EarthOrbit original =
        (MissionSpec.EarthOrbit) MissionFactory.specFromWizardValues(leoValues(), MissionType.LEO);
    MissionSpec.EarthOrbit reopened =
        (MissionSpec.EarthOrbit) reopen(entryFor(leoValues(), MissionType.LEO), MissionType.LEO);

    assertSameSite(original, reopened);
    assertSameVehicle(original, reopened);
    assertEquals(original.perigeeAltitude(), reopened.perigeeAltitude(), 1e-6, "perigee");
    assertEquals(original.apogeeAltitude(), reopened.apogeeAltitude(), 1e-6, "apogee");
  }

  @Test
  void geoSpec_survivesTheRoundTrip() {
    MissionSpec.Geo original =
        (MissionSpec.Geo) MissionFactory.specFromWizardValues(geoValues(), MissionType.GEO);
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

  // --- MIS-7 P2: the target plane across the round trip (spec 02 §2.0 and §2.1) ---

  private static Map<String, Object> polarValues() {
    Map<String, Object> values = leoValues();
    values.put("LEO_PERIGEE_ALT", 550.0);
    values.put("LEO_APOGEE_ALT", 550.0);
    values.put("TARGET_INCLINATION", 90.0);
    return values;
  }

  /**
   * The rule that protects every calibrated trajectory: a mission left on its site's free plane must
   * come back with <b>no</b> inclination key, so revalidating an untouched edit rebuilds the plane
   * from the latitude rather than from a rounded field. Publishing it would move the azimuth, the
   * launch assist and every propellant load, and no inclination assertion would notice.
   */
  @Test
  void dueEastMission_comesBackWithoutAnInclinationKey() {
    Map<String, Object> prefilled = WizardPrefill.fromEntry(entryFor(leoValues(), MissionType.LEO));
    assertFalse(prefilled.containsKey("TARGET_INCLINATION"));
    assertEquals(MissionProfile.LEO.name(), prefilled.get("MISSION_PROFILE"));
  }

  /** And the loads it rebuilds are the ones it flew, which is what "bit-for-bit" has to mean. */
  @Test
  void dueEastMission_rebuildsTheSameVehicle() {
    MissionSpec original = MissionFactory.specFromWizardValues(leoValues(), MissionType.LEO);
    MissionSpec reopened = reopen(entryFor(leoValues(), MissionType.LEO), MissionType.LEO);

    assertArrayEquals(
        original.configuration().propellantLoads(),
        reopened.configuration().propellantLoads(),
        0.0,
        "an untouched edit must not resize the vehicle");
  }

  // --- MIS-2: the target node across the same round trip ---

  @Test
  void missionWithoutATargetNode_comesBackWithoutTheKey() {
    assertFalse(
        WizardPrefill.fromEntry(entryFor(leoValues(), MissionType.LEO)).containsKey("TARGET_RAAN"));
  }

  /**
   * The node has no derived form to be confused with — unlike the inclination, whose absence means
   * "the site's free plane" — so it comes back whenever it was asked for, and a reopened mission
   * keeps waiting for the same plane.
   */
  @Test
  void missionWithATargetNode_reopensWithIt() {
    Map<String, Object> values = leoValues();
    values.put("TARGET_INCLINATION", 51.6);
    values.put("TARGET_RAAN", 120.0);
    MissionEntry entry = entryFor(values, MissionType.LEO);

    Map<String, Object> prefilled = WizardPrefill.fromEntry(entry);

    assertEquals(120.0, (Double) prefilled.get("TARGET_RAAN"), 1e-9);
    assertEquals(
        120.0,
        ((MissionSpec.EarthOrbit) reopen(entry, MissionType.LEO)).targetRaan(),
        1e-9);
  }

  @Test
  void polarMission_reopensPolar() {
    MissionEntry entry = entryFor(polarValues(), MissionType.LEO);
    Map<String, Object> prefilled = WizardPrefill.fromEntry(entry);

    assertEquals(90.0, (Double) prefilled.get("TARGET_INCLINATION"), 1e-9);
    assertEquals(MissionProfile.POLAR.name(), prefilled.get("MISSION_PROFILE"));

    MissionSpec.EarthOrbit reopened =
        (MissionSpec.EarthOrbit) reopen(entry, MissionType.LEO);
    assertEquals(
        90.0, Math.toDegrees(reopened.targetInclination()), 1e-9, "inclination after the round trip");
  }

  /** The sizing follows the plane, so a polar mission must not come back budgeted as a due-east one. */
  @Test
  void polarMission_keepsItsHeavierBudget() {
    MissionSpec.EarthOrbit original =
        (MissionSpec.EarthOrbit) MissionFactory.specFromWizardValues(polarValues(), MissionType.LEO);
    MissionSpec.EarthOrbit reopened =
        (MissionSpec.EarthOrbit) reopen(entryFor(polarValues(), MissionType.LEO), MissionType.LEO);

    assertSameVehicle(original, reopened);
  }

  // --- UI-3 L0: the forced horizon comes back (spec docs/scenario/01-persistance-missions.md §4.3) ---

  /**
   * Until this was written the prefill never emitted the key, so reopening a mission on a forced
   * horizon quietly brought it back to "auto" — and every scenario saved through the prefill would
   * have lost it with it.
   */
  @Test
  void forcedHorizon_comesBack() {
    Map<String, Object> values = leoValues();
    values.put("MISSION_HORIZON_DAYS", 4.5);
    MissionEntry entry = entryFor(values, MissionType.LEO);

    assertEquals(
        4.5, (Double) WizardPrefill.fromEntry(entry).get("MISSION_HORIZON_DAYS"), 1e-9);

    MissionHorizon reopened = reopen(entry, MissionType.LEO).horizon();
    assertInstanceOf(MissionHorizon.FixedDuration.class, reopened);
    assertEquals(
        4.5 * MissionHorizon.SECONDS_PER_DAY,
        ((MissionHorizon.FixedDuration) reopened).seconds(),
        1e-6);
  }

  /** "Auto" is the absence of the key, so an untouched mission must come back with no key. */
  @Test
  void autoHorizon_staysAuto() {
    MissionEntry entry = entryFor(leoValues(), MissionType.LEO);

    assertFalse(WizardPrefill.fromEntry(entry).containsKey("MISSION_HORIZON_DAYS"));
    assertInstanceOf(
        MissionHorizon.Revolutions.class, reopen(entry, MissionType.LEO).horizon());
  }

  /**
   * The predicate is on the horizon <b>type</b>, never on its value. A GEO mission defaults to 3
   * revolutions, which is about the three days a user might have typed; comparing durations would
   * bring a forced horizon back as "auto", and the absence of the key would stop meaning what
   * {@code FormField.MISSION_HORIZON_DAYS} documents it to mean.
   */
  @Test
  void forcedHorizonWorthTheDerivedDefault_isStillForced() {
    Map<String, Object> values = geoValues();
    values.put("MISSION_HORIZON_DAYS", (double) MissionHorizon.DEFAULT_GEO_REVOLUTIONS);

    assertTrue(
        WizardPrefill.fromEntry(entryFor(values, MissionType.GEO))
            .containsKey("MISSION_HORIZON_DAYS"));
    assertFalse(
        WizardPrefill.fromEntry(entryFor(geoValues(), MissionType.GEO))
            .containsKey("MISSION_HORIZON_DAYS"));
  }

  /** Legacy entries carry no spec, which is exactly why the roster does not offer to edit them. */
  @Test
  void legacyEntry_hasNothingToPrefillFrom() {
    MissionEntry legacy =
        new MissionEntry(MissionFactory.fromWizardValues(leoValues(), MissionType.LEO));
    assertThrows(IllegalArgumentException.class, () -> WizardPrefill.fromEntry(legacy));
  }
}
