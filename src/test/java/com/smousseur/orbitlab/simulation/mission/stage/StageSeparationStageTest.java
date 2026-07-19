package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

class StageSeparationStageTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  @Test
  void enter_dropsToExactMassAfterJettison() {
    LaunchVehicle s2 = new LaunchVehicle(4_000, 107_500, 500, new PropulsionSystem(348, 981_000));
    Spacecraft akmSat = new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));
    VehicleStack stack = new VehicleStack(List.of(s2, akmSat));
    Mission mission =
        new Mission("separation test", stack, List.of(), null) {
          @Override
          public SpacecraftState getInitialState(AbsoluteDate initialDate) {
            return null;
          }
        };

    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    SpacecraftState beforeSeparation =
        new SpacecraftState(
                new CartesianOrbit(
                    new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                    gcrf,
                    AbsoluteDate.J2000_EPOCH,
                    Constants.WGS84_EARTH_MU))
            .withMass(stack.getMass());

    StageSeparationStage separation = new StageSeparationStage("S2 separation", 2.0);
    SpacecraftState afterSeparation = separation.enter(beforeSeparation, mission);

    // The mass is forced to the exact reference mass of the stack above: the payload and its AKM.
    assertEquals(akmSat.getMass(), afterSeparation.getMass(), 1e-9);
  }

  @Test
  void negativeCoastDuration_rejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new StageSeparationStage("S2 separation", -1.0));
  }
}
