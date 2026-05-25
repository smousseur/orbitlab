package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.LauncherType;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import org.junit.jupiter.api.Test;

class MissionBuildVehicleTest {

  @Test
  void buildVehicleFlattensLauncherAndAppendsPayloadOnTop() {
    VehicleStack launcher = LauncherType.FALCON_HEAVY.toVehicleStack();
    Spacecraft payload = new Spacecraft(15_000, 0, PropulsionSystem.getSpacecraftPropulsion());

    VehicleStack assembled = Mission.buildVehicle(launcher, payload);

    assertEquals(3, assembled.vehicles().size(), "Two launcher stages + one payload = 3");
    assertSame(launcher.vehicles().get(0), assembled.vehicles().get(0));
    assertSame(launcher.vehicles().get(1), assembled.vehicles().get(1));
    assertSame(payload, assembled.vehicles().get(2));
  }

  @Test
  void buildVehicleProducesFlatStackNotNested() {
    VehicleStack launcher = LauncherType.FALCON_HEAVY.toVehicleStack();
    Spacecraft payload = Spacecraft.defaultPayload();

    VehicleStack assembled = Mission.buildVehicle(launcher, payload);

    for (Vehicle v : assembled.vehicles()) {
      assertFalse(
          v instanceof VehicleStack, "Assembled stack must not contain a nested VehicleStack");
    }
  }

  @Test
  void buildVehicleDoesNotMutateInputLauncher() {
    VehicleStack launcher = LauncherType.FALCON_HEAVY.toVehicleStack();
    int initialSize = launcher.vehicles().size();
    Spacecraft payload = Spacecraft.defaultPayload();

    Mission.buildVehicle(launcher, payload);

    assertEquals(initialSize, launcher.vehicles().size(), "Input launcher must be untouched");
  }

  @Test
  void resolveActiveStageSeesAllThreeStagesSequentially() {
    VehicleStack assembled =
        Mission.buildVehicle(LauncherType.FALCON_HEAVY.toVehicleStack(), Spacecraft.defaultPayload());

    double totalMass = assembled.getMass();
    LaunchVehicle s1 = LaunchVehicle.defaultStage1();
    LaunchVehicle s2 = LaunchVehicle.defaultStage2();

    // Full mass → active stage = stage 1 (bottom).
    ActiveStageInfo atLiftoff = assembled.resolveActiveStage(totalMass);
    assertEquals(0, atLiftoff.stageIndex());

    // After stage 1 is dropped → stage 2 active.
    double afterStage1 = totalMass - s1.getMass();
    ActiveStageInfo afterS1 = assembled.resolveActiveStage(afterStage1 - 1.0);
    assertEquals(1, afterS1.stageIndex());

    // After stage 2 is dropped → payload (stage 2) active.
    double afterStage2 = afterStage1 - s2.getMass();
    ActiveStageInfo afterS2 = assembled.resolveActiveStage(afterStage2 - 1.0);
    assertEquals(2, afterS2.stageIndex());
  }

  @Test
  void getDefaultVehicleMatchesFalconHeavyPlusDefaultPayload() {
    VehicleStack defaultStack = Mission.getDefaultVehicle();

    assertEquals(3, defaultStack.vehicles().size());

    Spacecraft expectedPayload = Spacecraft.defaultPayload();
    Spacecraft actualPayload = (Spacecraft) defaultStack.vehicles().get(2);

    assertEquals(expectedPayload.dryMass(), actualPayload.dryMass());
    assertEquals(expectedPayload.propellantCapacity(), actualPayload.propellantCapacity());
    assertEquals(expectedPayload.propulsion(), actualPayload.propulsion());

    LaunchVehicle expectedS1 = LaunchVehicle.defaultStage1();
    LaunchVehicle expectedS2 = LaunchVehicle.defaultStage2();
    double expectedTotal = expectedS1.getMass() + expectedS2.getMass() + expectedPayload.getMass();
    assertEquals(expectedTotal, defaultStack.getMass(), 1e-9);
  }
}
