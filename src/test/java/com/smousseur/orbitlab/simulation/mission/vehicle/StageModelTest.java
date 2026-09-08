package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
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

  @Test
  void multiplicity_extensiveFiguresAreAggregated() {
    StageModel boosters = boosters(4);

    assertEquals(28_000, boosters.dryMass(), 1e-6);
    assertEquals(564_000, boosters.propellantCapacity(), 1e-6);
    assertEquals(18_000_000, boosters.propulsion().thrust(), 1e-6);
  }

  @Test
  void multiplicity_intensiveFiguresAreNot() {
    StageModel boosters = boosters(4);

    assertEquals(275, boosters.propulsion().isp(), 1e-9);
    assertEquals(0.4, boosters.aerodynamics().dragCoefficient(), 1e-9);
  }

  @Test
  void multiplicity_crossSectionIsAggregated() {
    assertEquals(36.32, boosters(4).aerodynamics().crossSection(), 1e-9);
  }

  @Test
  void multiplicity_unitFiguresStayReadable() {
    StageModel boosters = boosters(4);

    assertEquals(7_000, boosters.unitDryMass(), 1e-6);
    assertEquals(141_000, boosters.unitPropellantCapacity(), 1e-6);
    assertEquals(9.08, boosters.unitAerodynamics().crossSection(), 1e-9);
  }

  @Test
  void multiplicity_toVehicleTakesTheAggregateLoad() {
    LaunchVehicle vehicle = boosters(4).toVehicle(564_000);

    assertEquals(28_000, vehicle.dryMass(), 1e-6);
    assertEquals(564_000, vehicle.propellantLoad(), 1e-6);
    assertEquals(18_000_000, vehicle.propulsion().thrust(), 1e-6);
  }

  @Test
  void multiplicity_defaultsToOne() {
    assertEquals(1, LIQUID_STAGE.multiplicity());
    assertEquals(10_000, LIQUID_STAGE.dryMass(), 1e-6);
  }

  @Test
  void multiplicity_belowOne_rejected() {
    assertThrows(IllegalArgumentException.class, () -> boosters(0));
  }

  private static StageModel boosters(int multiplicity) {
    return new StageModel(
        "P120C",
        7_000,
        141_000,
        new PropulsionSystem(275, 4_500_000),
        SOLID,
        new AerodynamicProperties(9.08, 0.4),
        multiplicity);
  }
}
