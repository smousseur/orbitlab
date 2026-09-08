package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;

/**
 * The third burn an ascent gains when the boosters run dry before the core (spec {@code
 * docs/etagement/03-conception-L1.md} §3.5). {@link AscentPlanTest} pins the other half of the
 * contract: without a core phase, the very same accessors reproduce the pre-split date chain
 * epsilon by epsilon.
 */
class AscentPlanCorePhaseTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static final double EPS = 1.0e-3;
  private static final double BURN1 = 100.0;
  private static final double CORE_BURN = 40.0;
  private static final double INTERSTAGE_COAST = 2.0;

  @Test
  void withoutACorePhase_thePlanSaysSo() {
    assertFalse(plan(0.0, null).hasCorePhase());
  }

  @Test
  void withACorePhase_thePlanCarriesTheStageThatFliesIt() {
    AscentPlan plan = plan(CORE_BURN, coreStage());

    assertTrue(plan.hasCorePhase());
    assertEquals(StageRole.CORE, plan.coreStage().role());
  }

  @Test
  void coreIgnitionFollowsTheBoosterJettisonBySeparationCoastThenSettling() {
    AscentPlan plan = plan(CORE_BURN, coreStage());

    assertEquals(
        plan.jettisonDate().shiftedBy(AscentPlan.BOOSTER_SEPARATION_COAST).shiftedBy(EPS),
        plan.coreIgnitionDate());
  }

  @Test
  void coreJettisonFollowsItsOwnBurn() {
    AscentPlan plan = plan(CORE_BURN, coreStage());

    assertEquals(
        plan.coreIgnitionDate().shiftedBy(CORE_BURN).shiftedBy(EPS), plan.coreJettisonDate());
  }

  @Test
  void secondIgnitionHangsOffTheCoreJettisonWhenThereIsOne() {
    AscentPlan plan = plan(CORE_BURN, coreStage());

    assertEquals(
        plan.coreJettisonDate().shiftedBy(INTERSTAGE_COAST).shiftedBy(EPS),
        plan.secondIgnitionDate());
  }

  @Test
  void secondIgnitionHangsOffTheBoosterJettisonWhenThereIsNoCorePhase() {
    AscentPlan plan = plan(0.0, null);

    assertEquals(
        plan.jettisonDate().shiftedBy(INTERSTAGE_COAST).shiftedBy(EPS), plan.secondIgnitionDate());
  }

  @Test
  void stagingCompletesOnlyOnceTheCoreIsDroppedToo() {
    assertEquals(
        BURN1 + AscentPlan.BOOSTER_SEPARATION_COAST + CORE_BURN + INTERSTAGE_COAST,
        plan(CORE_BURN, coreStage()).stagingCompleteTime(),
        1e-12);
    assertEquals(BURN1 + INTERSTAGE_COAST, plan(0.0, null).stagingCompleteTime(), 1e-12);
  }

  private static AscentPlan plan(double coreBurnDuration, ActiveStageInfo coreStage) {
    VehicleStack stack = splitStack();
    ActiveStageInfo block = stack.resolveActiveStage(stack.getMass());
    ActiveStageInfo upper = stack.resolveActiveStage(55_000);
    return new AscentPlan(
        AbsoluteDate.J2000_EPOCH,
        400.0,
        0.32,
        BURN1,
        coreBurnDuration,
        INTERSTAGE_COAST,
        100.0,
        30.0,
        block,
        coreStage,
        upper,
        null);
  }

  private static ActiveStageInfo coreStage() {
    return splitStack().resolveActiveStage(175_000);
  }

  private static VehicleStack splitStack() {
    List<Vehicle> vehicles =
        List.of(
            new LaunchVehicle(20_000, 400_000, 400_000, new PropulsionSystem(300, 2_000_000)),
            new LaunchVehicle(20_000, 200_000, 200_000, new PropulsionSystem(300, 500_000)),
            new LaunchVehicle(4_000, 50_000, 50_000, new PropulsionSystem(450, 100_000)),
            new Spacecraft(1_000, 0, new PropulsionSystem(300, 3_000)));
    return new VehicleStack(
        vehicles,
        new StagingPlan(
            List.of(StageRole.BOOSTER, StageRole.CORE, StageRole.UPPER, StageRole.KICK),
            new StagingPlan.ParallelBlock(0, false, 1.0, 100_000)));
  }
}
