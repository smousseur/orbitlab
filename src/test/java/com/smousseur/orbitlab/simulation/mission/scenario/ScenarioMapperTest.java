package com.smousseur.orbitlab.simulation.mission.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.ui.mission.wizard.WizardPrefill;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The map ↔ DTO round trip, in both mission types and with the absent values present and absent.
 *
 * <p>The absences are the point: an uncommanded inclination, an unwaited node and an "auto" horizon
 * must come back <b>absent</b>, not written at whatever value they were derived to be. That is what
 * keeps a reopened scenario flying the trajectory it flew (spec {@code
 * docs/scenario/01-persistance-missions.md} §3.1, rule 1).
 */
class ScenarioMapperTest {

  private static final String LAUNCH_DATE = "2030-03-01 12:00:00";
  private static final ColorRGBA COLOR = new ColorRGBA(79 / 255f, 195 / 255f, 247 / 255f, 1f);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Map<String, Object> leoValues() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Scenario mission");
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

  private static MissionEntry entryFor(Map<String, Object> values, MissionType type) {
    MissionEntry entry = new MissionEntry(MissionFactory.specFromWizardValues(values, type));
    entry.setScheduledDate(TimeConverter.parseUtcDate(LAUNCH_DATE).orElseThrow());
    entry.setOptimizationType(OptimizationType.BALANCED);
    entry.setColor(COLOR);
    entry.setVisible(true);
    return entry;
  }

  /**
   * The prefill map, minus the one key the format deliberately drops: the profile is derived from
   * the spec, and persisting it would be a second truth about the mission.
   */
  private static Map<String, Object> prefilled(MissionEntry entry) {
    Map<String, Object> values = new LinkedHashMap<>(WizardPrefill.fromEntry(entry));
    values.remove("MISSION_PROFILE");
    return values;
  }

  private static Map<String, Object> roundTrip(MissionEntry entry) {
    Map<String, Object> values = prefilled(entry);
    return ScenarioMapper.toMissionValues(ScenarioMapper.toScenarioMission(entry, values, null));
  }

  @Test
  void leoValues_surviveTheRoundTrip() {
    MissionEntry entry = entryFor(leoValues(), MissionType.LEO);
    assertEquals(prefilled(entry), roundTrip(entry));
  }

  @Test
  void geoValues_surviveTheRoundTrip() {
    MissionEntry entry = entryFor(geoValues(), MissionType.GEO);
    assertEquals(prefilled(entry), roundTrip(entry));
  }

  @Test
  void lunarValues_surviveTheRoundTrip() {
    MissionEntry entry = entryFor(lunarValues(), MissionType.LUNAR_FLYBY);
    assertEquals(prefilled(entry), roundTrip(entry));
  }

  @Test
  void commandedPlane_survivesTheRoundTrip() {
    Map<String, Object> values = leoValues();
    values.put("TARGET_INCLINATION", 51.6);
    values.put("TARGET_RAAN", 120.0);
    MissionEntry entry = entryFor(values, MissionType.LEO);

    ScenarioMission.EarthOrbit dto =
        (ScenarioMission.EarthOrbit)
            ScenarioMapper.toScenarioMission(entry, prefilled(entry), null);

    assertEquals(51.6, dto.inclinationDeg(), 1e-9);
    assertEquals(120.0, dto.raanDeg(), 1e-9);
    assertEquals(prefilled(entry), roundTrip(entry));
  }

  /**
   * A due-east mission asked for no plane. Writing the derived inclination would move the azimuth,
   * the signed launch assist and every propellant load — and no assertion on the inclination would
   * see it, since the plane itself would still be right.
   */
  @Test
  void uncommandedPlane_staysAbsent() {
    MissionEntry entry = entryFor(leoValues(), MissionType.LEO);

    ScenarioMission.EarthOrbit dto =
        (ScenarioMission.EarthOrbit)
            ScenarioMapper.toScenarioMission(entry, prefilled(entry), null);

    assertNull(dto.inclinationDeg(), "an uncommanded inclination is omitted");
    assertNull(dto.raanDeg(), "an unwaited node is omitted");
    assertFalse(roundTrip(entry).containsKey("TARGET_INCLINATION"));
    assertFalse(roundTrip(entry).containsKey("TARGET_RAAN"));
  }

  @Test
  void forcedHorizon_survivesTheRoundTrip() {
    Map<String, Object> values = leoValues();
    values.put("MISSION_HORIZON_DAYS", 4.5);
    MissionEntry entry = entryFor(values, MissionType.LEO);

    ScenarioMission dto = ScenarioMapper.toScenarioMission(entry, prefilled(entry), null);

    assertEquals(4.5, dto.horizonDays(), 1e-9);
    assertEquals(4.5, (Double) roundTrip(entry).get("MISSION_HORIZON_DAYS"), 1e-9);
  }

  /** "Auto" is the absence of the key, so the file must not name a number for it either. */
  @Test
  void autoHorizon_staysAbsent() {
    MissionEntry entry = entryFor(leoValues(), MissionType.LEO);

    assertNull(ScenarioMapper.toScenarioMission(entry, prefilled(entry), null).horizonDays());
    assertFalse(roundTrip(entry).containsKey("MISSION_HORIZON_DAYS"));
  }

  /** The four values the wizard does not own, and without which a reopened scenario is not it. */
  @Test
  void entryOwnedValues_travelWithTheMission() {
    MissionEntry entry = entryFor(leoValues(), MissionType.LEO);

    ScenarioMission dto = ScenarioMapper.toScenarioMission(entry, prefilled(entry), null);

    assertEquals("BALANCED", dto.optimizationMode());
    assertEquals("NONE", dto.atmosphere());
    assertEquals("#4FC3F7", dto.color());
    assertTrue(dto.visible());
    assertEquals("2030-03-01T12:00:00Z", dto.launchDate(), "the file writes ISO UTC");
  }

  @Test
  void color_comesBackFromItsHexForm() {
    ColorRGBA color = ScenarioMapper.fromHex("#4FC3F7");
    assertEquals(79 / 255f, color.r, 1e-6);
    assertEquals(195 / 255f, color.g, 1e-6);
    assertEquals(247 / 255f, color.b, 1e-6);
    assertEquals(1f, color.a, 1e-6);
    assertNull(ScenarioMapper.fromHex(null), "no colour written, none read");
    assertThrows(OrbitlabException.class, () -> ScenarioMapper.fromHex("blue"));
  }

  /** A value that is present but unreadable is refused, never quietly dropped (§7). */
  @Test
  void unreadableValue_isRefused() {
    Map<String, Object> values = leoValues();
    MissionEntry entry = entryFor(values, MissionType.LEO);
    Map<String, Object> broken = prefilled(entry);
    broken.put("TARGET_INCLINATION", "not a number");

    assertThrows(
        OrbitlabException.class, () -> ScenarioMapper.toScenarioMission(entry, broken, null));
  }

  /**
   * MIS-4 / L5 §6.2 — the gap L4 §10 pt 5 flagged and no lot of the découpage claimed. It is closed
   * here because the property this lot makes true is "the lunar mission is created in the wizard",
   * and a mission that vanishes on save does not make it true.
   */
  @Test
  void lunarFlyby_carriesItsPeriluneThroughTheDto() {
    MissionEntry entry = entryFor(lunarValues(), MissionType.LUNAR_FLYBY);

    ScenarioMission mission = ScenarioMapper.toScenarioMission(entry, prefilled(entry), null);
    ScenarioMission.Lunar lunar = assertInstanceOf(ScenarioMission.Lunar.class, mission);
    assertEquals(MissionType.LUNAR_FLYBY, lunar.type());
    assertEquals(100.0, lunar.periluneKm(), 1e-9);

    Map<String, Object> back = ScenarioMapper.toMissionValues(lunar);
    assertEquals(100.0, (Double) back.get("LUNAR_PERILUNE_ALT"), 1e-9);
    assertEquals("LUNAR_PROBE", back.get("PAYLOAD_TYPE"));
    // The parking altitude is the mission's own constant; the file names no number for it.
    assertFalse(back.containsKey("GTO_PARKING_ALT"));
  }
}
