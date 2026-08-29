package com.smousseur.orbitlab.simulation.mission.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionHorizon;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.ui.mission.wizard.WizardPrefill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The test that counts: {@code MissionEntry → WizardPrefill → ScenarioMapper → JSON →
 * ScenarioMapper → MissionFactory → MissionSpec}, compared to the spec it started from, <b>down to
 * the propellant loads</b>.
 *
 * <p>The loads are the assertion that matters. They are downstream of every subtlety of the wizard
 * path — the due-east derivation, the parking-orbit sizing, the AKM load — so a value the format
 * lost or rounded on the way shows up there, whereas an assertion on the targets alone would pass
 * with a mission sized for another trajectory (spec {@code
 * docs/scenario/01-persistance-missions.md} §9).
 */
class ScenarioRoundTripTest {

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
    values.put("MISSION_NAME", "Round-trip mission");
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

  private static Map<String, Object> lunarValues() {
    Map<String, Object> values = leoValues();
    values.put("PAYLOAD_TYPE", "LUNAR_PROBE");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_PERILUNE_ALT", 100.0);
    return values;
  }

  private static Map<String, Object> lunarOrbitValues() {
    Map<String, Object> values = leoValues();
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    return values;
  }

  private static Map<String, Object> polarValues() {
    Map<String, Object> values = leoValues();
    values.put("LEO_APOGEE_ALT", 400.0);
    values.put("TARGET_INCLINATION", 90.0);
    return values;
  }

  /** Walks one mission all the way round, through the real JSON text. */
  private static MissionSpec throughTheFile(Map<String, Object> values, MissionType type) {
    MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
    entry.setScheduledDate(TimeConverter.parseUtcDate(LAUNCH_DATE).orElseThrow());

    ScenarioMission dto =
        ScenarioMapper.toScenarioMission(entry, WizardPrefill.fromEntry(entry), null);
    ScenarioFile file =
        new ScenarioFile(
            ScenarioFile.CURRENT_FORMAT_VERSION,
            "2026-08-21T14:32:10Z",
            "2030-03-01T05:30:00Z",
            List.of(dto));

    ScenarioMission read = ScenarioCodec.read(ScenarioCodec.write(file)).missions().getFirst();
    return MissionFactory.specFromWizardValues(ScenarioMapper.toMissionValues(read), read.type());
  }

  private static void assertSameVehicle(MissionSpec expected, MissionSpec actual) {
    LaunchConfiguration original = expected.configuration();
    LaunchConfiguration restored = actual.configuration();
    assertEquals(original.launcher().id(), restored.launcher().id(), "launcher");
    assertEquals(original.payloadId(), restored.payloadId(), "payload catalog id");
    assertEquals(original.payload().dryMass(), restored.payload().dryMass(), 1e-9, "payload mass");
    assertEquals(
        original.payload().propellantLoad(), restored.payload().propellantLoad(), 1e-9, "AKM load");
    assertArrayEquals(original.propellantLoads(), restored.propellantLoads(), 0.0, "loads");
  }

  private static void assertSameSite(MissionSpec expected, MissionSpec actual) {
    assertEquals(expected.name(), actual.name(), "name");
    assertEquals(expected.siteName(), actual.siteName(), "site name");
    assertEquals(expected.latitude(), actual.latitude(), 1e-9, "latitude");
    assertEquals(expected.longitude(), actual.longitude(), 1e-9, "longitude");
    assertEquals(expected.altitude(), actual.altitude(), 1e-9, "altitude");
  }

  @Test
  void leoMission_comesBackIdentical() {
    MissionSpec.EarthOrbit original =
        (MissionSpec.EarthOrbit) MissionFactory.specFromWizardValues(leoValues(), MissionType.LEO);
    MissionSpec.EarthOrbit restored =
        (MissionSpec.EarthOrbit) throughTheFile(leoValues(), MissionType.LEO);

    assertSameSite(original, restored);
    assertSameVehicle(original, restored);
    assertEquals(original.perigeeAltitude(), restored.perigeeAltitude(), 1e-6, "perigee");
    assertEquals(original.apogeeAltitude(), restored.apogeeAltitude(), 1e-6, "apogee");
    assertEquals(original.targetInclination(), restored.targetInclination(), 1e-12, "target plane");
  }

  @Test
  void geoMission_comesBackIdentical() {
    MissionSpec.Geo original =
        (MissionSpec.Geo) MissionFactory.specFromWizardValues(geoValues(), MissionType.GEO);
    MissionSpec.Geo restored = (MissionSpec.Geo) throughTheFile(geoValues(), MissionType.GEO);

    assertSameSite(original, restored);
    assertSameVehicle(original, restored);
    assertEquals(original.parkingAltitude(), restored.parkingAltitude(), 1e-6, "parking altitude");
    assertEquals(original.targetAltitude(), restored.targetAltitude(), 1e-6, "GEO altitude");
  }

  /**
   * MIS-4 / L5 §6.2 — the lunar branch of the format, and the loads are the assertion that matters
   * here too: they are sized by {@code PropellantBudget.loadsForLunar} from the perilune and the
   * pad, so a value the file lost on the way shows up as a resized upper stage.
   */
  @Test
  void lunarMission_comesBackIdentical() {
    MissionSpec.Lunar original =
        (MissionSpec.Lunar)
            MissionFactory.specFromWizardValues(lunarValues(), MissionType.LUNAR_FLYBY);
    MissionSpec.Lunar restored =
        (MissionSpec.Lunar) throughTheFile(lunarValues(), MissionType.LUNAR_FLYBY);

    assertSameSite(original, restored);
    assertSameVehicle(original, restored);
    assertEquals(original.periluneAltitude(), restored.periluneAltitude(), 1e-6, "perilune");
    assertEquals(
        original.parkingAltitude(),
        restored.parkingAltitude(),
        1e-6,
        "the parking altitude comes back from the mission's constant, not from the file");
  }

  /**
   * MIS-5 / L7 §4 — the fourth branch of the format, and the one with a second load to lose.
   *
   * <p>A lunar orbit sizes its launcher <em>and</em> the payload's own insertion tank, so a value
   * the file dropped on the way would show up twice: as a resized upper stage, and as a spacecraft
   * carrying the wrong propellant into the burn it exists for.
   */
  @Test
  void lunarOrbitMission_comesBackIdentical() {
    MissionSpec.LunarOrbit original =
        (MissionSpec.LunarOrbit)
            MissionFactory.specFromWizardValues(lunarOrbitValues(), MissionType.LUNAR_ORBIT);
    MissionSpec.LunarOrbit restored =
        (MissionSpec.LunarOrbit) throughTheFile(lunarOrbitValues(), MissionType.LUNAR_ORBIT);

    assertSameSite(original, restored);
    assertSameVehicle(original, restored);
    assertEquals(original.orbitAltitude(), restored.orbitAltitude(), 1e-6, "lunar orbit altitude");
    assertEquals(
        original.parkingAltitude(),
        restored.parkingAltitude(),
        1e-6,
        "the parking altitude comes back from the mission's constant, not from the file");
    assertEquals(
        original.configuration().payload().propellantLoad(),
        restored.configuration().payload().propellantLoad(),
        1e-6,
        "the insertion load the payload carries");
  }

  /**
   * The plane is the seam the whole "meaningful absence" rule exists for: a commanded inclination
   * comes back commanded, and the heavier budget it costs comes back with it.
   */
  @Test
  void polarMission_comesBackPolarAndBudgetedForIt() {
    MissionSpec.EarthOrbit original =
        (MissionSpec.EarthOrbit)
            MissionFactory.specFromWizardValues(polarValues(), MissionType.LEO);
    MissionSpec.EarthOrbit restored =
        (MissionSpec.EarthOrbit) throughTheFile(polarValues(), MissionType.LEO);

    assertEquals(
        90.0, Math.toDegrees(restored.targetInclination()), 1e-9, "inclination after the file");
    assertSameVehicle(original, restored);
  }

  /**
   * A due-east mission must be rebuilt from its latitude, not from the degrees the file could have
   * printed. The two differ by thousandths of a degree — enough to move every propellant load.
   */
  @Test
  void dueEastMission_isRebuiltFromItsLatitude() {
    MissionSpec original = MissionFactory.specFromWizardValues(leoValues(), MissionType.LEO);
    MissionSpec restored = throughTheFile(leoValues(), MissionType.LEO);

    assertArrayEquals(
        original.configuration().propellantLoads(),
        restored.configuration().propellantLoads(),
        0.0,
        "a saved and reopened mission must not be resized");
  }

  @Test
  void forcedHorizon_survivesTheFile() {
    Map<String, Object> values = leoValues();
    values.put("MISSION_HORIZON_DAYS", 4.5);

    MissionSpec restored = throughTheFile(values, MissionType.LEO);

    assertInstanceOf(MissionHorizon.FixedDuration.class, restored.horizon());
    assertEquals(
        4.5 * MissionHorizon.SECONDS_PER_DAY,
        ((MissionHorizon.FixedDuration) restored.horizon()).seconds(),
        1e-6);
  }

  /** And a mission left on "auto" comes back on "auto", not on the number auto happened to give. */
  @Test
  void autoHorizon_survivesTheFile() {
    MissionSpec restored = throughTheFile(leoValues(), MissionType.LEO);

    assertEquals(
        MissionHorizon.defaultFor(MissionType.LEO).getClass(),
        restored.horizon().getClass(),
        "the derived default, not a frozen duration");
  }
}
