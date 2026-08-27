package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * MIS-4 / L4 §8.2 — the trap L1 §6 left open, made visible in a few seconds on a synthetic parking
 * state.
 *
 * <p>In {@code MissionOptimizer}'s stage walk a stage is advanced by {@code propagateStandalone},
 * whose default implementation is {@code enter} alone. A coast has nothing to do on entry, so an
 * ordinary {@link CoastingStage} placed mid-chain returns the state <em>unchanged</em> — the walk
 * then plans the next stage from the wrong date and the wrong phase, and nothing is raised. This is
 * the single assertion that separates the two behaviours, and it is why {@link ParkingCoastStage}
 * exists at all.
 *
 * <p>Its family is {@code GravityTurnReplayConsistencyTest}, which guards the same stage-walk /
 * ephemeris agreement on the ascent after a real defect that ended in ~5° of inclination on the GEO
 * chain.
 */
class ParkingCoastStageTest {

  private static final double PARKING_ALTITUDE = 400_000.0;

  /** The epoch every lunar test of the repository reads its geometry at. */
  private static AbsoluteDate epoch;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
    epoch = new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  @DisplayName("The parking coast advances the stage walk by the coast it resolved")
  void propagateStandalone_advancesToTheInjectionPoint() {
    VehicleStack stack = stack();
    SpacecraftState parking = parkingState(stack.getMass());
    Departure departure = TranslunarInjectionPlan.departureFrom(parking);

    // Teeth: an injection point already underfoot would make the assertion below hold for a stage
    // that does nothing at all.
    assertTrue(
        departure.coastDuration() > 60.0,
        "the fixture must have a real coast to fly, got " + departure.coastDuration() + " s");

    SpacecraftState flown =
        new ParkingCoastStage("Parking coast").propagateStandalone(parking, missionWith(stack));

    assertEquals(
        departure.coastDuration(),
        flown.getDate().durationFrom(parking.getDate()),
        1.0,
        "the stage walk must reach the injection point departureFrom resolved");
    assertTrue(
        Vector3D.distance(flown.getPosition(), parking.getPosition()) > 1_000_000.0,
        "the state must have travelled, not merely been re-dated");
  }

  @Test
  @DisplayName("A plain coast in the same place advances nothing — the defect this class closes")
  void plainCoastingStage_doesNotAdvanceTheStageWalk() {
    VehicleStack stack = stack();
    SpacecraftState parking = parkingState(stack.getMass());

    SpacecraftState flown =
        new CoastingStage("Coasting", null).propagateStandalone(parking, missionWith(stack));

    assertEquals(
        0.0,
        flown.getDate().durationFrom(parking.getDate()),
        0.0,
        "if this ever fails, CoastingStage has been repaired and ParkingCoastStage may go");
  }

  /** Circular at {@link #PARKING_ALTITUDE}, inclined at the Canaveral latitude, ascending node. */
  private static SpacecraftState parkingState(double mass) {
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + PARKING_ALTITUDE;
    double v = Math.sqrt(Constants.WGS84_EARTH_MU / r);
    double inclination = Math.toRadians(28.562);
    return new SpacecraftState(
            new CartesianOrbit(
                new PVCoordinates(
                    new Vector3D(r, 0, 0),
                    new Vector3D(0, v * Math.cos(inclination), v * Math.sin(inclination))),
                OrekitService.get().gcrf(),
                epoch,
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  private static VehicleStack stack() {
    LaunchVehicle upperStage =
        new LaunchVehicle(4_000, 107_500, 12_000, new PropulsionSystem(348, 981_000));
    Spacecraft payload = new Spacecraft(2_000, 2_000, 0, new PropulsionSystem(320, 400));
    return new VehicleStack(List.of(upperStage, payload));
  }

  private static Mission missionWith(Vehicle vehicle) {
    return new Mission("parking coast test", vehicle, List.of(), null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }
    };
  }
}
