package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hipparchus.util.FastMath;
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
   * Exit criteria of spec 06 I2 (vehicle mass reflects the entered payload) and I3 (loads sized per
   * mission: S1 full, S2 well under capacity for LEO 400 km).
   */
  @Test
  void leoFromWizard_payloadReflected_andS2SizedByBudget() {
    Mission mission = MissionFactory.fromWizardValues(baseValues(), MissionType.LEO);
    assertInstanceOf(EarthOrbitMission.class, mission);

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

  /**
   * The wizard filters inert payloads out of a GEO mission; this is the net for every other caller,
   * turning what used to be an NPE inside the propagation thread into an explicit refusal.
   */
  @Test
  void geoWithInertPayload_rejected() {
    Map<String, Object> values = baseValues();
    values.put("PAYLOAD_TYPE", "CARGO_MODULE");
    values.put("PAYLOAD_MASS", 15_000.0);
    assertThrows(
        IllegalArgumentException.class,
        () -> MissionFactory.fromWizardValues(values, MissionType.GEO));
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

  // --- MIS-7 P2: the inclination the wizard hands over (spec 02 §2.0) ---

  private static MissionSpec.EarthOrbit earthOrbitSpec(Map<String, Object> values) {
    return assertInstanceOf(
        MissionSpec.EarthOrbit.class, MissionFactory.specFromWizardValues(values, MissionType.LEO));
  }

  /**
   * The non-regression rule, and the reason the wizard omits the key rather than publishing the
   * derived value: an absent inclination must rebuild the plane from the latitude <b>in double</b>.
   * Comparing against {@code dueEast} rather than against a literal is deliberate — it is the one
   * comparison that stays true if the latitude ever changes.
   */
  @Test
  void absentInclination_isTheSitesFreePlane() {
    MissionSpec.EarthOrbit spec = earthOrbitSpec(baseValues());
    LaunchPlane expected = LaunchPlane.dueEast(5.23);

    assertEquals(expected.targetInclination(), spec.targetInclination(), 0.0, "exactly due east");
    assertEquals(NodeBranch.ASCENDING, spec.nodeBranch());
    assertFalse(
        spec.launchPlane().commands(FastMath.toRadians(5.23)),
        "a free plane must not be flown as a commanded one");
  }

  /** A blank entry is an absent one: it means the field was cleared, not that 0° was asked for. */
  @Test
  void blankInclination_isTheSitesFreePlane() {
    Map<String, Object> values = baseValues();
    values.put("TARGET_INCLINATION", "  ");
    assertEquals(
        LaunchPlane.dueEast(5.23).targetInclination(),
        earthOrbitSpec(values).targetInclination(),
        0.0);
  }

  @Test
  void polarInclination_isCarriedToTheSpec() {
    Map<String, Object> values = baseValues();
    values.put("TARGET_INCLINATION", 90.0);
    MissionSpec.EarthOrbit spec = earthOrbitSpec(values);

    assertEquals(90.0, FastMath.toDegrees(spec.targetInclination()), 1e-9);
    assertTrue(
        spec.launchPlane().commands(FastMath.toRadians(5.23)),
        "a polar target must be flown as a commanded plane");
  }

  /** The wizard writes doubles, a hand-assembled map may well write text. Both are read. */
  @Test
  void inclinationGivenAsText_isRead() {
    Map<String, Object> values = baseValues();
    values.put("TARGET_INCLINATION", "98.19");
    assertEquals(98.19, FastMath.toDegrees(earthOrbitSpec(values).targetInclination()), 1e-9);
  }

  /**
   * Refused, not clamped (spec 01 §8). The message has to name the reachable band, because it is the
   * one the wizard shows the user.
   */
  @Test
  void unreachableInclination_isRefusedWithTheReachableBand() {
    Map<String, Object> values = baseValues();
    values.put("TARGET_INCLINATION", 2.0);
    OrbitlabException error =
        assertThrows(
            OrbitlabException.class,
            () -> MissionFactory.specFromWizardValues(values, MissionType.LEO));
    assertTrue(error.getMessage().contains("5.230"), () -> "no reachable bound named: " + error);
  }

  @Test
  void unreadableInclination_isRefused() {
    Map<String, Object> values = baseValues();
    values.put("TARGET_INCLINATION", "polar");
    assertThrows(
        OrbitlabException.class, () -> MissionFactory.specFromWizardValues(values, MissionType.LEO));
  }

  // --- MIS-7 P2.e: what the wizard's launcher step dry-runs before submitting (spec 02 §6) ---

  private static Map<String, Object> meoValues(String launcherId, String payloadId) {
    Map<String, Object> values = baseValues();
    values.put("LAUNCHER_TYPE", launcherId);
    values.put("PAYLOAD_TYPE", payloadId);
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LEO_PERIGEE_ALT", 20_200.0);
    values.put("LEO_APOGEE_ALT", 20_200.0);
    values.put("TARGET_INCLINATION", 55.0);
    return values;
  }

  /**
   * The refusal the wizard shows on its launcher step: a Falcon Heavy upper stage declares 2 h of
   * coast against the 2 h 58 the transfer to 20 200 km needs, and an inert payload has no kick motor
   * to take the apogee burn over. What matters as much as the refusal is its <b>wording</b> — it is
   * shown verbatim, so it has to name the stage and both durations for the user to know the way out.
   */
  @Test
  void mediumEarthOrbit_onAShortCoastStageWithoutAkm_isRefusedByName() {
    Map<String, Object> values = meoValues("FALCON_HEAVY", "EARTH_OBS_SAT");
    OrbitlabException error =
        assertThrows(
            OrbitlabException.class,
            () -> MissionFactory.fromWizardValues(values, MissionType.LEO));

    String message = error.getMessage();
    assertTrue(message.contains("2.98 h"), () -> "transfer coast not named: " + message);
    assertTrue(message.contains("2.00 h"), () -> "declared coast not named: " + message);
    assertTrue(message.contains("kick motor"), () -> "no way out offered: " + message);
  }

  /** The same target on a stage that holds the coast composes — through the parking chain. */
  @Test
  void mediumEarthOrbit_onALongCoastStage_composes() {
    Mission mission =
        MissionFactory.fromWizardValues(
            meoValues("ARIANE_62", "EARTH_OBS_SAT"), MissionType.LEO);
    assertInstanceOf(GEOMission.class, mission, "a MEO is flown through the parking chain");
  }

  /** And so does one whose payload can take the apogee burn over, short stage or not. */
  @Test
  void mediumEarthOrbit_withAnApogeeKickMotor_composes() {
    Mission mission =
        MissionFactory.fromWizardValues(meoValues("FALCON_HEAVY", "GEO_SAT"), MissionType.LEO);
    assertInstanceOf(GEOMission.class, mission);
  }

  /**
   * The budget has to follow the plane, not the latitude: a polar launch loses the whole 463 m/s of
   * eastward entrainment the due-east one banks (spec 01 §7), so it is sized heavier. This is what
   * makes the single line of §14.1.3 enough — nothing downstream had to be told about the plane.
   */
  @Test
  void commandedPlane_reachesThePropellantBudget() {
    Map<String, Object> polarValues = baseValues();
    polarValues.put("TARGET_INCLINATION", 90.0);

    double dueEastLoad = earthOrbitSpec(baseValues()).configuration().propellantLoads()[1];
    double polarLoad = earthOrbitSpec(polarValues).configuration().propellantLoads()[1];

    assertTrue(
        polarLoad > dueEastLoad,
        () -> "polar S2 load " + polarLoad + " kg should exceed due-east " + dueEastLoad + " kg");
  }
}
