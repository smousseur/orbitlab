package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.IgnitionMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.PropellantType;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageCapabilities;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaunchersTest {

  @Test
  void byId_falconHeavy_returnsCatalogConstant() {
    assertSame(Launchers.FALCON_HEAVY, Launchers.byId("FALCON_HEAVY"));
  }

  @Test
  void byId_ariane64_returnsCatalogEntry() {
    assertEquals("Ariane 64", Launchers.byId("ARIANE_64").displayName());
  }

  /**
   * The Ariane 64 flight profile, and the reasoning behind each figure (spec {@code
   * docs/launchers/01-ariane-64.md} §4.2). The pitch kick is deliberately identical to Falcon
   * Heavy's: nothing justifies an offset, and inventing one to make the catalog look more varied
   * would put an unfounded number in it.
   */
  @Test
  void ariane64_ascentProfile_differsFromFalconHeavy() {
    AscentProfile profile = Launchers.ARIANE_64.ascentProfile();
    // Kept from the Ariane 62 entry this one replaces. Its comment justified the 6 s by a lift-off
    // T/W of ~1.99; the Ariane 64 leaves the pad at ~1.60, so that justification no longer holds
    // and the value is now simply inherited — moving it would be a second cause in a lot that is
    // already a re-baseline (spec docs/etagement/06-conception-L4.md §3.1).
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
  void all_listsFalconHeavyThenAriane64() {
    assertEquals(List.of(Launchers.FALCON_HEAVY, Launchers.ARIANE_64), Launchers.all());
  }

  @Test
  void ariane64_knownFigures() {
    List<StageModel> stages = Launchers.ARIANE_64.stages();
    assertEquals(3, stages.size(), "the four boosters are a stage of their own since PHY-8 / L4");

    // Per exemplar, as everywhere in this catalog since L1: one P120C, flown in four.
    StageModel boosters = stages.getFirst();
    assertEquals(4, boosters.multiplicity());
    assertEquals(11_000, boosters.unitDryMass(), 1e-6);
    assertEquals(44_000, boosters.dryMass(), 1e-6);
    assertEquals(564_000, boosters.propellantCapacity(), 1e-6);
    assertEquals(278.5, boosters.propulsion().isp(), 1e-6);
    assertEquals(2_962_000, boosters.unitPropulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.GROUND, boosters.capabilities().ignition());
    assertEquals(StageRole.BOOSTER, boosters.capabilities().role());
    // A solid at last, and the point of saying so: variableLoad() now returns false on a stage
    // that really is one, which is all the lambda sweep ever needed (spec 06 §3.2).
    assertEquals(PropellantType.SOLID, boosters.capabilities().propellant());
    assertFalse(boosters.capabilities().variableLoad(), "solid boosters fly full");

    StageModel core = stages.get(1);
    assertEquals(14_000, core.dryMass(), 1e-6);
    assertEquals(152_000, core.propellantCapacity(), 1e-6);
    assertEquals(360, core.propulsion().isp(), 1e-6);
    assertEquals(1_118_000, core.propulsion().thrust(), 1e-6);
    assertEquals(StageRole.CORE, core.capabilities().role());
    assertEquals(PropellantType.CRYOGENIC, core.capabilities().propellant());
    assertTrue(core.capabilities().variableLoad(), "the core stays mission-sizable");

    StageModel s2 = stages.get(2);
    assertEquals(6_000, s2.dryMass(), 1e-6);
    assertEquals(31_000, s2.propellantCapacity(), 1e-6);
    assertEquals(457, s2.propulsion().isp(), 1e-6);
    assertEquals(180_000, s2.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.AIRSTART, s2.capabilities().ignition());
    assertEquals(4, s2.capabilities().restartCount());
    assertEquals(StageRole.UPPER, s2.capabilities().role());
  }

  /**
   * The two figures the decoupage gives as controls, which is what the thrusts were derived from:
   * the boosters run dry around 130 s and the Vulcain around 8 minutes, in a flow ratio near 14
   * (spec {@code docs/etagement/06-conception-L4.md} §3.1). Asserted on durations rather than on
   * thrusts, because the durations are what was anchored and the thrusts are what followed.
   */
  @Test
  void ariane64_burnDurations_areTheOnesTheThrustsWereDerivedFrom() {
    List<StageModel> stages = Launchers.ARIANE_64.stages();
    double boosterFlow = massFlow(stages.getFirst());
    double coreFlow = massFlow(stages.get(1));

    assertEquals(130.0, stages.getFirst().propellantCapacity() / boosterFlow, 0.1);
    assertEquals(480.0, stages.get(1).propellantCapacity() / coreFlow, 0.5);
    assertEquals(13.7, boosterFlow / coreFlow, 0.05);
  }

  private static double massFlow(StageModel stage) {
    return stage.propulsion().thrust()
        / (stage.propulsion().isp() * org.orekit.utils.Constants.G0_STANDARD_GRAVITY);
  }

  /**
   * Locks a <b>declaration</b>, not a behaviour. Nothing in {@code src/main} reads {@code
   * canCoastFor}, {@code restartCount}, {@code ShutdownMode}, {@code IgnitionMode} or {@code
   * StageRole} — {@code StageCapabilities} is, in its own words, the input of a <em>future</em>
   * profile derivation. So this passing does <b>not</b> mean an Ariane 64 GEO mission circularizes
   * with its own upper stage: {@code GEOMission} hardcodes the split profile and delegates the
   * apogee burn to the payload's kick motor for every launcher. Read the sibling Falcon Heavy test
   * the same way.
   */
  @Test
  void ariane64_upperStageCoast_declaresTheLongCoastItsDesignAllows() {
    StageCapabilities s2 = Launchers.ARIANE_64.stages().getLast().capabilities();
    assertTrue(s2.canCoastFor(45 * 60), "parking coast to node must be possible");
    assertTrue(s2.canCoastFor(5.25 * 3_600), "the ULPM is designed for a GTO coast to apogee");
  }

  @Test
  void falconHeavy_knownFigures() {
    List<StageModel> stages = Launchers.FALCON_HEAVY.stages();
    assertEquals(3, stages.size(), "the two side cores are a stage of their own since PHY-8 / L2");

    // Components are per exemplar, accessors aggregate (spec docs/etagement/03-conception-L1.md
    // §3.4): one side core is exactly a third of the block the catalog used to declare.
    StageModel boosters = stages.getFirst();
    assertEquals(2, boosters.multiplicity());
    assertEquals(22_000, boosters.unitDryMass(), 1e-6);
    assertEquals(44_000, boosters.dryMass(), 1e-6);
    assertEquals(822_000, boosters.propellantCapacity(), 1e-6);
    assertEquals(296, boosters.propulsion().isp(), 1e-6);
    assertEquals(15_200_000, boosters.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.GROUND, boosters.capabilities().ignition());
    assertEquals(StageRole.BOOSTER, boosters.capabilities().role());

    StageModel core = stages.get(1);
    assertEquals(22_000, core.dryMass(), 1e-6);
    assertEquals(411_000, core.propellantCapacity(), 1e-6);
    assertEquals(296, core.propulsion().isp(), 1e-6);
    assertEquals(7_600_000, core.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.GROUND, core.capabilities().ignition());
    assertEquals(StageRole.CORE, core.capabilities().role());

    StageModel s2 = stages.get(2);
    assertEquals(4_000, s2.dryMass(), 1e-6);
    assertEquals(107_500, s2.propellantCapacity(), 1e-6);
    assertEquals(348, s2.propulsion().isp(), 1e-6);
    assertEquals(981_000, s2.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.AIRSTART, s2.capabilities().ignition());
    assertEquals(2, s2.capabilities().restartCount());
    assertEquals(StageRole.UPPER, s2.capabilities().role());
  }

  /** The block still weighs, holds and pushes exactly what the aggregated S1 did (spec L2 §3.5). */
  @Test
  void falconHeavy_theBlockAggregatesToTheFormerFirstStage() {
    List<StageModel> stages = Launchers.FALCON_HEAVY.stages();
    StageModel boosters = stages.getFirst();
    StageModel core = stages.get(1);

    assertEquals(66_000, boosters.dryMass() + core.dryMass(), 0.0);
    assertEquals(1_233_000, boosters.propellantCapacity() + core.propellantCapacity(), 0.0);
    assertEquals(22_800_000, boosters.propulsion().thrust() + core.propulsion().thrust(), 0.0);
  }

  /**
   * What the wizard card shows: the thrust the vehicle actually leaves the pad with, summed over
   * every ground-lit entry (spec {@code docs/etagement/04-conception-L2.md} §3.4). Reading the
   * bottom entry alone would report 15.2 MN on a split Falcon Heavy.
   *
   * <p>15.2 MN of boosters plus a core held at 0.81 of its 7.6 — the installed 22.8 MN is what the
   * vehicle has, not what it leaves the pad with (spec {@code docs/etagement/05-conception-L3.md}
   * §3.4).
   */
  @Test
  void liftOffThrust_sumsTheGroundLitStagesAtTheThrustTheyApply() {
    assertEquals(15_200_000 + 0.81 * 7_600_000, Launchers.FALCON_HEAVY.liftOffThrust(), 1e-6);
    assertEquals(4 * 2_962_000 + 1_118_000, Launchers.ARIANE_64.liftOffThrust(), 1e-6);
  }

  @Test
  void liftOffThrust_ignoresTheAirStartedStages() {
    double groundLit =
        Launchers.FALCON_HEAVY.stages().stream()
            .filter(stage -> stage.capabilities().ignition() == IgnitionMode.GROUND)
            .mapToDouble(
                stage ->
                    stage.capabilities().role() == StageRole.CORE
                        ? stage.propulsion().thrust()
                            * Launchers.FALCON_HEAVY.ascentProfile().coreThrottle()
                        : stage.propulsion().thrust())
            .sum();

    assertEquals(groundLit, Launchers.FALCON_HEAVY.liftOffThrust(), 0.0);
  }

  /**
   * The Falcon Heavy's three cores being identical, thrust and propellant are in the same ratio and
   * both blocks would flame out at the same instant; throttling the centre one is what gives it a
   * solo phase (spec {@code docs/etagement/05-conception-L3.md} §3.1).
   */
  @Test
  void falconHeavy_throttlesItsCoreDuringTheSharedPhase() {
    assertTrue(Launchers.FALCON_HEAVY.ascentProfile().throttlesCore());
    assertEquals(0.81, Launchers.FALCON_HEAVY.ascentProfile().coreThrottle(), 0.0);
  }

  @Test
  void ariane64_doesNotThrottle() {
    assertFalse(Launchers.ARIANE_64.ascentProfile().throttlesCore());
  }

  @Test
  void falconHeavy_upperStageCoast_allowsParkingButNotGtoCoast() {
    StageCapabilities s2 = Launchers.FALCON_HEAVY.stages().getLast().capabilities();
    assertTrue(s2.canCoastFor(45 * 60), "parking coast to node must be possible");
    assertFalse(s2.canCoastFor(5.25 * 3_600), "GTO coast to apogee must delegate to the AKM");
  }

  /**
   * Mass-equivalence lock (spec 07 §6): instantiating the catalog model with the loads of the
   * former {@code Launchers.FalconHeavy(600_000, 50_000, …)} factory yields a stack with the same
   * masses. Propulsion follows the catalog, whose S1 ISP was deliberately recalibrated from 311 s
   * to 296 s (spec 06 §S1).
   *
   * <p>Since {@code PHY-8 / L2} the first stage is two entries, so the 600 t are the pro rata
   * {@code 400 / 200} split and the former figures are read off the block the stack resolves — the
   * level at which the vehicle burns.
   */
  @Test
  void falconHeavy_instantiate_matchesFormerFactoryStack() {
    Spacecraft payload = Spacecraft.LEGACY;
    VehicleStack stack =
        Launchers.FALCON_HEAVY.instantiate(new double[] {400_000, 200_000, 50_000}, payload);

    List<Vehicle> vehicles = stack.vehicles();
    assertEquals(4, vehicles.size());

    // Since PHY-8 / L3 the core is throttled, so the block the stack resolves is the boosters
    // alone: their 44 t of dry mass, and 15.2 + 0.81 × 7.6 MN. The former S1's 66 t and 22.8 MN are
    // still the sum of the two catalog entries, which
    // falconHeavy_theBlockAggregatesToTheFormerFirstStage asserts.
    ActiveStageInfo block = stack.resolveActiveStage(stack.getMass());
    assertEquals(44_000, block.dryMass(), 1e-6);
    assertEquals(296, block.propulsion().isp(), 1e-6);
    assertEquals(15_200_000 + 0.81 * 7_600_000, block.propulsion().thrust(), 1e-6);
    assertEquals(
        600_000, vehicles.getFirst().propellantLoad() + vehicles.get(1).propellantLoad(), 1e-6);

    Vehicle s2 = vehicles.get(2);
    assertEquals(4_000, s2.dryMass(), 1e-6);
    assertEquals(50_000, s2.propellantLoad(), 1e-6);
    assertEquals(348, s2.propulsion().isp(), 1e-6);
    assertEquals(981_000, s2.propulsion().thrust(), 1e-6);

    assertSame(payload, vehicles.get(3));
    assertEquals(66_000 + 600_000 + 4_000 + 50_000 + payload.getMass(), stack.getMass(), 1e-6);
  }
}
