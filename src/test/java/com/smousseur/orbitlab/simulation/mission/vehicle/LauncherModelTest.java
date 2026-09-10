package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LauncherModelTest {

  private static final StageModel STAGE_1 =
      new StageModel(
          "S1",
          10_000,
          100_000,
          new PropulsionSystem(300, 1_000_000),
          new StageCapabilities(
              IgnitionMode.GROUND,
              0,
              ShutdownMode.COMMANDED,
              PropellantType.CRYOGENIC,
              0.0,
              StageRole.CORE));

  private static final StageModel STAGE_2 =
      new StageModel(
          "S2",
          2_000,
          20_000,
          new PropulsionSystem(340, 100_000),
          new StageCapabilities(
              IgnitionMode.AIRSTART,
              1,
              ShutdownMode.COMMANDED,
              PropellantType.STORABLE,
              Double.POSITIVE_INFINITY,
              StageRole.UPPER));

  private static final StageModel BOOSTERS =
      new StageModel(
          "Boosters",
          5_000,
          100_000,
          new PropulsionSystem(300, 1_000_000),
          new StageCapabilities(
              IgnitionMode.GROUND,
              0,
              ShutdownMode.BURN_TO_DEPLETION,
              PropellantType.SOLID,
              0.0,
              StageRole.BOOSTER),
          null,
          2);

  private static final LauncherModel MODEL =
      new LauncherModel(
          "TEST_LAUNCHER", "Test launcher", List.of(STAGE_1, STAGE_2), new AscentProfile(7, 3, 2));

  private static final Spacecraft PAYLOAD = Spacecraft.LEGACY;

  @Test
  void instantiate_stackCarriesTheStagingPlan() {
    VehicleStack stack = MODEL.instantiate(new double[] {50_000, 10_000}, PAYLOAD);

    assertEquals(0, stack.stagingPlan().indexOf(StageRole.CORE));
    assertEquals(1, stack.stagingPlan().indexOf(StageRole.UPPER));
    assertFalse(stack.stagingPlan().hasParallelBlock());
  }

  @Test
  void instantiate_withBoosters_stackCarriesAParallelBlock() {
    LauncherModel strapOn =
        new LauncherModel(
            "STRAP_ON",
            "Strap-on",
            List.of(BOOSTERS, STAGE_1, STAGE_2),
            new AscentProfile(7, 3, 2));

    VehicleStack stack = strapOn.instantiate(new double[] {200_000, 100_000, 10_000}, PAYLOAD);

    assertTrue(stack.stagingPlan().hasParallelBlock());
    assertEquals(0, stack.stagingPlan().parallelBlock().bottomIndex());
    assertEquals(2, stack.stagingPlan().indexOf(StageRole.UPPER));
  }

  /**
   * A fixture stating no height gets the Falcon Heavy's, because that is the mesh the renderer
   * falls back to when a launcher has no row of its own — 100 m, what every vehicle used to be
   * drawn at, would be a number known to be nobody's (spec {@code docs/etagement/01-decoupage.md}
   * §3.8).
   */
  @Test
  void aLauncherStatingNoHeightTakesTheOneItsFallbackMeshIsDrawnAt() {
    assertEquals(70.0, MODEL.heightMeters(), 1e-9);
    assertEquals(LauncherModel.DEFAULT_HEIGHT_METERS, MODEL.heightMeters(), 1e-9);
  }

  @Test
  void nonPositiveHeight_rejectedAtConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LauncherModel(
                "BAD", "Bad", List.of(STAGE_1, STAGE_2), new AscentProfile(7, 3, 2), 0.0));
  }

  @Test
  void throttleWithoutBoosters_rejectedAtConstruction() {
    assertThrows(
        OrbitlabException.class,
        () ->
            new LauncherModel(
                "BAD", "Bad", List.of(STAGE_1, STAGE_2), new AscentProfile(7, 3, 2, 0.8)));
  }

  @Test
  void twoStagesSharingARole_rejectedAtConstruction() {
    assertThrows(
        OrbitlabException.class,
        () ->
            new LauncherModel(
                "BAD", "Bad", List.of(STAGE_1, STAGE_1, STAGE_2), new AscentProfile(7, 3, 2)));
  }

  @Test
  void coreDryingBeforeTheBoosters_rejectedAtInstantiation() {
    LauncherModel strapOn =
        new LauncherModel(
            "STRAP_ON",
            "Strap-on",
            List.of(BOOSTERS, STAGE_1, STAGE_2),
            new AscentProfile(7, 3, 2));

    assertThrows(
        OrbitlabException.class,
        () -> strapOn.instantiate(new double[] {200_000, 10_000, 10_000}, PAYLOAD));
  }

  @Test
  void instantiate_wrongLoadCount_rejected() {
    assertThrows(
        IllegalArgumentException.class, () -> MODEL.instantiate(new double[] {50_000}, PAYLOAD));
  }

  @Test
  void instantiate_stackOrder_bottomToTopThenPayload() {
    VehicleStack stack = MODEL.instantiate(new double[] {50_000, 10_000}, PAYLOAD);
    List<Vehicle> vehicles = stack.vehicles();
    assertEquals(3, vehicles.size());
    assertEquals(10_000, vehicles.get(0).dryMass(), 1e-6);
    assertEquals(2_000, vehicles.get(1).dryMass(), 1e-6);
    assertSame(PAYLOAD, vehicles.get(2));
  }

  @Test
  void instantiate_massAccounting() {
    VehicleStack stack = MODEL.instantiate(new double[] {50_000, 10_000}, PAYLOAD);
    assertEquals(10_000 + 2_000 + 150, stack.dryMass(), 1e-6);
    assertEquals(60_000, stack.propellantLoad(), 1e-6);
    assertEquals(120_000, stack.propellantCapacity(), 1e-6);
    assertEquals(72_150, stack.getMass(), 1e-6);
  }

  @Test
  void instantiate_delegatesLoadValidationToStageModel() {
    assertThrows(
        IllegalArgumentException.class,
        () -> MODEL.instantiate(new double[] {150_000, 10_000}, PAYLOAD));
  }

  @Test
  void instantiateFullyLoaded_equalsInstantiateWithCapacities() {
    VehicleStack full = MODEL.instantiateFullyLoaded(PAYLOAD);
    VehicleStack explicit = MODEL.instantiate(new double[] {100_000, 20_000}, PAYLOAD);
    assertEquals(explicit.getMass(), full.getMass(), 1e-6);
    assertEquals(explicit.propellantLoad(), full.propellantLoad(), 1e-6);
  }

  @Test
  void emptyStages_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LauncherModel("EMPTY", "Empty", List.of(), new AscentProfile(7, 3, 2)));
  }

  /**
   * Expressiveness lock (spec 07 §4.6): the capability descriptor must express Ariane 5 ECA (solid
   * boosters, non-restartable cryogenic upper stage) without any future refactor.
   */
  @Test
  void capabilityDescriptor_expressesAriane5Eca() {
    StageModel eap =
        new StageModel(
            "EAP P241",
            33_000,
            240_000,
            new PropulsionSystem(275, 7_080_000),
            new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.BURN_TO_DEPLETION,
                PropellantType.SOLID,
                0.0,
                StageRole.BOOSTER));
    StageModel epc =
        new StageModel(
            "EPC (Vulcain 2)",
            14_700,
            170_000,
            new PropulsionSystem(431, 1_390_000),
            new StageCapabilities(
                IgnitionMode.GROUND,
                0,
                ShutdownMode.COMMANDED,
                PropellantType.CRYOGENIC,
                0.0,
                StageRole.CORE));
    StageModel escA =
        new StageModel(
            "ESC-A (HM7B)",
            4_540,
            14_900,
            new PropulsionSystem(446, 67_000),
            new StageCapabilities(
                IgnitionMode.AIRSTART,
                0,
                ShutdownMode.COMMANDED,
                PropellantType.CRYOGENIC,
                3_600.0,
                StageRole.UPPER));

    assertFalse(eap.capabilities().variableLoad());
    assertEquals(0, escA.capabilities().restartCount());
    assertFalse(escA.capabilities().canCoastFor(5.25 * 3_600));
    assertTrue(epc.capabilities().variableLoad());
  }

  /** Expressiveness lock for a payload-integrated apogee kick motor. */
  @Test
  void capabilityDescriptor_expressesApogeeKickMotor() {
    StageCapabilities kickStage =
        new StageCapabilities(
            IgnitionMode.AIRSTART,
            1,
            ShutdownMode.COMMANDED,
            PropellantType.STORABLE,
            Double.POSITIVE_INFINITY,
            StageRole.KICK);
    assertTrue(kickStage.canCoastFor(5.25 * 3_600));
    assertTrue(kickStage.variableLoad());
  }
}
