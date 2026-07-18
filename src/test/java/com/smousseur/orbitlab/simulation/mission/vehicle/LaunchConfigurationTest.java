package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LaunchConfigurationTest {

  private static final Spacecraft PAYLOAD = Spacecraft.getSpacecraft();

  @Test
  void wrongLoadCount_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LaunchConfiguration(Launchers.FALCON_HEAVY, new double[] {600_000}, PAYLOAD));
  }

  @Test
  void toVehicleStack_matchesLauncherInstantiate() {
    double[] loads = {600_000, 50_000};
    LaunchConfiguration configuration =
        new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, PAYLOAD);
    VehicleStack fromConfiguration = configuration.toVehicleStack();
    VehicleStack fromLauncher = Launchers.FALCON_HEAVY.instantiate(loads, PAYLOAD);
    assertEquals(fromLauncher.getMass(), fromConfiguration.getMass(), 1e-6);
    assertEquals(fromLauncher.propellantLoad(), fromConfiguration.propellantLoad(), 1e-6);
  }

  @Test
  void ascentProfile_delegatesToLauncher() {
    LaunchConfiguration configuration =
        new LaunchConfiguration(Launchers.FALCON_HEAVY, new double[] {600_000, 50_000}, PAYLOAD);
    assertSame(Launchers.FALCON_HEAVY.ascentProfile(), configuration.ascentProfile());
  }

  @Test
  void fullyLoaded_loadsEqualCapacities() {
    LaunchConfiguration configuration =
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, PAYLOAD);
    assertEquals(
        1_233_000 + 107_500 + PAYLOAD.propellantLoad(),
        configuration.toVehicleStack().propellantLoad(),
        1e-6);
  }

  @Test
  void propellantLoads_isDefensivelyCopied() {
    double[] loads = {600_000, 50_000};
    LaunchConfiguration configuration =
        new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, PAYLOAD);
    loads[0] = 0.0;
    assertEquals(600_000, configuration.propellantLoads()[0], 1e-6);
    configuration.propellantLoads()[1] = 0.0;
    assertEquals(50_000, configuration.propellantLoads()[1], 1e-6);
  }
}
