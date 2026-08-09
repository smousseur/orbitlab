package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.IgnitionMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.PropellantType;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageCapabilities;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import org.junit.jupiter.api.Test;

class LaunchersTest {

  @Test
  void byId_falconHeavy_returnsCatalogConstant() {
    assertSame(Launchers.FALCON_HEAVY, Launchers.byId("FALCON_HEAVY"));
  }

  @Test
  void byId_ariane62_returnsCatalogEntry() {
    assertEquals("Ariane 62", Launchers.byId("ARIANE_62").displayName());
  }

  /**
   * The Ariane 62 flight profile, and the reasoning behind each figure (spec {@code
   * specs/launchers/01-ariane-62.md} §4.2). The pitch kick is deliberately identical to Falcon
   * Heavy's: nothing justifies an offset, and inventing one to make the catalog look more varied
   * would put an unfounded number in it.
   */
  @Test
  void ariane62_ascentProfile_differsFromFalconHeavy() {
    AscentProfile profile = Launchers.ARIANE_62.ascentProfile();
    // Lifts off at T/W ~1.99 against ~1.65 for Falcon Heavy: the pad is cleared sooner.
    assertEquals(6.0, profile.verticalAscentDuration(), 1e-9);
    assertEquals(3.0, profile.pitchKickAngleDeg(), 1e-9);
    // Vinci is cryogenic and needs a chill-down before ignition; the Merlin Vacuum relights fast.
    assertEquals(5.0, profile.interstageCoastDuration(), 1e-9);
  }

  @Test
  void byId_unknownId_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Launchers.byId("SATURN_V"));
  }

  /**
   * The catalog is an ordered, deliberate list, not a set: {@code Launchers.all()} feeds the
   * wizard's launcher list directly, so the order is user-visible. Asserting it exactly is meant to
   * fail when a third launcher lands — that addition should be a conscious decision about where it
   * appears, not a silent append.
   */
  @Test
  void all_listsFalconHeavyThenAriane62() {
    assertEquals(List.of(Launchers.FALCON_HEAVY, Launchers.ARIANE_62), Launchers.all());
  }

  @Test
  void ariane62_knownFigures() {
    List<StageModel> stages = Launchers.ARIANE_62.stages();
    assertEquals(2, stages.size());

    StageModel s1 = stages.getFirst();
    assertEquals(36_000, s1.dryMass(), 1e-6);
    assertEquals(434_000, s1.propellantCapacity(), 1e-6);
    assertEquals(300, s1.propulsion().isp(), 1e-6);
    assertEquals(9_960_000, s1.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.GROUND, s1.capabilities().ignition());
    assertEquals(StageRole.CORE, s1.capabilities().role());
    // Two thirds of this block's propellant is solid, yet it is declared liquid on purpose:
    // SOLID would freeze the load out of StageModel.toVehicle and of the multi-lambda sweep.
    assertEquals(PropellantType.CRYOGENIC, s1.capabilities().propellant());
    assertTrue(s1.capabilities().variableLoad(), "the aggregate must stay mission-sizable");

    StageModel s2 = stages.get(1);
    assertEquals(6_000, s2.dryMass(), 1e-6);
    assertEquals(31_000, s2.propellantCapacity(), 1e-6);
    assertEquals(457, s2.propulsion().isp(), 1e-6);
    assertEquals(180_000, s2.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.AIRSTART, s2.capabilities().ignition());
    assertEquals(4, s2.capabilities().restartCount());
    assertEquals(StageRole.UPPER, s2.capabilities().role());
  }

  /**
   * Locks a <b>declaration</b>, not a behaviour. Nothing in {@code src/main} reads {@code
   * canCoastFor}, {@code restartCount}, {@code ShutdownMode}, {@code IgnitionMode} or {@code
   * StageRole} — {@code StageCapabilities} is, in its own words, the input of a <em>future</em>
   * profile derivation. So this passing does <b>not</b> mean an Ariane 62 GEO mission circularizes
   * with its own upper stage: {@code GEOMission} hardcodes the split profile and delegates the
   * apogee burn to the payload's kick motor for every launcher. Read the sibling Falcon Heavy test
   * the same way.
   */
  @Test
  void ariane62_upperStageCoast_declaresTheLongCoastItsDesignAllows() {
    StageCapabilities s2 = Launchers.ARIANE_62.stages().get(1).capabilities();
    assertTrue(s2.canCoastFor(45 * 60), "parking coast to node must be possible");
    assertTrue(s2.canCoastFor(5.25 * 3_600), "the ULPM is designed for a GTO coast to apogee");
  }

  @Test
  void falconHeavy_knownFigures() {
    List<StageModel> stages = Launchers.FALCON_HEAVY.stages();
    assertEquals(2, stages.size());

    StageModel s1 = stages.getFirst();
    assertEquals(66_000, s1.dryMass(), 1e-6);
    assertEquals(1_233_000, s1.propellantCapacity(), 1e-6);
    assertEquals(296, s1.propulsion().isp(), 1e-6);
    assertEquals(22_800_000, s1.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.GROUND, s1.capabilities().ignition());
    assertEquals(StageRole.CORE, s1.capabilities().role());

    StageModel s2 = stages.get(1);
    assertEquals(4_000, s2.dryMass(), 1e-6);
    assertEquals(107_500, s2.propellantCapacity(), 1e-6);
    assertEquals(348, s2.propulsion().isp(), 1e-6);
    assertEquals(981_000, s2.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.AIRSTART, s2.capabilities().ignition());
    assertEquals(2, s2.capabilities().restartCount());
    assertEquals(StageRole.UPPER, s2.capabilities().role());
  }

  @Test
  void falconHeavy_upperStageCoast_allowsParkingButNotGtoCoast() {
    StageCapabilities s2 = Launchers.FALCON_HEAVY.stages().get(1).capabilities();
    assertTrue(s2.canCoastFor(45 * 60), "parking coast to node must be possible");
    assertFalse(s2.canCoastFor(5.25 * 3_600), "GTO coast to apogee must delegate to the AKM");
  }

  /**
   * Mass-equivalence lock (spec 07 §6): instantiating the catalog model with the loads of the
   * former {@code Launchers.FalconHeavy(600_000, 50_000, …)} factory yields a stack with the same
   * per-stage masses. Propulsion follows the catalog, whose S1 ISP was deliberately recalibrated
   * from 311 s to 296 s (spec 06 §S1).
   */
  @Test
  void falconHeavy_instantiate_matchesFormerFactoryStack() {
    Spacecraft payload = Spacecraft.LEGACY;
    VehicleStack stack =
        Launchers.FALCON_HEAVY.instantiate(new double[] {600_000, 50_000}, payload);

    List<Vehicle> vehicles = stack.vehicles();
    assertEquals(3, vehicles.size());

    Vehicle s1 = vehicles.getFirst();
    assertEquals(66_000, s1.dryMass(), 1e-6);
    assertEquals(600_000, s1.propellantLoad(), 1e-6);
    assertEquals(296, s1.propulsion().isp(), 1e-6);
    assertEquals(22_800_000, s1.propulsion().thrust(), 1e-6);

    Vehicle s2 = vehicles.get(1);
    assertEquals(4_000, s2.dryMass(), 1e-6);
    assertEquals(50_000, s2.propellantLoad(), 1e-6);
    assertEquals(348, s2.propulsion().isp(), 1e-6);
    assertEquals(981_000, s2.propulsion().thrust(), 1e-6);

    assertSame(payload, vehicles.get(2));
    assertEquals(66_000 + 600_000 + 4_000 + 50_000 + payload.getMass(), stack.getMass(), 1e-6);
  }
}
