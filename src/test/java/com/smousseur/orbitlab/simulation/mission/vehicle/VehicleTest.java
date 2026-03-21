package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

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
    assertEquals(v.dryMass() + v.propellantCapacity(), v.getMass(), 1e-6);
  }

  @Test
  void launchVehicle_stage1_knownValues() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage1Vehicle();
    assertEquals(27_000, v.dryMass(), 1e-6);
    assertEquals(425_000, v.propellantCapacity(), 1e-6);
    assertEquals(452_000, v.getMass(), 1e-6);
  }

  @Test
  void launchVehicle_stage2_knownValues() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage2Vehicle();
    assertEquals(10_000, v.dryMass(), 1e-6);
    assertEquals(134_000, v.propellantCapacity(), 1e-6);
    assertEquals(144_000, v.getMass(), 1e-6);
  }

  @Test
  void launchVehicle_getStage_returnsItself() {
    LaunchVehicle v = LaunchVehicle.getLauncherStage1Vehicle();
    assertSame(v, v.getStage(0));
    assertSame(v, v.getStage(1));
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
}
