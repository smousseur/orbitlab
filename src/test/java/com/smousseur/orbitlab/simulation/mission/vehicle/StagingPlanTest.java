package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The staging plan is where the parallel block is decided (spec {@code
 * docs/etagement/03-conception-L1.md} §3.2, §3.3). The figures below are deliberately synthetic and
 * round: the boosters and the core share one Isp, so the flow ratio is the thrust ratio and every
 * expected value is exact.
 */
class StagingPlanTest {

  private static final AscentProfile FULL_THRUST = new AscentProfile(7.0, 3.0, 2.0);

  @Test
  void noBoosterStage_noBlock() {
    StagingPlan plan = plan(List.of(core(200_000), upper()), new double[] {200_000, 50_000});

    assertFalse(plan.hasParallelBlock());
  }

  @Test
  void boosterStage_blockSitsAtTheBottomWithTheCoreAboveIt() {
    StagingPlan plan = splitStack(200_000, FULL_THRUST);

    assertTrue(plan.hasParallelBlock());
    assertEquals(0, plan.parallelBlock().bottomIndex());
  }

  @Test
  void proRataLoads_coreEmptiesWithTheBoosters_jettisonIsGrouped() {
    // The core flows a quarter of the block, so 400 t of boosters drain exactly 100 t of core.
    StagingPlan plan = splitStack(100_000, FULL_THRUST);

    assertTrue(plan.parallelBlock().groupedJettison());
    assertEquals(0.0, plan.parallelBlock().coreLeftAtBoosterBurnout(), 0.0);
  }

  @Test
  void coreOutlastsTheBoosters_jettisonIsSplitInTwo() {
    StagingPlan plan = splitStack(200_000, FULL_THRUST);

    assertFalse(plan.parallelBlock().groupedJettison());
    assertEquals(100_000, plan.parallelBlock().coreLeftAtBoosterBurnout(), 1e-6);
  }

  @Test
  void throttledCore_burnsLessDuringTheSharedPhaseAndKeepsMore() {
    StagingPlan plan = splitStack(200_000, new AscentProfile(7.0, 3.0, 2.0, 0.5));

    assertEquals(150_000, plan.parallelBlock().coreLeftAtBoosterBurnout(), 1e-6);
    assertEquals(0.5, plan.parallelBlock().coreThrottle(), 1e-9);
  }

  @Test
  void coreEmptiesBeforeTheBoosters_rejected() {
    // A thrust loss in the middle of the shared phase: no ConstantThrustManeuver expresses it.
    assertThrows(OrbitlabException.class, () -> splitStack(50_000, FULL_THRUST));
  }

  @Test
  void throttleWithoutBoosters_rejected() {
    assertThrows(
        OrbitlabException.class,
        () ->
            plan(
                List.of(core(200_000), upper()),
                new double[] {200_000, 50_000},
                new AscentProfile(7.0, 3.0, 2.0, 0.5)));
  }

  @Test
  void boosterNotUnderACore_rejected() {
    assertThrows(
        OrbitlabException.class,
        () -> plan(List.of(boosters(), upper()), new double[] {400_000, 50_000}));
  }

  @Test
  void twoEntriesSharingARole_rejected() {
    assertThrows(
        OrbitlabException.class,
        () ->
            plan(
                List.of(core(200_000), core(100_000), upper()),
                new double[] {200_000, 100_000, 50_000}));
  }

  @Test
  void rolesAreResolvedByIndex_payloadIncluded() {
    StagingPlan plan = splitStack(200_000, FULL_THRUST);

    assertEquals(StageRole.BOOSTER, plan.roleAt(0));
    assertEquals(StageRole.CORE, plan.roleAt(1));
    assertEquals(StageRole.UPPER, plan.roleAt(2));
    assertEquals(StageRole.KICK, plan.roleAt(3), "the payload closes the stack");
  }

  @Test
  void indexOfRole_findsTheUpperStageWhateverTheStackShape() {
    assertEquals(2, splitStack(200_000, FULL_THRUST).indexOf(StageRole.UPPER));
    assertEquals(
        1,
        plan(List.of(core(200_000), upper()), new double[] {200_000, 50_000})
            .indexOf(StageRole.UPPER));
  }

  @Test
  void indexOfAbsentRole_rejected() {
    StagingPlan plan = plan(List.of(core(200_000), upper()), new double[] {200_000, 50_000});

    assertThrows(OrbitlabException.class, () -> plan.indexOf(StageRole.BOOSTER));
  }

  @Test
  void unknownPlan_declaresNoRoleAndNoBlock() {
    StagingPlan plan = StagingPlan.unknown(3);

    assertFalse(plan.hasParallelBlock());
    assertFalse(plan.declaresRoles());
    assertNull(plan.roleAt(0));
  }

  private static StagingPlan splitStack(double coreLoad, AscentProfile profile) {
    return plan(
        List.of(boosters(), core(coreLoad), upper()),
        new double[] {400_000, coreLoad, 50_000},
        profile);
  }

  private static StagingPlan plan(List<StageModel> stages, double[] loads) {
    return plan(stages, loads, FULL_THRUST);
  }

  private static StagingPlan plan(List<StageModel> stages, double[] loads, AscentProfile profile) {
    return StagingPlan.forLauncher(stages, loads, profile);
  }

  private static StageModel boosters() {
    return new StageModel(
        "Boosters",
        10_000,
        200_000,
        new PropulsionSystem(300, 1_000_000),
        capabilities(PropellantType.SOLID, ShutdownMode.BURN_TO_DEPLETION, StageRole.BOOSTER),
        null,
        2);
  }

  private static StageModel core(double capacity) {
    return new StageModel(
        "Core",
        20_000,
        capacity,
        new PropulsionSystem(300, 500_000),
        capabilities(PropellantType.CRYOGENIC, ShutdownMode.COMMANDED, StageRole.CORE));
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
