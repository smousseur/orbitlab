package com.smousseur.orbitlab.simulation.mission.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan;
import com.smousseur.orbitlab.simulation.mission.maneuver.TranslunarInjectionPlan.Departure;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.window.problem.ParkingInsertion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
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
  private static final Logger logger = LogManager.getLogger(ParkingCoastStageTest.class);

  private static final double PARKING_ALTITUDE = 400_000.0;

  /**
   * The ascent flown due east from Canaveral at 2026-10-07T01:24:53, as {@code
   * ParkingInsertion.fly} measured it — Falcon Heavy, a 2 000 kg orbiter, a 400 km parking orbit,
   * the production budget and seed.
   */
  private static final ParkingInsertion LONGEST_COAST_INSERTION =
      new ParkingInsertion(
          new PVCoordinates(
              new Vector3D(-4420797.557283313, 4617049.949570638, -2215256.9544630554),
              new Vector3D(-5466.1228318177355, -3957.6706225001444, 2653.6112379471742)),
          183.4411577407793,
          3655.7108201071132,
          3.9448683549704513,
          20146.245388898584);

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
  @DisplayName("The parking coast stops half a burn short of the injection point it really reaches")
  void propagateStandalone_advancesToTheInjectionPoint() {
    VehicleStack stack = stack();
    SpacecraftState parking = keplerianShift(parkingState(stack.getMass()), -2_300.0);
    Mission mission = missionWith(stack);
    Departure departure = TranslunarInjectionPlan.departureFrom(parking);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(parking.getMass());
    double ignitionLead = TranslunarInjectionPlan.ignitionLead(parking, departure, active);

    // Teeth: the Keplerian prediction drifts behind the flown point as the coast grows, so a short
    // coast would let a stage stopping on the prediction pass as well.
    assertTrue(
        departure.coastDuration() > 2_000.0,
        "the fixture must have a long coast to fly, got " + departure.coastDuration() + " s");

    SpacecraftState flown =
        new ParkingCoastStage("Parking coast").propagateStandalone(parking, mission);

    // Re-predicted from half a burn out, the Keplerian closed form is exact to the millisecond.
    assertEquals(
        ignitionLead,
        TranslunarInjectionPlan.departureFrom(flown).coastDuration(),
        0.1,
        "the coast must stop one ignitionLead short of the injection point the flight reaches");
    assertTrue(
        Vector3D.distance(flown.getPosition(), parking.getPosition()) > 1_000_000.0,
        "the state must have travelled, not merely been re-dated");
  }

  @Test
  @DisplayName("Entered less than half a burn before its injection point, the coast waits a turn")
  void propagateStandalone_takesTheNextPassageWhenTheFirstIsTooClose() {
    VehicleStack stack = stack();
    Mission mission = missionWith(stack);
    SpacecraftState parking = parkingState(stack.getMass());
    Departure first = TranslunarInjectionPlan.departureFrom(parking);
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(parking.getMass());
    double halfBurn = TranslunarInjectionPlan.ignitionLead(parking, first, active);
    SpacecraftState late = keplerianShift(parking, first.coastDuration() - 0.5 * halfBurn);
    Departure tooClose = TranslunarInjectionPlan.departureFrom(late);
    double ignitionLead = TranslunarInjectionPlan.ignitionLead(late, tooClose, active);

    // Teeth: lit on this passage, the burn would ignite before the coast even began.
    assertTrue(
        tooClose.coastDuration() < ignitionLead,
        "the fixture must enter inside the half burn, got "
            + tooClose.coastDuration()
            + " s to the point for a "
            + ignitionLead
            + " s lead");

    SpacecraftState flown =
        new ParkingCoastStage("Parking coast").propagateStandalone(late, mission);

    assertTrue(
        flown.getDate().durationFrom(late.getDate()) > 0.5 * late.getOrbit().getKeplerianPeriod(),
        "the coast must run to the next passage, got "
            + flown.getDate().durationFrom(late.getDate())
            + " s");
    assertEquals(
        ignitionLead,
        TranslunarInjectionPlan.departureFrom(flown).coastDuration(),
        0.1,
        "the coast must stop one ignitionLead short of the next passage");
  }

  /**
   * The Canaveral window of 2026-10-07T01:24:53, the longest parking coast of the week: stopped on
   * the Keplerian prediction, its burn was centred 7.3 s past the injection point and the aim
   * refused it at 839 km. Behind {@code orbitlab.slowTests}: the aim flies some thirty four-day
   * transfers.
   */
  @Test
  @EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
  @DisplayName("The longest parking coast of the week lights a centred injection the aim accepts")
  void theLongestCoastOfTheWeekLightsACentredInjection() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "longest coast");
    values.put("LAUNCH_SITE_LAT", 28.562);
    values.put("LAUNCH_SITE_LONG", -80.577);
    values.put("LAUNCH_SITE_ALT", 3.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    MissionSpec spec = MissionFactory.specFromWizardValues(values, MissionType.LUNAR_ORBIT);
    Mission mission = MissionComposer.compose(spec, OptimizationType.FAST);
    mission.setAtmosphere(spec.atmosphere());
    AbsoluteDate liftOff = new AbsoluteDate("2026-10-07T01:24:53.000Z", TimeScalesFactory.getUTC());

    SpacecraftState ignition =
        stageOf(mission, ParkingCoastStage.class)
            .propagateStandalone(LONGEST_COAST_INSERTION.carriedTo(liftOff), mission);
    SpacecraftState settled =
        stageOf(mission, TLIBurnStage.class).propagateStandalone(ignition, mission);

    double burn =
        settled.getDate().durationFrom(ignition.getDate()) - TLIBurnStage.SETTLING_COAST_SECONDS;
    double centring = 0.5 * burn - TranslunarInjectionPlan.departureFrom(ignition).coastDuration();
    logger.info(
        "Longest coast of the week: a {} s burn centred {} s past its point", burn, centring);
    assertEquals(
        0.0,
        centring,
        0.1,
        "the " + burn + " s burn must be centred on the injection point, got " + centring + " s");
  }

  private static <T extends MissionStage> T stageOf(Mission mission, Class<T> type) {
    return mission.getStages().stream().filter(type::isInstance).map(type::cast).findFirst().get();
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

  /** The same orbit {@code dt} seconds further along, by the two-body closed form. */
  private static SpacecraftState keplerianShift(SpacecraftState state, double dt) {
    return new SpacecraftState(
            new KeplerianOrbit(
                    new PVCoordinates(state.getPosition(), state.getPVCoordinates().getVelocity()),
                    state.getFrame(),
                    state.getDate(),
                    state.getOrbit().getMu())
                .shiftedBy(dt))
        .withMass(state.getMass());
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
