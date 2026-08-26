package com.smousseur.orbitlab.simulation.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.stage.CoastingStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.VehicleStack;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
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
 * The switch of PHY-1 / L1 (spec {@code docs/atmosphere/04-conception-L1.md} §§3.2–3.3): where the
 * atmosphere choice lives, who writes it, and the two independent yes it takes for a propagation to
 * actually carry drag.
 */
class AtmosphereChoiceTest {

  private static final AerodynamicProperties S1_AERO = new AerodynamicProperties(31.6, 0.4);
  private static final AerodynamicProperties S2_AERO = new AerodynamicProperties(10.5, 2.2);

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // ════════════════════════════════════════════════════════════════════════
  // The choice: spec, then mission
  // ════════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("A spec built without an atmosphere carries NONE, not null")
  void spec_normalisesNullToNone() {
    assertEquals(AtmosphereModel.NONE, earthOrbitSpec(null).atmosphere());
    assertEquals(AtmosphereModel.NONE, geoSpec(null).atmosphere());
    assertEquals(AtmosphereModel.NRLMSISE, earthOrbitSpec(AtmosphereModel.NRLMSISE).atmosphere());
  }

  /**
   * The default is written twice — here and in the spec — and that is necessary rather than
   * redundant: a mission assembled without going through {@code MissionComposer} (the optimizer
   * test base class, the fixtures) never sees the spec's normalisation.
   */
  @Test
  @DisplayName("A mission built outside the composer still carries NONE")
  void mission_defaultsToNone_withoutTheComposer() {
    Mission mission = new BareMission(stack(S1_AERO, S2_AERO));

    assertEquals(AtmosphereModel.NONE, mission.getAtmosphere());
    assertThrows(NullPointerException.class, () -> mission.setAtmosphere(null));
  }

  @Test
  @DisplayName("The composer is the single writer, and it copies the spec's choice")
  void composer_writesTheSpecChoice() {
    Mission fromDefault = MissionComposer.compose(earthOrbitSpec(null), OptimizationType.FAST);
    assertEquals(AtmosphereModel.NONE, fromDefault.getAtmosphere());

    Mission fromChoice =
        MissionComposer.compose(
            earthOrbitSpec(AtmosphereModel.HARRIS_PRIESTER), OptimizationType.FAST);
    assertEquals(AtmosphereModel.HARRIS_PRIESTER, fromChoice.getAtmosphere());
  }

  /** The choice survives the spec copy the propellant planner makes at every candidate load. */
  @Test
  @DisplayName("withLauncherLoads carries the choice over")
  void withLauncherLoads_keepsTheChoice() {
    MissionSpec spec = earthOrbitSpec(AtmosphereModel.NRLMSISE);
    double[] loads = spec.configuration().propellantLoads();
    loads[0] = loads[0] / 2.0;

    assertEquals(AtmosphereModel.NRLMSISE, spec.withLauncherLoads(loads).atmosphere());
  }

  // ════════════════════════════════════════════════════════════════════════
  // The resolver: two independent yes
  // ════════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("NONE means no drag, however well the catalog is populated")
  void switchOff_meansNoDrag() {
    Mission mission = new BareMission(stack(S1_AERO, S2_AERO));
    FlightContext context =
        new CoastingStage("coast", 10.0).flightContext(liftOff(mission), mission);

    assertFalse(context.hasDrag());
    assertSame(mission.gravitationalContext(), context.gravity());
  }

  @Test
  @DisplayName("An atmosphere plus a declared stage means drag, from the ACTIVE stage")
  void switchOn_andDeclaredHardware_meansDrag() {
    Mission mission = new BareMission(stack(S1_AERO, S2_AERO));
    mission.setAtmosphere(AtmosphereModel.NRLMSISE);
    CoastingStage stage = new CoastingStage("coast", 10.0);

    FlightContext atLiftOff = stage.flightContext(liftOff(mission), mission);
    assertTrue(atLiftOff.hasDrag());
    assertSame(S1_AERO, atLiftOff.drag().aero());
    assertEquals(AtmosphereModel.NRLMSISE, atLiftOff.drag().model());

    FlightContext afterStaging = stage.flightContext(afterStaging(mission), mission);
    assertNotNull(afterStaging.drag());
    assertSame(S2_AERO, afterStaging.drag().aero(), "the section changes at the jettison");
  }

  @Test
  @DisplayName("An atmosphere over silent hardware still means no drag")
  void switchOn_butUndeclaredHardware_meansNoDrag() {
    Mission mission = new BareMission(stack(null, null));
    mission.setAtmosphere(AtmosphereModel.NRLMSISE);

    FlightContext context =
        new CoastingStage("coast", 10.0).flightContext(liftOff(mission), mission);

    assertFalse(context.hasDrag(), "no invented section, no NaN — simply no drag");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private static VehicleStack stack(AerodynamicProperties s1, AerodynamicProperties s2) {
    return new VehicleStack(
        List.of(
            new LaunchVehicle(
                66_000, 1_233_000, 1_233_000, new PropulsionSystem(296, 22_800_000), s1),
            new LaunchVehicle(4_000, 107_500, 107_500, new PropulsionSystem(348, 981_000), s2),
            Spacecraft.LEGACY));
  }

  private static SpacecraftState liftOff(Mission mission) {
    return stateAt(mission.getVehicle().getMass());
  }

  private static SpacecraftState afterStaging(Mission mission) {
    VehicleStack stack = (VehicleStack) mission.getVehicle();
    return stateAt(stack.resolveActiveStage(stack.getMass()).massAfterJettison());
  }

  private static SpacecraftState stateAt(double mass) {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    double radius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0;
    PVCoordinates pv =
        new PVCoordinates(
            new Vector3D(radius, 0, 0),
            new Vector3D(0, Math.sqrt(Constants.WGS84_EARTH_MU / radius), 0));
    return new SpacecraftState(
            new CartesianOrbit(pv, OrekitService.get().gcrf(), date, Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  private static MissionSpec.EarthOrbit earthOrbitSpec(AtmosphereModel atmosphere) {
    return new MissionSpec.EarthOrbit(
        "LEO",
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
        400_000.0,
        400_000.0,
        Math.toRadians(5.2),
        null,
        null,
        "Kourou",
        5.2,
        -52.8,
        0.0,
        null,
        atmosphere);
  }

  private static MissionSpec.Geo geoSpec(AtmosphereModel atmosphere) {
    return new MissionSpec.Geo(
        "GEO",
        LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY),
        300_000.0,
        35_786_000.0,
        0.0,
        "Kourou",
        5.2,
        -52.8,
        0.0,
        null,
        atmosphere);
  }

  /** A mission with a vehicle and nothing else: the shape a fixture assembles by hand. */
  private static final class BareMission extends Mission {
    private BareMission(Vehicle vehicle) {
      super("bare", vehicle, List.of(), objective());
    }

    @Override
    public SpacecraftState getInitialState(AbsoluteDate initialDate) {
      return stateAt(getVehicle().getMass());
    }

    private static MissionObjective objective() {
      return OrbitInsertionObjective.circular(SolarSystemBody.EARTH, 400_000.0, 0.0);
    }
  }
}
