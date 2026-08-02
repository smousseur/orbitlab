package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
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
                    Payloads.CARGO_MODULE, Payloads.EARTH_OBSERVATION_SAT, Payloads.GEO_SAT)));
  }

  @Test
  void hasAkm_tracksPropulsion() {
    assertFalse(Payloads.CARGO_MODULE.hasAkm());
    assertFalse(Payloads.EARTH_OBSERVATION_SAT.hasAkm());
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

  @Test
  void forMissionType_leo_keepsWholeCatalog() {
    assertEquals(Payloads.all(), Payloads.forMissionType(MissionType.LEO));
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
