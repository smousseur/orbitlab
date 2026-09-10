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
  void hasPropulsion_tracksTheDeclaredEngine() {
    assertFalse(Payloads.CARGO_MODULE.hasPropulsion());
    assertFalse(Payloads.LUNAR_PROBE.hasPropulsion());
    assertTrue(Payloads.GEO_SAT.hasPropulsion());
    assertTrue(
        Payloads.EARTH_OBSERVATION_SAT.hasPropulsion(),
        "the observation satellite keeps its own orbit since PHY-8 / L6");
  }

  @Test
  void forMissionType_geo_keepsOnlyPropelledPayloads() {
    List<PayloadModel> eligible = Payloads.forMissionType(MissionType.GEO);
    assertFalse(eligible.isEmpty(), "GEO must keep at least one flyable payload");
    assertTrue(eligible.stream().allMatch(PayloadModel::hasPropulsion));
    assertFalse(eligible.contains(Payloads.CARGO_MODULE));
    assertTrue(eligible.contains(Payloads.GEO_SAT));
  }

  /**
   * MIS-4 / L5 §5.2 — the catalog stopped being the answer to "what can a LEO fly" the day a lunar
   * probe entered it: eligibility has two axes, and this is the second one.
   */
  @Test
  void forMissionType_leo_keepsEveryEarthPayloadAndNoCargo() {
    List<PayloadModel> eligible = Payloads.forMissionType(MissionType.LEO);
    assertTrue(eligible.contains(Payloads.EARTH_OBSERVATION_SAT));
    assertTrue(eligible.contains(Payloads.GEO_SAT));
    assertFalse(eligible.contains(Payloads.LUNAR_PROBE), "a lunar probe is not a LEO payload");
    assertFalse(
        eligible.contains(Payloads.CARGO_MODULE),
        "putting a cargo module in orbit for its own sake is not a mission (PHY-8 / L6)");
  }

  /**
   * The third axis, PHY-8 / L6 §3.5: <b>what</b> a payload is for. The cargo module leaves every
   * list at once, and it is the only entry that does — which is also what makes the payload → mesh
   * table total on everything the wizard can offer, and spares PHY-6 a fallback.
   */
  @Test
  void theCargoModuleIsOfferedByNoMissionTypeAtAll() {
    for (MissionType type : MissionType.values()) {
      assertFalse(
          Payloads.forMissionType(type).contains(Payloads.CARGO_MODULE),
          () -> "cargo module offered for " + type);
    }
    assertTrue(Payloads.CARGO_MODULE.requiresRendezvous());
    assertTrue(
        Payloads.all().stream().filter(PayloadModel::requiresRendezvous).count() == 1,
        "it is the only entry filtered on purpose");
  }

  /**
   * MIS-5 / L3 §2.2 — the orbiter joins this list, and that is the property rather than a leak. A
   * flyby requires no propulsion, so it excludes none: the orbiter flies it with an empty tank,
   * exactly as {@code MissionType.LEO}'s javadoc says a propelled payload does. Keeping it out
   * would have taken an axis of eligibility that forbids something physically licit.
   */
  @Test
  void forMissionType_lunarFlyby_keepsEveryLunarAndUniversalPayload() {
    assertEquals(
        List.of(Payloads.LUNAR_PROBE, Payloads.LUNAR_ORBITER),
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

    // Universal, inert, and now filtered on purpose besides.
    assertEquals(PayloadDomain.ANY, Payloads.CARGO_MODULE.domain());
    assertFalse(Payloads.CARGO_MODULE.hasPropulsion());
    assertTrue(Payloads.CARGO_MODULE.requiresRendezvous());
    // Lunar but inert.
    assertEquals(PayloadDomain.LUNAR, Payloads.LUNAR_PROBE.domain());
    assertFalse(Payloads.LUNAR_PROBE.hasPropulsion());
    // Propelled but terrestrial.
    assertTrue(Payloads.GEO_SAT.hasPropulsion());
    assertEquals(PayloadDomain.EARTH, Payloads.GEO_SAT.domain());
  }

  /**
   * The mirror of {@link #lunarProbe_isInertAndPlacedByItsDomain()}: the orbiter is the first model
   * that is lunar <em>and</em> propelled, which is what the two axes have to cross to select it.
   */
  @Test
  void lunarOrbiter_isPropelledAndPlacedByItsDomain() {
    assertTrue(Payloads.LUNAR_ORBITER.hasPropulsion());
    assertEquals(PayloadDomain.LUNAR, Payloads.LUNAR_ORBITER.domain());
    assertEquals(800.0, Payloads.LUNAR_ORBITER.propellantCapacity(), 1e-6);
    assertEquals(5_500.0, Payloads.LUNAR_ORBITER.propulsion().thrust(), 1e-6);
    assertEquals(320.0, Payloads.LUNAR_ORBITER.propulsion().isp(), 1e-6);
  }

  @Test
  void forMissionType_geo_doesNotOfferTheLunarProbe() {
    assertFalse(Payloads.forMissionType(MissionType.GEO).contains(Payloads.LUNAR_PROBE));
  }

  /** The probe is inert, so only the domain can keep it out of an Earth mission's list. */
  @Test
  void lunarProbe_isInertAndPlacedByItsDomain() {
    assertFalse(Payloads.LUNAR_PROBE.hasPropulsion());
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
  void propellantLoadAboveCapacity_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Payloads.GEO_SAT.toSpacecraft(2_000, 2_001));
  }

  @Test
  void nonPositiveDryMass_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Payloads.CARGO_MODULE.toSpacecraft(0, 0));
  }

  /**
   * The bus dimension and the declared cross-section are two ways of writing the same assumption,
   * and PHY-8 / L5 turned the first from a comment into a field. Nothing reads the dimension yet —
   * PHY-6 will — so this is what keeps the pair from drifting apart in the meantime: a section
   * edited without its dimension, or the reverse, goes red here.
   *
   * <p>The shapes are the catalog's own, stated in its comments: the cargo module is the one
   * cylindrical bus, the four satellites are boxy.
   */
  @Test
  void everyPayloadSectionIsItsDeclaredBusDimension() {
    assertEquals(
        Math.PI * Math.pow(Payloads.CARGO_MODULE.dimensionMeters() / 2, 2),
        Payloads.CARGO_MODULE.aerodynamics().crossSection(),
        0.01,
        "CARGO_MODULE, cylindrical");

    for (PayloadModel boxy :
        List.of(
            Payloads.EARTH_OBSERVATION_SAT,
            Payloads.GEO_SAT,
            Payloads.LUNAR_PROBE,
            Payloads.LUNAR_ORBITER)) {
      assertEquals(
          Math.pow(boxy.dimensionMeters(), 2),
          boxy.aerodynamics().crossSection(),
          1e-9,
          boxy.id() + ", boxy");
    }
  }

  /** Every wizard-offered payload states a size; a fixture built by hand states none, and may. */
  @Test
  void everyCatalogPayloadDeclaresItsDimension() {
    for (PayloadModel model : Payloads.all()) {
      assertTrue(model.dimensionMeters() > 0, model.id() + " declares no dimension");
    }
    assertEquals(0.0, new PayloadModel("BARE", "Bare fixture", 1_000, 0, null).dimensionMeters());
  }

  @Test
  void negativeDimension_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PayloadModel(
                "BAD", "Negative bus", 1_000, 0, null, null, PayloadDomain.ANY, -1.0, 0, false));
  }

  /**
   * The two controls PHY-8 / L6 §3.4 argues the observation satellite's numbers from, so that
   * changing one of them without the other goes red.
   *
   * <p>The budget is anchored on the worst LEO trim {@code 02-baseline-L0.md} §5 measured — 6.1
   * m/s, stable to 0.4 m/s across two launchers and three payload masses — and the thrust on the
   * rule {@link Payloads#LUNAR_ORBITER} states: a burn past 5 % of a revolution is not
   * near-impulsive, and the analytic stages that will fly it after PHY-6 assume it is.
   */
  @Test
  void theObservationSatellitesBudgetCoversTheMeasuredTrimWithANearImpulsiveBurn() {
    PayloadModel sat = Payloads.EARTH_OBSERVATION_SAT;
    double measuredWorstTrim = 6.1;

    assertTrue(
        sat.deltaVBudget() >= 2 * measuredWorstTrim,
        () -> "budget " + sat.deltaVBudget() + " m/s does not cover the measured trim twice over");

    double burnSeconds = sat.defaultDryMass() * measuredWorstTrim / sat.propulsion().thrust();
    double leo400Period = 5_553.6;
    assertTrue(
        burnSeconds < 0.05 * leo400Period,
        () -> "the trim would take " + burnSeconds + " s, over 5 % of a revolution");
  }

  /** A budget nothing can burn is a number that lies; the record refuses it. */
  @Test
  void deltaVBudgetWithoutPropulsion_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PayloadModel(
                "BAD",
                "Budget without engine",
                1_000,
                0,
                null,
                null,
                PayloadDomain.ANY,
                0,
                15,
                false));
  }

  @Test
  void capacityPropulsionCoherence_rejected() {
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
