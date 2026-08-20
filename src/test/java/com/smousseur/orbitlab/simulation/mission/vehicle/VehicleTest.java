package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.orekit.utils.Constants;

class VehicleTest {

  // --- PropulsionSystem ---

  @Test
  void propulsionSystem_massBurnt_matchesRocketEquation() {
    // massBurnt = (thrust / (isp * G0)) * duration
    PropulsionSystem p = new PropulsionSystem(300, 8_400_000);
    double flowRate = 8_400_000 / (300 * Constants.G0_STANDARD_GRAVITY);
    double expected = flowRate * 10.0; // 10 seconds
    assertEquals(expected, p.massBurnt(10.0), 1e-6);
  }

  @Test
  void propulsionSystem_massBurnt_zeroDuration_returnsZero() {
    assertEquals(0.0, new PropulsionSystem(300, 8_400_000).massBurnt(0.0), 1e-10);
  }

  @Test
  void propulsionSystem_massBurnt_isLinearInDuration() {
    PropulsionSystem p = PropulsionSystem.getLauncherStage1Propulsion();
    double m1 = p.massBurnt(1.0);
    double m10 = p.massBurnt(10.0);
    assertEquals(10 * m1, m10, 1e-6);
  }

  @Test
  void propulsionSystem_higherIsp_lessConsumption_sameDuration() {
    PropulsionSystem lowIsp = new PropulsionSystem(300, 1_000_000);
    PropulsionSystem highIsp = new PropulsionSystem(400, 1_000_000);
    assertTrue(
        lowIsp.massBurnt(60) > highIsp.massBurnt(60), "Higher Isp should consume less propellant");
  }

  // --- LaunchVehicle ---

  @Test
  void launchVehicle_getMass_equalsDryPlusPropellant() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage1Vehicle();
    assertEquals(v.dryMass() + v.propellantLoad(), v.getMass(), 1e-6);
  }

  @Test
  void launchVehicle_stage1_knownValues() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage1Vehicle();
    assertEquals(27_000, v.dryMass(), 1e-6);
    assertEquals(425_000, v.propellantLoad(), 1e-6);
    assertEquals(452_000, v.getMass(), 1e-6);
  }

  @Test
  void launchVehicle_stage2_knownValues() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage2Vehicle();
    assertEquals(5_000, v.dryMass(), 1e-6);
    assertEquals(134_000, v.propellantLoad(), 1e-6);
    assertEquals(139_000, v.getMass(), 1e-6);
  }

  @Test
  void singleVehicle_resolveActiveStage_returnsSelf() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage1Vehicle();
    ActiveStageInfo info = v.resolveActiveStage(v.getMass());
    assertEquals(0, info.stageIndex());
    assertSame(v, info.vehicle());
    assertEquals(0, info.massAbove(), 1e-6);
  }

  // --- VehicleStack ---

  @Test
  void vehicleStack_getMass_sumOfAllStages() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    VehicleStack stack = new VehicleStack(List.of(s1, s2));
    assertEquals(s1.getMass() + s2.getMass(), stack.getMass(), 1e-6);
  }

  @Test
  void vehicleStack_dryMass_sumOfAllDryMasses() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    VehicleStack stack = new VehicleStack(List.of(s1, s2));
    assertEquals(s1.dryMass() + s2.dryMass(), stack.dryMass(), 1e-6);
  }

  @Test
  void vehicleStack_propulsion_delegatesToFirstStage() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    VehicleStack stack = new VehicleStack(List.of(s1, s2));
    assertEquals(s1.propulsion().isp(), stack.propulsion().isp(), 1e-6);
    assertEquals(s1.propulsion().thrust(), stack.propulsion().thrust(), 1e-6);
  }

  // --- resolveActiveStage ---

  @Test
  void vehicleStack_resolveActiveStage_fullMass_returnsFirstStage() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    ActiveStageInfo info = stack.resolveActiveStage(stack.getMass());
    assertEquals(0, info.stageIndex());
    assertSame(s1, info.vehicle());
    assertEquals(s2.getMass() + sc.getMass(), info.massAbove(), 1e-6);
    assertEquals(s2.dryMass() + sc.dryMass(), info.dryMassAbove(), 1e-6);
  }

  @Test
  void vehicleStack_resolveActiveStage_afterJettison_returnsSecondStage() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    // After jettison of vehicle 1, mass = s2.getMass() + sc.getMass()
    double massAfterJettison = s2.getMass() + sc.getMass();
    ActiveStageInfo info = stack.resolveActiveStage(massAfterJettison);
    assertEquals(1, info.stageIndex());
    assertSame(s2, info.vehicle());
    assertEquals(sc.getMass(), info.massAbove(), 1e-6);
  }

  @Test
  void vehicleStack_resolveActiveStage_spacecraftOnly_returnsTopStage() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    ActiveStageInfo info = stack.resolveActiveStage(sc.getMass());
    assertEquals(2, info.stageIndex());
    assertSame(sc, info.vehicle());
    assertEquals(0, info.massAbove(), 1e-6);
  }

  @Test
  void vehicleStack_resolveActiveStage_massAfterJettison_equalsReferenceAbove() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    ActiveStageInfo stage1 = stack.resolveActiveStage(stack.getMass());
    assertEquals(s2.getMass() + sc.getMass(), stage1.massAfterJettison(), 1e-6);

    // Chained resolution: resolve next vehicle from massAfterJettison
    ActiveStageInfo stage2 = stack.resolveActiveStage(stage1.massAfterJettison());
    assertEquals(1, stage2.stageIndex());
    assertEquals(sc.getMass(), stage2.massAfterJettison(), 1e-6);
  }

  @Test
  void vehicleStack_resolveActiveStage_remainingFuel() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    // At full mass, remaining fuel of vehicle 1 = propellant capacity of vehicle 1
    ActiveStageInfo info = stack.resolveActiveStage(stack.getMass());
    assertEquals(s1.propellantLoad(), info.remainingFuel(stack.getMass()), 1e-6);

    // After burning some fuel
    double partialMass = stack.getMass() - 100_000;
    ActiveStageInfo info2 = stack.resolveActiveStage(partialMass);
    assertEquals(s1.propellantLoad() - 100_000, info2.remainingFuel(partialMass), 1e-6);
  }

  @Test
  void vehicleStack_resolveActiveStage_remainingDryMass() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    ActiveStageInfo stage1 = stack.resolveActiveStage(stack.getMass());
    assertEquals(s1.dryMass() + s2.dryMass() + sc.dryMass(), stage1.remainingDryMass(), 1e-6);

    ActiveStageInfo stage2 = stack.resolveActiveStage(stage1.massAfterJettison());
    assertEquals(s2.dryMass() + sc.dryMass(), stage2.remainingDryMass(), 1e-6);
  }

  @Test
  void vehicleStack_resolveActiveStage_depletionFloor() {
    LaunchVehicle s1 = LaunchVehicle.getLauncherStage1Vehicle();
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    // Stage 1 floor: stage 1 fully drained, upper stack at reference mass.
    ActiveStageInfo stage1 = stack.resolveActiveStage(stack.getMass());
    assertEquals(s1.dryMass() + s2.getMass() + sc.getMass(), stage1.depletionFloor(), 1e-6);

    // Stage 2 floor after jettison: stage 2 fully drained, payload at reference mass.
    ActiveStageInfo stage2 = stack.resolveActiveStage(stage1.massAfterJettison());
    assertEquals(s2.dryMass() + sc.getMass(), stage2.depletionFloor(), 1e-6);
  }

  /**
   * Separation boundary of the split GEO profile (spec 06 I5 risk): with a nearly-empty upper
   * stage, jettisoning to the exact reference mass of the stack above must activate the payload's
   * kick motor with its full AKM load available.
   */
  @Test
  void vehicleStack_separationBoundary_activatesKickStage() {
    LaunchVehicle s2 = new LaunchVehicle(4_000, 107_500, 500, new PropulsionSystem(348, 981_000));
    Spacecraft akmSat = new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));
    VehicleStack stack = new VehicleStack(List.of(s2, akmSat));

    double beforeSeparation = s2.getMass() + akmSat.getMass();
    ActiveStageInfo active = stack.resolveActiveStage(beforeSeparation);
    assertSame(s2, active.vehicle(), "nearly-empty upper stage must still be the active one");

    double afterSeparation = active.massAfterJettison();
    assertEquals(akmSat.getMass(), afterSeparation, 1e-9);

    ActiveStageInfo kick = stack.resolveActiveStage(afterSeparation);
    assertSame(akmSat, kick.vehicle(), "the payload must be active right at the boundary mass");
    assertEquals(1_500, kick.remainingFuel(afterSeparation), 1e-9);
    assertEquals(400, kick.propulsion().thrust(), 1e-9);
  }

  // --- aerodynamics (PHY-1 / L1, spec docs/atmosphere/04-conception-L1.md section 5.3) ---

  /**
   * The aerodynamics follows the active stage, and <b>changes at the jettison</b>. The continuum-flow
   * section of a first stage giving way to the free-molecular section of an upper stage is an
   * assertion here, not an intention stated in a comment.
   */
  @Test
  void aerodynamics_followTheActiveStage_andChangeAtJettison() {
    AerodynamicProperties s1Aero = new AerodynamicProperties(31.6, 0.4);
    AerodynamicProperties s2Aero = new AerodynamicProperties(10.5, 2.2);
    LaunchVehicle s1 =
        new LaunchVehicle(
            66_000, 1_233_000, 1_233_000, new PropulsionSystem(296, 22_800_000), s1Aero);
    LaunchVehicle s2 =
        new LaunchVehicle(
            4_000, 107_500, 107_500, new PropulsionSystem(348, 981_000), s2Aero);
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    assertSame(s1Aero, stack.resolveActiveStage(stack.getMass()).aerodynamics());

    double afterJettison = s2.getMass() + sc.getMass();
    assertSame(s2Aero, stack.resolveActiveStage(afterJettison).aerodynamics());
  }

  /**
   * A stage that declares nothing does not drag — <b>even when another stage of the same stack
   * declares something</b>. There is no inheritance up or down the stack, so a partially populated
   * catalog is predictable rather than dangerous.
   */
  @Test
  void aerodynamics_absentOnOneStage_doNotLeakFromAnother() {
    LaunchVehicle s1 =
        new LaunchVehicle(
            66_000,
            1_233_000,
            1_233_000,
            new PropulsionSystem(296, 22_800_000),
            new AerodynamicProperties(31.6, 0.4));
    LaunchVehicle s2 = LaunchVehicle.getLauncherStage2Vehicle();
    Spacecraft sc = Spacecraft.LEGACY;
    VehicleStack stack = new VehicleStack(List.of(s1, s2, sc));

    assertNotNull(stack.resolveActiveStage(stack.getMass()).aerodynamics());
    assertNull(
        stack.resolveActiveStage(s2.getMass() + sc.getMass()).aerodynamics(),
        "an undeclared stage flies its phase without drag");
  }

  /**
   * {@code VehicleStack} does not override {@link Vehicle#aerodynamics()}, and the {@code null} it
   * inherits is the honest answer: a stack has no single frontal area, and nothing in production
   * asks one for it — drag resolves through {@code resolveActiveStage}. An override would be
   * unreachable code stating something false about the model.
   */
  @Test
  void vehicleStack_aerodynamics_isNull_becauseAStackHasNoFrontalArea() {
    LaunchVehicle s1 =
        new LaunchVehicle(
            66_000,
            1_233_000,
            1_233_000,
            new PropulsionSystem(296, 22_800_000),
            new AerodynamicProperties(31.6, 0.4));
    VehicleStack stack = new VehicleStack(List.of(s1, Spacecraft.LEGACY));

    assertNull(stack.aerodynamics());
    assertNotNull(stack.resolveActiveStage(stack.getMass()).aerodynamics());
  }

  /** The catalog values are wired end to end: model to flying instance to active stage. */
  @Test
  void catalogAerodynamics_reachTheFlyingStack() {
    VehicleStack stack =
        LaunchConfiguration.fullyLoaded(
                com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers.FALCON_HEAVY,
                com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads.GEO_SAT
                    .toSpacecraft(2_000, 2_000))
            .toVehicleStack();

    AerodynamicProperties liftOff = stack.resolveActiveStage(stack.getMass()).aerodynamics();
    assertNotNull(liftOff);
    assertEquals(31.6, liftOff.crossSection(), 1e-9);
    assertEquals(0.4, liftOff.dragCoefficient(), 1e-9);

    AerodynamicProperties payload =
        stack.resolveActiveStage(stack.vehicles().getLast().getMass()).aerodynamics();
    assertNotNull(payload);
    assertEquals(6.25, payload.crossSection(), 1e-9);
    assertEquals(291.0, payload.ballisticCoefficient(4_000), 1.0);
  }
}
