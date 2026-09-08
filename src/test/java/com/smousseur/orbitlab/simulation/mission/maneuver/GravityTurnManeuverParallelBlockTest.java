package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * What the maneuver derives when the stack burns two stages at once (spec {@code
 * docs/etagement/03-conception-L1.md} §3.5, §4).
 *
 * <p>Round figures: the boosters flow 2 000 kN worth, the core 500 kN at the same Isp, so the core
 * drains a fifth of the block. Loaded 400 t and 200 t, the boosters run dry with 100 t left in the
 * core.
 */
class GravityTurnManeuverParallelBlockTest {

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static final double G0 = Constants.G0_STANDARD_GRAVITY;

  @Test
  void splitBlock_thePlanCarriesACorePhase() {
    AscentPlan plan = planOf(splitStack());

    assertTrue(plan.hasCorePhase());
    assertEquals(StageRole.CORE, plan.coreStage().role());
  }

  @Test
  void splitBlock_theFirstBurnEndsAtBoosterFlameOut() {
    // 500 t consumed by the block at 2 500 kN / 300 s.
    double blockFlow = 2_500_000 / (300 * G0);

    assertEquals(500_000 / blockFlow, planOf(splitStack()).burn1Duration(), 1e-9);
  }

  @Test
  void splitBlock_theCoreBurnsItsOwnRemainderAtFullThrust() {
    double coreFlow = 500_000 / (300 * G0);

    assertEquals(100_000 / coreFlow, planOf(splitStack()).coreBurnDuration(), 1e-9);
  }

  @Test
  void groupedBlock_thereIsNoCorePhase() {
    assertFalse(planOf(groupedStack()).hasCorePhase());
  }

  @Test
  void sequentialStack_thereIsNoCorePhase() {
    assertFalse(planOf(sequentialStack()).hasCorePhase());
  }

  @Test
  void stagingCompletesLaterWhenACorePhaseHasToBeFlown() {
    assertEquals(
        planOf(splitStack()).stagingCompleteTime(),
        maneuverOf(splitStack()).getStagingCompleteTime(),
        1e-9);
    assertTrue(
        maneuverOf(splitStack()).getStagingCompleteTime()
            > maneuverOf(groupedStack()).getStagingCompleteTime());
  }

  @Test
  void theSinglePropagatorPathRefusesAParallelBlock() {
    // It plants the jettison inside the burn and knows only two burns; teaching it the block would
    // duplicate the five-phase logic in a second place. No src/main caller reaches it (spec §4).
    GravityTurnManeuver maneuver = maneuverOf(splitStack());
    AscentPlan plan = planOf(splitStack());
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(FlightContext.earth(), 30.0);

    OrbitlabException thrown =
        assertThrows(OrbitlabException.class, () -> maneuver.configure(propagator, plan));
    assertTrue(thrown.getMessage().contains("core"), thrown.getMessage());
  }

  @Test
  void theSinglePropagatorPathStillAcceptsASequentialStack() {
    GravityTurnManeuver maneuver = maneuverOf(sequentialStack());
    AscentPlan plan = planOf(sequentialStack());
    NumericalPropagator propagator =
        OrekitService.get().createOptimizationPropagator(FlightContext.earth(), 30.0);

    assertDoesNotThrow(() -> maneuver.configure(propagator, plan));
  }

  private static AscentPlan planOf(VehicleStack stack) {
    return maneuverOf(stack).plan(stateAtMass(stack.getMass()), new double[] {600.0, 0.32});
  }

  private static GravityTurnManeuver maneuverOf(VehicleStack stack) {
    return new GravityTurnManeuver(
        stack, stack.getMass(), 0.05, Math.PI / 2, 2.0, FlightContext.earth());
  }

  private static VehicleStack splitStack() {
    return blockStack(200_000, false, 100_000);
  }

  private static VehicleStack groupedStack() {
    return blockStack(100_000, true, 0.0);
  }

  private static VehicleStack blockStack(
      double coreLoad, boolean grouped, double coreLeftAtBoosterBurnout) {
    List<Vehicle> vehicles =
        List.of(
            new LaunchVehicle(20_000, 400_000, 400_000, new PropulsionSystem(300, 2_000_000)),
            new LaunchVehicle(20_000, coreLoad, coreLoad, new PropulsionSystem(300, 500_000)),
            new LaunchVehicle(4_000, 50_000, 50_000, new PropulsionSystem(450, 100_000)),
            new Spacecraft(1_000, 0, new PropulsionSystem(300, 3_000)));
    return new VehicleStack(
        vehicles,
        new StagingPlan(
            List.of(StageRole.BOOSTER, StageRole.CORE, StageRole.UPPER, StageRole.KICK),
            new StagingPlan.ParallelBlock(0, grouped, 1.0, coreLeftAtBoosterBurnout)));
  }

  private static VehicleStack sequentialStack() {
    List<Vehicle> vehicles =
        List.of(
            new LaunchVehicle(20_000, 400_000, 400_000, new PropulsionSystem(300, 2_000_000)),
            new LaunchVehicle(4_000, 50_000, 50_000, new PropulsionSystem(450, 100_000)),
            new Spacecraft(1_000, 0, new PropulsionSystem(300, 3_000)));
    return new VehicleStack(
        vehicles, new StagingPlan(List.of(StageRole.CORE, StageRole.UPPER, StageRole.KICK), null));
  }

  private static SpacecraftState stateAtMass(double mass) {
    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 200_000;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                gcrf,
                AbsoluteDate.J2000_EPOCH,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }
}
