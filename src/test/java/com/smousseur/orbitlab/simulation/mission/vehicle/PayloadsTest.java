package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadDomain;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayloadsTest {

  @Test
  void byId_geoSat_returnsCatalogConstant() {
    assertSame(Payloads.GEO_SAT, Payloads.byId("GEO_SAT"));
  }

  @Test
  void byId_unknownId_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Payloads.byId("SPACE_TELESCOPE"));
  }

  @Test
  void all_containsCatalogEntries() {
    assertTrue(
        Payloads.all()
            .containsAll(
                java.util.List.of(
                    Payloads.CARGO_MODULE,
                    Payloads.EARTH_OBSERVATION_SAT,
                    Payloads.GEO_SAT,
                    Payloads.LUNAR_PROBE)));
  }

  @Test
  void hasAkm_tracksPropulsion() {
    assertFalse(Payloads.CARGO_MODULE.hasAkm());
    assertFalse(Payloads.EARTH_OBSERVATION_SAT.hasAkm());
    assertFalse(Payloads.LUNAR_PROBE.hasAkm());
    assertTrue(Payloads.GEO_SAT.hasAkm());
  }

  @Test
  void forMissionType_geo_keepsOnlyPropelledPayloads() {
    List<PayloadModel> eligible = Payloads.forMissionType(MissionType.GEO);
    assertFalse(eligible.isEmpty(), "GEO must keep at least one flyable payload");
    assertTrue(eligible.stream().allMatch(PayloadModel::hasAkm));
    assertFalse(eligible.contains(Payloads.CARGO_MODULE));
    assertTrue(eligible.contains(Payloads.GEO_SAT));
  }

  /**
   * MIS-4 / L5 §5.2 — the catalog stopped being the answer to "what can a LEO fly" the day a lunar
   * probe entered it: eligibility has two axes, and this is the second one.
   */
  @Test
  void forMissionType_leo_keepsEveryEarthAndUniversalPayload() {
    List<PayloadModel> eligible = Payloads.forMissionType(MissionType.LEO);
    assertTrue(eligible.contains(Payloads.CARGO_MODULE));
    assertTrue(eligible.contains(Payloads.EARTH_OBSERVATION_SAT));
    assertTrue(eligible.contains(Payloads.GEO_SAT));
    assertFalse(eligible.contains(Payloads.LUNAR_PROBE), "a lunar probe is not a LEO payload");
  }

  /**
   * MIS-5 / L3 §2.2 — the orbiter joins this list, and that is the property rather than a leak. A
   * flyby requires no propulsion, so it excludes none: the orbiter flies it with an empty tank,
   * exactly as {@code MissionType.LEO}'s javadoc says an AKM-equipped payload does. Keeping it out
   * would have taken a third axis of eligibility, to forbid something physically licit.
   */
  @Test
  void forMissionType_lunarFlyby_keepsEveryLunarAndUniversalPayload() {
    assertEquals(
        List.of(Payloads.CARGO_MODULE, Payloads.LUNAR_PROBE, Payloads.LUNAR_ORBITER),
        Payloads.forMissionType(MissionType.LUNAR_FLYBY));
  }

  /**
   * MIS-5 / L3 §6.1 — the one mission type the catalog answers with a single model, and the three
   * refusals are for three different reasons. Asserting only the count would still pass if the two
   * axes of the filter collapsed into one, which is the defect worth catching here.
   */
  @Test
  void forMissionType_lunarOrbit_offersOnlyTheOrbiter() {
    List<PayloadModel> eligible = Payloads.forMissionType(MissionType.LUNAR_ORBIT);

    assertEquals(List.of(Payloads.LUNAR_ORBITER), eligible);

    // Universal but inert.
    assertEquals(PayloadDomain.ANY, Payloads.CARGO_MODULE.domain());
    assertFalse(Payloads.CARGO_MODULE.hasAkm());
    // Lunar but inert.
    assertEquals(PayloadDomain.LUNAR, Payloads.LUNAR_PROBE.domain());
    assertFalse(Payloads.LUNAR_PROBE.hasAkm());
    // Propelled but terrestrial.
    assertTrue(Payloads.GEO_SAT.hasAkm());
    assertEquals(PayloadDomain.EARTH, Payloads.GEO_SAT.domain());
  }

  /**
   * The mirror of {@link #lunarProbe_isInertAndPlacedByItsDomain()}: the orbiter is the first model
   * that is lunar <em>and</em> propelled, which is what the two axes have to cross to select it.
   */
  @Test
  void lunarOrbiter_isPropelledAndPlacedByItsDomain() {
    assertTrue(Payloads.LUNAR_ORBITER.hasAkm());
    assertEquals(PayloadDomain.LUNAR, Payloads.LUNAR_ORBITER.domain());
    assertEquals(800.0, Payloads.LUNAR_ORBITER.akmPropellantCapacity(), 1e-6);
    assertEquals(5_500.0, Payloads.LUNAR_ORBITER.akmPropulsion().thrust(), 1e-6);
    assertEquals(320.0, Payloads.LUNAR_ORBITER.akmPropulsion().isp(), 1e-6);
  }

  @Test
  void forMissionType_geo_doesNotOfferTheLunarProbe() {
    assertFalse(Payloads.forMissionType(MissionType.GEO).contains(Payloads.LUNAR_PROBE));
  }

  /** The probe is inert, so only the domain can keep it out of an Earth mission's list. */
  @Test
  void lunarProbe_isInertAndPlacedByItsDomain() {
    assertFalse(Payloads.LUNAR_PROBE.hasAkm());
    assertEquals(PayloadDomain.LUNAR, Payloads.LUNAR_PROBE.domain());
    assertEquals(PayloadDomain.ANY, Payloads.CARGO_MODULE.domain());
    assertEquals(PayloadDomain.EARTH, Payloads.EARTH_OBSERVATION_SAT.domain());
    assertEquals(PayloadDomain.EARTH, Payloads.GEO_SAT.domain());
  }

  @Test
  void geoSat_toSpacecraft_fullAkm_massIsDryPlusLoad() {
    Spacecraft spacecraft = Payloads.GEO_SAT.toSpacecraft(2_000, 2_000);
    assertEquals(2_000, spacecraft.dryMass(), 1e-6);
    assertEquals(2_000, spacecraft.propellantCapacity(), 1e-6);
    assertEquals(4_000, spacecraft.getMass(), 1e-6);
    assertEquals(320, spacecraft.propulsion().isp(), 1e-6);
  }

  @Test
  void inertPayload_akmLoad_rejected() {
    assertThrows(
        IllegalArgumentException.class, () -> Payloads.CARGO_MODULE.toSpacecraft(15_000, 1.0));
  }

  @Test
  void akmLoadAboveCapacity_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Payloads.GEO_SAT.toSpacecraft(2_000, 2_001));
  }

  @Test
  void nonPositiveDryMass_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Payloads.CARGO_MODULE.toSpacecraft(0, 0));
  }

  @Test
  void akmCapacityPropulsionCoherence_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PayloadModel("BAD", "Capacity without propulsion", 1_000, 500, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PayloadModel(
                "BAD", "Propulsion without capacity", 1_000, 0, new PropulsionSystem(300, 400)));
  }
}
