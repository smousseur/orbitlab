package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import org.junit.jupiter.api.Test;

class StageModelTest {

  private static final StageCapabilities LIQUID =
      new StageCapabilities(
          IgnitionMode.GROUND,
          0,
          ShutdownMode.COMMANDED,
          PropellantType.CRYOGENIC,
          0.0,
          StageRole.CORE);

  private static final StageCapabilities SOLID =
      new StageCapabilities(
          IgnitionMode.GROUND,
          0,
          ShutdownMode.BURN_TO_DEPLETION,
          PropellantType.SOLID,
          0.0,
          StageRole.BOOSTER);

  private static final StageModel LIQUID_STAGE =
      new StageModel("Liquid", 10_000, 100_000, new PropulsionSystem(300, 1_000_000), LIQUID);

  private static final StageModel SOLID_STAGE =
      new StageModel("Solid", 30_000, 240_000, new PropulsionSystem(275, 7_000_000), SOLID);

  @Test
  void toVehicle_partialLoad_massReflectsLoad() {
    LaunchVehicle vehicle = LIQUID_STAGE.toVehicle(40_000);
    assertEquals(10_000, vehicle.dryMass(), 1e-6);
    assertEquals(100_000, vehicle.propellantCapacity(), 1e-6);
    assertEquals(40_000, vehicle.propellantLoad(), 1e-6);
    assertEquals(50_000, vehicle.getMass(), 1e-6);
  }

  @Test
  void toVehicle_negativeOrNanLoad_rejected() {
    assertThrows(IllegalArgumentException.class, () -> LIQUID_STAGE.toVehicle(-1.0));
    assertThrows(IllegalArgumentException.class, () -> LIQUID_STAGE.toVehicle(Double.NaN));
  }

  @Test
  void toVehicle_loadAboveCapacity_rejected() {
    assertThrows(IllegalArgumentException.class, () -> LIQUID_STAGE.toVehicle(100_001));
  }

  @Test
  void toVehicle_solidStage_partialLoad_rejected() {
    assertThrows(IllegalArgumentException.class, () -> SOLID_STAGE.toVehicle(120_000));
  }

  @Test
  void toVehicle_solidStage_fullLoad_accepted() {
    LaunchVehicle vehicle = SOLID_STAGE.toVehicle(240_000);
    assertEquals(240_000, vehicle.propellantLoad(), 1e-6);
  }

  @Test
  void toVehicleFullyLoaded_massIsDryPlusCapacity() {
    LaunchVehicle vehicle = LIQUID_STAGE.toVehicleFullyLoaded();
    assertEquals(100_000, vehicle.propellantLoad(), 1e-6);
    assertEquals(110_000, vehicle.getMass(), 1e-6);
  }

  @Test
  void nonPositiveDryMass_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StageModel("Bad", 0, 100_000, new PropulsionSystem(300, 1_000_000), LIQUID));
  }
}
