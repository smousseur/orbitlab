package com.smousseur.orbitlab.simulation.mission.context;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Covers the spec replacement backing the wizard's edit action. */
class MissionEntryTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Map<String, Object> values() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Edited mission");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.769);
    values.put("LAUNCH_SITE_ALT", 14.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", 8_000.0);
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 400.0);
    values.put("GTO_PARKING_ALT", 300.0);
    return values;
  }

  private static MissionSpec leoSpec(double perigeeKm, double apogeeKm) {
    Map<String, Object> values = values();
    values.put("LEO_PERIGEE_ALT", perigeeKm);
    values.put("LEO_APOGEE_ALT", apogeeKm);
    return MissionFactory.specFromWizardValues(values, MissionType.LEO);
  }

  @Test
  void applySpec_recomposesAndInvalidates() {
    MissionEntry entry = new MissionEntry(leoSpec(400, 400));
    Mission before = entry.mission();
    entry.mission().setStatus(MissionStatus.READY);

    assertTrue(entry.applySpec(leoSpec(600, 600)));

    assertNotSame(before, entry.mission(), "the mission is recomposed, not mutated");
    assertEquals(MissionStatus.DRAFT, entry.mission().getStatus(), "must be recomputed");
    assertTrue(entry.getEphemeris().isEmpty());
    assertTrue(entry.getOptimizerResult().isEmpty());
    assertEquals(
        600_000,
        ((MissionSpec.Leo) entry.spec().orElseThrow()).perigeeAltitude(),
        1e-6,
        "the entry now flies the edited spec");
  }

  /** Identity is what every registry, event and renderer keys on: an edit must not disturb it. */
  @Test
  void applySpec_keepsIdentity() {
    MissionEntry entry = new MissionEntry(leoSpec(400, 400));
    var id = entry.id();
    entry.setVisible(true);

    entry.applySpec(leoSpec(500, 500));

    assertEquals(id, entry.id());
    assertTrue(entry.isVisible());
  }

  @Test
  void applySpec_refusesAnotherMissionType() {
    MissionEntry entry = new MissionEntry(leoSpec(400, 400));
    Map<String, Object> geo = values();
    geo.put("PAYLOAD_TYPE", "GEO_SAT");
    geo.put("PAYLOAD_MASS", 2_000.0);
    MissionSpec geoSpec = MissionFactory.specFromWizardValues(geo, MissionType.GEO);

    assertThrows(IllegalArgumentException.class, () -> entry.applySpec(geoSpec));
  }

  @Test
  void applySpec_refusedOnALegacyEntry() {
    MissionEntry legacy =
        new MissionEntry(MissionFactory.fromWizardValues(values(), MissionType.LEO));
    MissionSpec spec = leoSpec(400, 400);

    assertThrows(IllegalStateException.class, () -> legacy.applySpec(spec));
  }
}
