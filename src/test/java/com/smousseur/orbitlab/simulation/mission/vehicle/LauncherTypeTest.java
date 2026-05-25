package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LauncherTypeTest {

  @Test
  void falconHeavyBuildsTwoStageStackUsingDefaultFactories() {
    VehicleStack stack = LauncherType.FALCON_HEAVY.toVehicleStack();

    assertEquals(2, stack.vehicles().size(), "Falcon Heavy must have two stages");

    LaunchVehicle expectedS1 = LaunchVehicle.defaultStage1();
    LaunchVehicle expectedS2 = LaunchVehicle.defaultStage2();

    LaunchVehicle s1 = (LaunchVehicle) stack.vehicles().get(0);
    LaunchVehicle s2 = (LaunchVehicle) stack.vehicles().get(1);

    assertEquals(expectedS1.dryMass(), s1.dryMass());
    assertEquals(expectedS1.propellantCapacity(), s1.propellantCapacity());
    assertEquals(expectedS1.propulsion(), s1.propulsion());

    assertEquals(expectedS2.dryMass(), s2.dryMass());
    assertEquals(expectedS2.propellantCapacity(), s2.propellantCapacity());
    assertEquals(expectedS2.propulsion(), s2.propulsion());
  }

  @Test
  void ariane5EcaBuildsTwoStageStackUsingArianeFactories() {
    VehicleStack stack = LauncherType.ARIANE_5_ECA.toVehicleStack();

    assertEquals(2, stack.vehicles().size(), "Ariane 5 ECA must have two stages");

    LaunchVehicle s1 = (LaunchVehicle) stack.vehicles().get(0);
    LaunchVehicle s2 = (LaunchVehicle) stack.vehicles().get(1);

    assertEquals(7_600_000, s1.propulsion().thrust(), "Stage 1 thrust matches wizard spec");
    assertEquals(300, s1.propulsion().isp());
    assertEquals(431, s2.propulsion().isp(), "Stage 2 Isp matches wizard spec");
    assertEquals(67_000, s2.propulsion().thrust());

    assertTrue(s1.dryMass() > 0);
    assertTrue(s1.propellantCapacity() > 0);
    assertTrue(s2.dryMass() > 0);
    assertTrue(s2.propellantCapacity() > 0);
  }

  @Test
  void everyLauncherHasNonEmptyModelPath() {
    for (LauncherType type : LauncherType.values()) {
      assertNotNull(type.modelPath(), "modelPath() must not be null for " + type);
      assertFalse(type.modelPath().isBlank(), "modelPath() must not be blank for " + type);
    }
  }

  @Test
  void modelPathsAreDistinctAcrossLaunchers() {
    Set<String> paths = new HashSet<>();
    for (LauncherType type : LauncherType.values()) {
      assertTrue(paths.add(type.modelPath()), "modelPath() must be unique for " + type);
    }
  }

  @Test
  void toVehicleStackProducesIndependentInstancesOnEachCall() {
    VehicleStack first = LauncherType.FALCON_HEAVY.toVehicleStack();
    VehicleStack second = LauncherType.FALCON_HEAVY.toVehicleStack();
    assertNotSame(first, second);
    assertNotSame(first.vehicles(), second.vehicles());
  }

  @Test
  void stackDoesNotIncludePayload() {
    for (LauncherType type : LauncherType.values()) {
      VehicleStack stack = type.toVehicleStack();
      for (Vehicle v : stack.vehicles()) {
        assertFalse(
            v instanceof Spacecraft,
            "Launcher stack must not contain a Spacecraft payload (" + type + ")");
      }
    }
  }
}
