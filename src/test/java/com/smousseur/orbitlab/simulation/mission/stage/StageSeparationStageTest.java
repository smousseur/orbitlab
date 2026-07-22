package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
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

  private static final LaunchVehicle S2 =
      new LaunchVehicle(4_000, 107_500, 500, new PropulsionSystem(348, 981_000));
  private static final Spacecraft AKM_SAT =
      new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));

  private static Mission missionWith(VehicleStack stack) {
    return new Mission("separation test", stack, List.of(), null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }
    };
  }

  private static SpacecraftState stateAtMass(double mass) {
    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                gcrf,
                AbsoluteDate.J2000_EPOCH,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  @Test
  void enter_dropsToExactMassAfterJettison() {
    VehicleStack stack = new VehicleStack(List.of(S2, AKM_SAT));
    StageSeparationStage separation = new StageSeparationStage("S2 separation", 2.0);

    SpacecraftState afterSeparation =
        separation.enter(stateAtMass(stack.getMass()), missionWith(stack));

    // The mass is forced to the exact reference mass of the stack above: the payload and its AKM.
    assertEquals(AKM_SAT.getMass(), afterSeparation.getMass(), 1e-9);
  }

  @Test
  void negativeCoastDuration_rejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new StageSeparationStage("S2 separation", -1.0));
  }

  // ── expectedStageIndex guard (bilan 10 §6 follow-up) ──────────────────────

  @Test
  void enter_expectedStageActive_jettisonsIt() {
    VehicleStack stack = new VehicleStack(List.of(S2, AKM_SAT));
    StageSeparationStage separation = new StageSeparationStage("S2 separation", 2.0, 0);

    SpacecraftState afterSeparation =
        separation.enter(stateAtMass(stack.getMass()), missionWith(stack));

    assertEquals(AKM_SAT.getMass(), afterSeparation.getMass(), 1e-9);
  }

  @Test
  void enter_wrongStageActive_throwsInsteadOfDroppingIt() {
    // Three-stage stack; the separation is meant to drop S2 (index 1), but the mass says S1
    // (index 0) is still active with propellant aboard — the I7 GEO failure mode.
    LaunchVehicle s1 = new LaunchVehicle(66_000, 1_233_000, 1_233_000, new PropulsionSystem(311, 2e7));
    VehicleStack stack = new VehicleStack(List.of(s1, S2, AKM_SAT));
    StageSeparationStage separation = new StageSeparationStage("S2 separation", 2.0, 1);

    // Full stack mass: S1 is active and still holds its whole load.
    SpacecraftState beforeSeparation = stateAtMass(stack.getMass());

    OrbitlabException thrown =
        assertThrows(
            OrbitlabException.class, () -> separation.enter(beforeSeparation, missionWith(stack)));
    assertTrue(
        thrown.getMessage().contains("stack stage 1"),
        () -> "message must name the expected stage, got: " + thrown.getMessage());
    assertTrue(
        thrown.getMessage().contains("stage 0 is active"),
        () -> "message must name the active stage, got: " + thrown.getMessage());
  }

  @Test
  void enter_anyStage_keepsLegacyUncheckedBehaviour() {
    LaunchVehicle s1 = new LaunchVehicle(66_000, 1_233_000, 1_233_000, new PropulsionSystem(311, 2e7));
    VehicleStack stack = new VehicleStack(List.of(s1, S2, AKM_SAT));
    StageSeparationStage separation =
        new StageSeparationStage("S2 separation", 2.0, StageSeparationStage.ANY_STAGE);

    // S1 active with a full load: unchecked, it is dropped anyway (the pre-guard behaviour).
    SpacecraftState afterSeparation =
        separation.enter(stateAtMass(stack.getMass()), missionWith(stack));

    assertEquals(S2.getMass() + AKM_SAT.getMass(), afterSeparation.getMass(), 1e-9);
  }
}
