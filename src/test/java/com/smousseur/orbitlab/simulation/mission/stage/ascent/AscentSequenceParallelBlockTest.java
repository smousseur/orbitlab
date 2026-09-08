package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnConstraints;
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The ascent gains two phases exactly when the boosters run dry before the core, and keeps the
 * three it always had otherwise (spec {@code docs/etagement/03-conception-L1.md} §3.5).
 */
class AscentSequenceParallelBlockTest {

  private static final AscentProfile PROFILE = new AscentProfile(7.0, 3.0, 2.0);
  private static final GravityTurnConstraints CONSTRAINTS =
      GravityTurnConstraints.forTarget(400_000);

  @Test
  void sequentialStack_threePhases() {
    List<MissionStage> ascent = ascentOf(sequentialStack());

    assertEquals(3, ascent.size());
    assertInstanceOf(GravityTurnFirstBurnStage.class, ascent.get(0));
    assertInstanceOf(StageSeparationStage.class, ascent.get(1));
    assertInstanceOf(GravityTurnSecondBurnStage.class, ascent.get(2));
  }

  @Test
  void groupedBlock_threePhases_becauseOneJettisonDropsBoth() {
    assertEquals(3, ascentOf(blockStack(true)).size());
  }

  @Test
  void splitBlock_fivePhases() {
    List<MissionStage> ascent = ascentOf(blockStack(false));

    assertEquals(5, ascent.size());
    assertInstanceOf(GravityTurnFirstBurnStage.class, ascent.get(0));
    assertInstanceOf(StageSeparationStage.class, ascent.get(1));
    assertInstanceOf(GravityTurnCoreBurnStage.class, ascent.get(2));
    assertInstanceOf(StageSeparationStage.class, ascent.get(3));
    assertInstanceOf(GravityTurnSecondBurnStage.class, ascent.get(4));
  }

  @Test
  void splitBlock_thePhasesAreNamedApart() {
    List<MissionStage> ascent = ascentOf(blockStack(false));

    assertEquals(AscentSequence.BOOSTER_SEPARATION_NAME, ascent.get(1).getName());
    assertEquals(AscentSequence.CORE_BURN_NAME, ascent.get(2).getName());
    assertEquals(AscentSequence.SEPARATION_NAME, ascent.get(3).getName());
  }

  @Test
  void theOptimizationChainHasTheSameShapeAsTheReplayChain() {
    Vehicle vehicle = blockStack(false);
    List<MissionStage> replay = ascentOf(vehicle);
    List<MissionStage> optimize =
        ((GravityTurnFirstBurnStage) replay.getFirst())
            .optimizationChain(vehicle, new AscentPlanRef(), null);

    assertEquals(replay.size(), optimize.size());
    for (int i = 0; i < replay.size(); i++) {
      assertEquals(replay.get(i).getClass(), optimize.get(i).getClass(), "phase " + i);
    }
  }

  private static List<MissionStage> ascentOf(Vehicle vehicle) {
    return AscentSequence.gravityTurn(vehicle, PROFILE, CONSTRAINTS, 5.2);
  }

  private static VehicleStack sequentialStack() {
    List<Vehicle> vehicles =
        List.of(
            new LaunchVehicle(20_000, 400_000, 400_000, new PropulsionSystem(300, 2_000_000)),
            new LaunchVehicle(4_000, 50_000, 50_000, new PropulsionSystem(450, 100_000)),
            new Spacecraft(1_000, 0, new PropulsionSystem(300, 3_000)));
    return new VehicleStack(
        vehicles, new StagingPlan(List.of(StageRole.CORE, StageRole.UPPER, StageRole.KICK), null));
  }

  private static VehicleStack blockStack(boolean grouped) {
    double coreLoad = grouped ? 100_000 : 200_000;
    List<Vehicle> vehicles =
        List.of(
            new LaunchVehicle(20_000, 400_000, 400_000, new PropulsionSystem(300, 2_000_000)),
            new LaunchVehicle(20_000, coreLoad, coreLoad, new PropulsionSystem(300, 500_000)),
            new LaunchVehicle(4_000, 50_000, 50_000, new PropulsionSystem(450, 100_000)),
            new Spacecraft(1_000, 0, new PropulsionSystem(300, 3_000)));
    return new VehicleStack(
        vehicles,
        new StagingPlan(
            List.of(StageRole.BOOSTER, StageRole.CORE, StageRole.UPPER, StageRole.KICK),
            new StagingPlan.ParallelBlock(0, grouped, 1.0, grouped ? 0.0 : 100_000)));
  }
}
