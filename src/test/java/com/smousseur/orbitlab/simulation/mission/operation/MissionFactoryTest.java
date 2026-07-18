package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MissionFactoryTest {

  /** Falcon Heavy fully loaded, without payload: 66 t + 1 233 t + 4 t + 107.5 t. */
  private static final double FH_FULL_MASS = 66_000 + 1_233_000 + 4_000 + 107_500;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Map<String, Object> baseValues() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Wizard mission");
    values.put("LAUNCH_SITE_LAT", 5.23);
    values.put("LAUNCH_SITE_LONG", -52.77);
    values.put("LAUNCH_SITE_ALT", 0.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", 10_000.0);
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 400.0);
    values.put("GTO_PARKING_ALT", 400.0);
    return values;
  }

  /** Exit criterion of spec 06 I2: the vehicle mass reflects the payload entered in the wizard. */
  @Test
  void leoFromWizard_vehicleMassReflectsEnteredPayload() {
    Mission mission = MissionFactory.fromWizardValues(baseValues(), MissionType.LEO);
    assertInstanceOf(LEOMission.class, mission);
    assertEquals(FH_FULL_MASS + 10_000, mission.getVehicle().getMass(), 1e-6);
  }

  @Test
  void geoFromWizard_geoSatCarriesFullAkm() {
    Map<String, Object> values = baseValues();
    values.put("PAYLOAD_TYPE", "GEO_SAT");
    values.put("PAYLOAD_MASS", 2_000.0);
    Mission mission = MissionFactory.fromWizardValues(values, MissionType.GEO);
    assertInstanceOf(GEOMission.class, mission);
    // 2 t dry entered in the wizard + 2 t of AKM propellant (fully loaded until spec 06 I3).
    assertEquals(FH_FULL_MASS + 4_000, mission.getVehicle().getMass(), 1e-6);
  }

  @Test
  void nonPositivePayloadMass_fallsBackToCatalogDefault() {
    Map<String, Object> values = baseValues();
    values.put("PAYLOAD_MASS", 0.0);
    Mission mission = MissionFactory.fromWizardValues(values, MissionType.LEO);
    assertEquals(FH_FULL_MASS + 10_000, mission.getVehicle().getMass(), 1e-6);
  }

  @Test
  void unknownLauncherId_rejected() {
    Map<String, Object> values = baseValues();
    values.put("LAUNCHER_TYPE", "SATURN_V");
    assertThrows(
        IllegalArgumentException.class,
        () -> MissionFactory.fromWizardValues(values, MissionType.LEO));
  }

  @Test
  void missingValue_rejected() {
    Map<String, Object> values = baseValues();
    values.remove("LEO_PERIGEE_ALT");
    assertThrows(
        IllegalArgumentException.class,
        () -> MissionFactory.fromWizardValues(values, MissionType.LEO));
  }
}
