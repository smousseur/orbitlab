package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The serial-equivalent view of a stack (spec {@code docs/etagement/04-conception-L2.md} §3.1).
 * {@code PropellantBudget} reasons in Tsiolkovsky terms, one stage jettisoned before the next
 * ignites; a parallel block is one burn, so it must be handed one stage.
 */
class StagingPlanFoldTest {

  private static final AscentProfile FULL_THRUST = new AscentProfile(7.0, 3.0, 2.0);

  @Test
  void aSequentialLauncherIsHandedBackUnchanged() {
    List<StageModel> stages = List.of(core(411_000), upper());

    assertSame(stages, StagingPlan.foldParallelBlock(stages, FULL_THRUST));
  }

  @Test
  void aParallelBlockBecomesOneStage() {
    List<StageModel> folded =
        StagingPlan.foldParallelBlock(List.of(boosters(), core(411_000), upper()), FULL_THRUST);

    assertEquals(2, folded.size());
    assertEquals(StageRole.CORE, folded.getFirst().capabilities().role());
  }

  @Test
  void theFoldedStageCarriesTheSumsAndTheEffectiveIsp() {
    StageModel block =
        StagingPlan.foldParallelBlock(List.of(boosters(), core(411_000), upper()), FULL_THRUST)
            .getFirst();

    assertEquals(66_000, block.dryMass(), 0.0);
    assertEquals(1_233_000, block.propellantCapacity(), 0.0);
    assertEquals(22_800_000, block.propulsion().thrust(), 0.0);
    assertEquals(296.0, block.propulsion().isp(), 0.0, "equal Isps aggregate to themselves");
  }

  @Test
  void aThrottledCoreLowersTheBlockThrustAndKeepsTheIsp() {
    StageModel block =
        StagingPlan.foldParallelBlock(
                List.of(boosters(), core(411_000), upper()), new AscentProfile(7.0, 3.0, 2.0, 0.5))
            .getFirst();

    assertEquals(15_200_000 + 0.5 * 7_600_000, block.propulsion().thrust(), 1e-6);
    assertEquals(296.0, block.propulsion().isp(), 1e-9);
  }

  @Test
  void theStageAboveTheBlockIsUntouched() {
    List<StageModel> stages = List.of(boosters(), core(411_000), upper());

    assertSame(stages.getLast(), StagingPlan.foldParallelBlock(stages, FULL_THRUST).getLast());
  }

  /** Falcon Heavy side cores as {@code L2} declares them: per exemplar, flown in two. */
  private static StageModel boosters() {
    return new StageModel(
        "Boosters",
        22_000,
        411_000,
        new PropulsionSystem(296, 7_600_000),
        capabilities(StageRole.BOOSTER),
        null,
        2);
  }

  private static StageModel core(double capacity) {
    return new StageModel(
        "Core",
        22_000,
        capacity,
        new PropulsionSystem(296, 7_600_000),
        capabilities(StageRole.CORE));
  }

  private static StageModel upper() {
    return new StageModel(
        "Upper",
        4_000,
        107_500,
        new PropulsionSystem(348, 981_000),
        new StageCapabilities(
            IgnitionMode.AIRSTART,
            2,
            ShutdownMode.COMMANDED,
            PropellantType.CRYOGENIC,
            7_200.0,
            StageRole.UPPER));
  }

  private static StageCapabilities capabilities(StageRole role) {
    return new StageCapabilities(
        IgnitionMode.GROUND, 0, ShutdownMode.COMMANDED, PropellantType.CRYOGENIC, 0.0, role);
  }
}
