package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import java.util.List;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * Guard on the aimed-apogee Newton iteration (bilan 10 §6 follow-up). A stage that cannot deliver
 * the injection caps its burn near zero, the post-burn apogee never moves, and the unchecked
 * iteration used to accumulate the whole defect into the aim — the I7 GEO run produced a 177 000 km
 * aim for a 35 786 km target, whose multi-day transfer orbit then made the downstream propagation
 * grind for tens of minutes. The plan must refuse instead.
 */
class AnalyticGtoInjectionStageTest {

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double GEO_ALTITUDE = 35_786_000.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static Mission missionWith(Vehicle vehicle) {
    return new Mission("gto injection test", vehicle, List.of(), null) {
      @Override
      public SpacecraftState getInitialState(AbsoluteDate initialDate) {
        return null;
      }
    };
  }

  /** Circular parking orbit at {@link #PARKING_ALTITUDE} with the given mass. */
  private static SpacecraftState parkingState(double mass) {
    Frame gcrf = OrekitService.get().gcrf();
    double r = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + PARKING_ALTITUDE;
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
  void propellantStarvedStage_refusesThePlanInsteadOfDivergingTheAim() {
    // 5 kg aboard an upper stage that needs ~2.4 t of propellant to reach GTO.
    LaunchVehicle starvedS2 =
        new LaunchVehicle(4_000, 107_500, 5, new PropulsionSystem(348, 981_000));
    Spacecraft payload = new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));
    VehicleStack stack = new VehicleStack(List.of(starvedS2, payload));

    AnalyticGtoInjectionStage stage = new AnalyticGtoInjectionStage("GTO injection", GEO_ALTITUDE);

    OrbitlabException thrown =
        assertThrows(
            OrbitlabException.class,
            () -> stage.propagateStandalone(parkingState(stack.getMass()), missionWith(stack)));

    // A starved stage is a *capability* limit, not a solver one: its burn is clamped to depletion,
    // so the aim iteration has no degree of freedom and no iteration budget could rescue it. The
    // message must say so — conflating the two is what made the I7 GEO wall look like an artifact
    // worth re-running with more iterations (bilan 11 §3.7).
    assertTrue(
        thrown.getMessage().contains("injection out of reach"),
        () -> "message must name a capability limit, got: " + thrown.getMessage());
    assertFalse(
        thrown.getMessage().contains("did not converge"),
        () ->
            "a propellant-capped burn must not be reported as a solver failure, got: "
                + thrown.getMessage());
    assertTrue(
        thrown.getMessage().contains("GTO injection"),
        () -> "message must name the stage, got: " + thrown.getMessage());
    assertTrue(
        thrown.getMessage().contains("burning all 5 kg"),
        () -> "message must carry the propellant actually available, got: " + thrown.getMessage());
  }

  @Test
  void adequatelyFuelledStage_planConverges() {
    // The same stage with a realistic GTO-injection load reaches the target and must not throw.
    LaunchVehicle s2 =
        new LaunchVehicle(4_000, 107_500, 12_000, new PropulsionSystem(348, 981_000));
    Spacecraft payload = new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));
    VehicleStack stack = new VehicleStack(List.of(s2, payload));

    AnalyticGtoInjectionStage stage = new AnalyticGtoInjectionStage("GTO injection", GEO_ALTITUDE);

    SpacecraftState afterInjection =
        stage.propagateStandalone(parkingState(stack.getMass()), missionWith(stack));

    assertTrue(
        afterInjection.getMass() < stack.getMass(),
        "the injection burn must have consumed propellant");
  }

  @Test
  void inclinedOffNodeParking_landsTheTransferApogeeOnTheEquator() {
    // Node-aware injection (bilan 11 §3.10): the downstream apogee plane change only has authority
    // when apogee sits on an equatorial node. From an inclined parking orbit taken deliberately off
    // a node (argument of latitude 45°), the un-targeted antipodal apogee would fall several
    // degrees
    // off the equator; node-aware injection must nudge its lead coast so the flown transfer apogee
    // lands back on the equator.
    LaunchVehicle s2 =
        new LaunchVehicle(4_000, 107_500, 12_000, new PropulsionSystem(348, 981_000));
    Spacecraft payload = new Spacecraft(2_000, 2_000, 1_500, new PropulsionSystem(320, 400));
    VehicleStack stack = new VehicleStack(List.of(s2, payload));

    Frame gcrf = OrekitService.get().gcrf();
    double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + PARKING_ALTITUDE;
    KeplerianOrbit parking =
        new KeplerianOrbit(
            a,
            1.0e-3,
            FastMath.toRadians(5.2), // Kourou-like inclination
            0.0, // argument of perigee
            0.0, // RAAN
            FastMath.toRadians(45.0), // true anomaly ⇒ argument of latitude 45°, off the node
            PositionAngleType.TRUE,
            gcrf,
            AbsoluteDate.J2000_EPOCH,
            Constants.WGS84_EARTH_MU);
    SpacecraftState parkingState = new SpacecraftState(parking).withMass(stack.getMass());

    AnalyticGtoInjectionStage stage = new AnalyticGtoInjectionStage("GTO injection", GEO_ALTITUDE);
    SpacecraftState afterInjection = stage.propagateStandalone(parkingState, missionWith(stack));

    SpacecraftState apogee =
        AnalyticTrimBurnStage.detectStateAtApogee(afterInjection, FlightContext.earth());
    assertNotNull(apogee, "the transfer apogee must be reachable after the injection burn");

    Vector3D r = apogee.getPVCoordinates().getPosition();
    double apogeeLatitudeDeg = FastMath.toDegrees(FastMath.asin(r.getZ() / r.getNorm()));
    assertTrue(
        FastMath.abs(apogeeLatitudeDeg) < 0.2,
        () ->
            "node-aware injection must place the transfer apogee on the equator, but its latitude "
                + "was "
                + apogeeLatitudeDeg
                + "° (off-node apogee starves the AKM plane change)");
  }
}
