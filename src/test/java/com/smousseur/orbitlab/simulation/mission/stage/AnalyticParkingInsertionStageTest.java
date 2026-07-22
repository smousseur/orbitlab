package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
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

/**
 * Guard on the raising-Hohmann geometry the stage assumes (bilan 10 §6 follow-up). An entry state
 * whose apoapsis already sits above the target needs a retrograde burn, which {@code
 * Physics.computeBurnDuration} turns into a <em>negative</em> duration — the propagator then gets a
 * maneuver ending before it starts and the plan predicts a mass gain. Observed on the I7 GEO loop
 * at λ=0.3 (ΔV1 = −57 m/s, dt1 = −0.61 s), where the burns silently did nothing.
 */
class AnalyticParkingInsertionStageTest {

  private static final double TARGET_ALTITUDE = 400_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Mission missionWith(Vehicle vehicle) {
    return new Mission("parking insertion test", vehicle, List.of(), null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }
    };
  }

  /** Circular orbit at {@code altitude} — the entry radius is what drives the burn signs. */
  private static SpacecraftState circularStateAt(double altitude, double mass) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + altitude;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(new Vector3D(r, 0, 0), new Vector3D(0, v, 0)),
                OrekitService.get().gcrf(),
                AbsoluteDate.J2000_EPOCH,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  private static VehicleStack stack() {
    LaunchVehicle upperStage =
        new LaunchVehicle(4_000, 107_500, 12_000, new PropulsionSystem(348, 981_000));
    Spacecraft payload = new Spacecraft(2_000, 2_000, 0, new PropulsionSystem(320, 400));
    return new VehicleStack(List.of(upperStage, payload));
  }

  @Test
  void entryAboveTarget_refusesThePlanInsteadOfPlanningARetrogradeBurn() {
    VehicleStack stack = stack();
    // Entering at 600 km for a 400 km target: the transfer ellipse is *below* the entry orbit, so
    // burn 1 would have to brake (≈ −55 m/s), the mirror image of the λ=0.3 GEO failure.
    SpacecraftState tooHigh = circularStateAt(600_000, stack.getMass());
    AnalyticParkingInsertionStage stage =
        new AnalyticParkingInsertionStage("Parking", TARGET_ALTITUDE);

    OrbitlabException thrown =
        assertThrows(
            OrbitlabException.class,
            () -> stage.propagateStandalone(tooHigh, missionWith(stack)));

    assertTrue(
        thrown.getMessage().contains("retrograde"),
        () -> "message must name the cause, got: " + thrown.getMessage());
    assertTrue(
        thrown.getMessage().contains("Parking"),
        () -> "message must name the stage, got: " + thrown.getMessage());
  }

  @Test
  void entryBelowTarget_plansAProgradeInsertion() {
    VehicleStack stack = stack();
    // Entering at 200 km for a 400 km target: both burns are prograde (burn 1 ≈ +58 m/s).
    SpacecraftState nominal = circularStateAt(200_000, stack.getMass());
    AnalyticParkingInsertionStage stage =
        new AnalyticParkingInsertionStage("Parking", TARGET_ALTITUDE);

    SpacecraftState afterInsertion = stage.propagateStandalone(nominal, missionWith(stack));

    assertTrue(
        afterInsertion.getMass() < nominal.getMass(),
        "a prograde insertion must consume propellant, never gain mass");
    assertTrue(
        afterInsertion.getDate().durationFrom(nominal.getDate()) > 0,
        "the plan must advance time through the transfer coast");
  }
}
