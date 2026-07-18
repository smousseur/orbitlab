package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MissionFactoryTest {

  private static final double S1_CAPACITY = 1_233_000;
  private static final double S2_CAPACITY = 107_500;

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

  private static List<Vehicle> stackOf(Mission mission) {
    return assertInstanceOf(VehicleStack.class, mission.getVehicle()).vehicles();
  }

  /**
   * Exit criteria of spec 06 I2 (vehicle mass reflects the entered payload) and I3 (loads sized
   * per mission: S1 full, S2 well under capacity for LEO 400 km).
   */
  @Test
  void leoFromWizard_payloadReflected_andS2SizedByBudget() {
    Mission mission = MissionFactory.fromWizardValues(baseValues(), MissionType.LEO);
    assertInstanceOf(LEOMission.class, mission);

    List<Vehicle> vehicles = stackOf(mission);
    assertEquals(S1_CAPACITY, vehicles.get(0).propellantLoad(), 1e-6, "S1 flies full in v1");
    double s2Load = vehicles.get(1).propellantLoad();
    assertTrue(s2Load > 0 && s2Load < 0.5 * S2_CAPACITY, () -> "sized S2 load, got " + s2Load);
    assertEquals(10_000, vehicles.get(2).getMass(), 1e-6, "payload mass as entered, AKM empty");
  }

  @Test
  void geoFromWizard_akmSized_andS2HeavierThanLeo() {
    Map<String, Object> values = baseValues();
    values.put("PAYLOAD_TYPE", "GEO_SAT");
    values.put("PAYLOAD_MASS", 2_000.0);
    Mission geoMission = MissionFactory.fromWizardValues(values, MissionType.GEO);
    assertInstanceOf(GEOMission.class, geoMission);

    Mission leoMission = MissionFactory.fromWizardValues(baseValues(), MissionType.LEO);

    List<Vehicle> geoVehicles = stackOf(geoMission);
    Vehicle akmPayload = geoVehicles.get(2);
    assertEquals(2_000, akmPayload.dryMass(), 1e-6);
    assertTrue(
        akmPayload.propellantLoad() > 1_000 && akmPayload.propellantLoad() <= 2_000,
        () -> "sized AKM load expected, got " + akmPayload.propellantLoad());

    double geoS2 = geoVehicles.get(1).propellantLoad();
    double leoS2 = stackOf(leoMission).get(1).propellantLoad();
    assertTrue(
        geoS2 > 3 * leoS2,
        () -> String.format("GEO S2 load (%.0f) must dwarf LEO S2 load (%.0f)", geoS2, leoS2));
  }

  @Test
  void nonPositivePayloadMass_fallsBackToCatalogDefault() {
    Map<String, Object> values = baseValues();
    values.put("PAYLOAD_MASS", 0.0);
    Mission mission = MissionFactory.fromWizardValues(values, MissionType.LEO);
    assertEquals(10_000, stackOf(mission).get(2).getMass(), 1e-6);
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
