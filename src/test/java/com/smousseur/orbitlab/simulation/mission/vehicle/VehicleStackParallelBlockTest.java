package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The parallel block as {@link VehicleStack} resolves it (spec {@code
 * docs/etagement/03-conception-L1.md} §3.1). Figures are synthetic and round: boosters and core
 * share one Isp, the core flows a fifth of the block, and every expected mass is exact.
 *
 * <pre>
 *   boosters ×2   dry 2 × 10 000, capacity 2 × 200 000, thrust 2 × 1 000 000, Isp 300
 *   core          dry 20 000,     thrust 500 000,       Isp 300
 *   upper         dry 4 000,      capacity 50 000
 *   payload       dry 1 000
 * </pre>
 */
class VehicleStackParallelBlockTest {

  private static final Spacecraft PAYLOAD =
      new Spacecraft(1_000, 0, new PropulsionSystem(300, 3_000));

  /** Core loaded at 200 t: it outlasts the boosters by 100 t, so the block splits in two. */
  private static final VehicleStack SPLIT = stack(200_000);

  /** Core loaded at 100 t: it runs dry with the boosters, so one jettison drops both. */
  private static final VehicleStack GROUPED = stack(100_000);

  @Test
  void liftOff_theBlockIsActive_notItsBottomEntryAlone() {
    ActiveStageInfo block = SPLIT.resolveActiveStage(695_000);

    assertEquals(0, block.stageIndex());
    assertEquals(2_500_000, block.propulsion().thrust(), 1e-6);
    assertEquals(300, block.propulsion().isp(), 1e-9);
  }

  @Test
  void split_theBlockDeclaresOnlyTheBoostersAsItsOwnDryMass() {
    ActiveStageInfo block = SPLIT.resolveActiveStage(695_000);

    assertEquals(20_000, block.dryMass(), 1e-6);
    assertEquals(175_000, block.massAfterJettison(), 1e-6);
  }

  @Test
  void split_theFloorIsTheMassAtBoosterBurnout_notAtCoreBurnout() {
    ActiveStageInfo block = SPLIT.resolveActiveStage(695_000);

    assertEquals(195_000, block.depletionFloor(), 1e-6);
    assertEquals(500_000, block.remainingFuel(695_000), 1e-6);
  }

  @Test
  void split_theBlockStaysActiveBelowTheReferenceMassOfTheStackAboveIt() {
    // The old threshold, mass of core + upper + payload at their reference loads. The block burns
    // straight through it: resolving on it would hand over to the core with the boosters still
    // firing (spec §2.2).
    ActiveStageInfo block = SPLIT.resolveActiveStage(250_000);

    assertEquals(0, block.stageIndex());
    assertEquals(2_500_000, block.propulsion().thrust(), 1e-6);
  }

  @Test
  void split_atTheJettisonMassTheCoreTakesOver() {
    ActiveStageInfo core = SPLIT.resolveActiveStage(175_000);

    assertEquals(1, core.stageIndex());
    assertEquals(500_000, core.propulsion().thrust(), 1e-6, "the core recovers its full thrust");
    assertEquals(100_000, core.remainingFuel(175_000), 1e-6);
  }

  @Test
  void grouped_theBlockDeclaresBothDryMassesAndDropsBoth() {
    ActiveStageInfo block = GROUPED.resolveActiveStage(595_000);

    assertEquals(40_000, block.dryMass(), 1e-6);
    assertEquals(55_000, block.massAfterJettison(), 1e-6);
    assertEquals(95_000, block.depletionFloor(), 1e-6);
  }

  @Test
  void grouped_atTheJettisonMassTheUpperStageTakesOver() {
    assertEquals(2, GROUPED.resolveActiveStage(55_000).stageIndex());
  }

  @Test
  void theBlockAggregatesTheFrontalSections() {
    ActiveStageInfo block = SPLIT.resolveActiveStage(695_000);

    assertEquals(34.0, block.aerodynamics().crossSection(), 1e-9, "2 × 12 + 10");
    assertEquals(0.4, block.aerodynamics().dragCoefficient(), 1e-9);
  }

  @Test
  void remainingDryMassSpansTheWholeBlock() {
    assertEquals(45_000, SPLIT.resolveActiveStage(695_000).remainingDryMass(), 1e-6);
    assertEquals(25_000, SPLIT.resolveActiveStage(175_000).remainingDryMass(), 1e-6);
  }

  @Test
  void propellantIsSplitBetweenTheTwoTanksAtTheirFlowRatio() {
    // 250 t burnt out of the block: the core flows a fifth of it, so 50 t of core and 200 t of
    // boosters.
    List<StagePropellant> perStage = SPLIT.resolveStagePropellant(445_000);

    assertEquals(200_000, perStage.get(0).residual(), 1e-6);
    assertEquals(150_000, perStage.get(1).residual(), 1e-6);
    assertEquals(50_000, perStage.get(2).residual(), 1e-6, "the upper stage is untouched");
  }

  @Test
  void afterTheBoosterJettisonTheCoreAccountsForItselfAgain() {
    List<StagePropellant> perStage = SPLIT.resolveStagePropellant(175_000);

    assertEquals(0, perStage.get(0).residual(), 1e-6);
    assertEquals(100_000, perStage.get(1).residual(), 1e-6);
  }

  @Test
  void theActiveStageCarriesItsRole() {
    assertEquals(StageRole.BOOSTER, SPLIT.resolveActiveStage(695_000).role());
    assertEquals(StageRole.CORE, SPLIT.resolveActiveStage(175_000).role());
    assertEquals(StageRole.UPPER, SPLIT.resolveActiveStage(55_000).role());
    assertEquals(StageRole.KICK, SPLIT.resolveActiveStage(1_000).role());
  }

  @Test
  void aStackAssembledByHandCarriesNoRole() {
    VehicleStack plain =
        new VehicleStack(List.of(new LaunchVehicle(1_000, 10_000, new PropulsionSystem(300, 1e6))));

    assertNull(plain.resolveActiveStage(11_000).role());
  }

  private static VehicleStack stack(double coreLoad) {
    List<StageModel> stages = List.of(boosters(), core(coreLoad), upper());
    double[] loads = {400_000, coreLoad, 50_000};
    List<Vehicle> vehicles =
        List.of(
            stages.get(0).toVehicle(loads[0]),
            stages.get(1).toVehicle(loads[1]),
            stages.get(2).toVehicle(loads[2]),
            PAYLOAD);
    return new VehicleStack(
        vehicles, StagingPlan.forLauncher(stages, loads, new AscentProfile(7.0, 3.0, 2.0)));
  }

  private static StageModel boosters() {
    return new StageModel(
        "Boosters",
        10_000,
        200_000,
        new PropulsionSystem(300, 1_000_000),
        capabilities(PropellantType.SOLID, ShutdownMode.BURN_TO_DEPLETION, StageRole.BOOSTER),
        new AerodynamicProperties(12.0, 0.4),
        2);
  }

  private static StageModel core(double capacity) {
    return new StageModel(
        "Core",
        20_000,
        capacity,
        new PropulsionSystem(300, 500_000),
        capabilities(PropellantType.CRYOGENIC, ShutdownMode.COMMANDED, StageRole.CORE),
        new AerodynamicProperties(10.0, 0.4));
  }

  private static StageModel upper() {
    return new StageModel(
        "Upper",
        4_000,
        50_000,
        new PropulsionSystem(450, 100_000),
        new StageCapabilities(
            IgnitionMode.AIRSTART,
            2,
            ShutdownMode.COMMANDED,
            PropellantType.CRYOGENIC,
            7_200.0,
            StageRole.UPPER));
  }

  private static StageCapabilities capabilities(
      PropellantType propellant, ShutdownMode shutdown, StageRole role) {
    return new StageCapabilities(IgnitionMode.GROUND, 0, shutdown, propellant, 0.0, role);
  }
}
