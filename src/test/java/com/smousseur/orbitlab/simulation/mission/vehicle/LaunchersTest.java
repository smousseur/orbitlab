package com.smousseur.orbitlab.simulation.mission.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.IgnitionMode;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageCapabilities;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import org.junit.jupiter.api.Test;

class LaunchersTest {

  @Test
  void byId_falconHeavy_returnsCatalogConstant() {
    assertSame(Launchers.FALCON_HEAVY, Launchers.byId("FALCON_HEAVY"));
  }

  @Test
  void byId_unknownId_rejected() {
    assertThrows(IllegalArgumentException.class, () -> Launchers.byId("SATURN_V"));
  }

  @Test
  void all_containsFalconHeavy() {
    assertTrue(Launchers.all().contains(Launchers.FALCON_HEAVY));
  }

  @Test
  void falconHeavy_knownFigures() {
    List<StageModel> stages = Launchers.FALCON_HEAVY.stages();
    assertEquals(2, stages.size());

    StageModel s1 = stages.get(0);
    assertEquals(66_000, s1.dryMass(), 1e-6);
    assertEquals(1_233_000, s1.propellantCapacity(), 1e-6);
    assertEquals(311, s1.propulsion().isp(), 1e-6);
    assertEquals(22_800_000, s1.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.GROUND, s1.capabilities().ignition());
    assertEquals(StageRole.CORE, s1.capabilities().role());

    StageModel s2 = stages.get(1);
    assertEquals(4_000, s2.dryMass(), 1e-6);
    assertEquals(107_500, s2.propellantCapacity(), 1e-6);
    assertEquals(348, s2.propulsion().isp(), 1e-6);
    assertEquals(981_000, s2.propulsion().thrust(), 1e-6);
    assertEquals(IgnitionMode.AIRSTART, s2.capabilities().ignition());
    assertEquals(2, s2.capabilities().restartCount());
    assertEquals(StageRole.UPPER, s2.capabilities().role());
  }

  @Test
  void falconHeavy_upperStageCoast_allowsParkingButNotGtoCoast() {
    StageCapabilities s2 = Launchers.FALCON_HEAVY.stages().get(1).capabilities();
    assertTrue(s2.canCoastFor(45 * 60), "parking coast to node must be possible");
    assertFalse(s2.canCoastFor(5.25 * 3_600), "GTO coast to apogee must delegate to the AKM");
  }

  /**
   * Behaviour-equivalence lock (spec 07 §6): instantiating the catalog model with the loads of the
   * former {@code Launchers.FalconHeavy(600_000, 50_000, …)} factory yields a stack with the same
   * per-stage masses and propulsion, hence identical trajectories.
   */
  @Test
  void falconHeavy_instantiate_matchesFormerFactoryStack() {
    Spacecraft payload = Spacecraft.getSpacecraft();
    VehicleStack stack =
        Launchers.FALCON_HEAVY.instantiate(new double[] {600_000, 50_000}, payload);

    List<Vehicle> vehicles = stack.vehicles();
    assertEquals(3, vehicles.size());

    Vehicle s1 = vehicles.get(0);
    assertEquals(66_000, s1.dryMass(), 1e-6);
    assertEquals(600_000, s1.propellantLoad(), 1e-6);
    assertEquals(311, s1.propulsion().isp(), 1e-6);
    assertEquals(22_800_000, s1.propulsion().thrust(), 1e-6);

    Vehicle s2 = vehicles.get(1);
    assertEquals(4_000, s2.dryMass(), 1e-6);
    assertEquals(50_000, s2.propellantLoad(), 1e-6);
    assertEquals(348, s2.propulsion().isp(), 1e-6);
    assertEquals(981_000, s2.propulsion().thrust(), 1e-6);

    assertSame(payload, vehicles.get(2));
    assertEquals(66_000 + 600_000 + 4_000 + 50_000 + payload.getMass(), stack.getMass(), 1e-6);
  }
}
