package com.smousseur.orbitlab.simulation.mission.maneuver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.detector.ReentryGuard;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.stage.TLIBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchVehicle;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * The translunar injection on the geometries that broke the lunar compute: aim candidates that fall
 * back into the atmosphere or hit the Moon, an injection the active stage cannot carry, and a
 * calibrated burn capped by its propellant.
 *
 * <p>Every state below was reached by the production computation of a Falcon Heavy lunar orbiter
 * and captured in full precision: the ignition states where the computation failed, from Cape
 * Canaveral on the 2026-10-08 02:52:04 UTC window and from Kourou on 2026-10-06 at 08:00 UTC, and
 * the post-burn states of three aim candidates flown from them or from a window geometry. They are
 * pinned rather than recomputed because the search that produces them is the thing under test.
 */
class TranslunarInjectionGuardTest {

  /** The perilune altitude both lunar profiles aim at by default (m). */
  private static final double TARGET_PERILUNE_ALTITUDE = 100_000.0;

  private static SpacecraftState canaveralIgnition;
  private static SpacecraftState kourouIgnition;

  @BeforeAll
  static void setUp() {
    OrekitService.get().initialize();
    canaveralIgnition =
        state(
            "2026-10-08T04:42:36.932610764234190952Z",
            new Vector3D(4973611.868168, 4314726.383995, 1608361.798281),
            new Vector3D(-3976.080372633, 5753.619853080, -3147.166622910),
            20220.267602);
    kourouIgnition =
        state(
            "2026-10-06T09:19:16.415424379303065168Z",
            new Vector3D(6349503.755827, 2318087.692678, 280607.898162),
            new Vector3D(-2640.880484260, 7188.603632583, 617.332927040),
            20147.885299);
  }

  /**
   * The candidate the outward bracket walk flew when the Canaveral compute crashed: a burn
   * saturated on its propellant that dives back into the atmosphere, where drag drove the step size
   * under Orekit's minimum.
   */
  @Test
  @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @DisplayName("A candidate falling back into the atmosphere reads far above the target")
  void aCandidateFallingBackIntoTheAtmosphereReadsAboveTheTarget() {
    SpacecraftState injected =
        state(
            "2026-10-08T04:43:24.115338797598603528Z",
            new Vector3D(4724295.888208703, 4585599.485600332, 1409322.9064215524),
            new Vector3D(-7104.5293720688205, 5778.232809715847, -5742.169101913541),
            6657.668307652268);

    double reading =
        TranslunarInjectionPlan.perileneRadius(
            injected,
            date("2026-10-12T04:42:51.750775137588028904Z"),
            context(canaveral(), canaveralIgnition));

    assertTrue(reading > targetRadius(), "reading " + reading);
  }

  /**
   * A candidate whose drag evaluation fails inside an integration step: NRLMSISE00 is asked for a
   * density under the surface by a trial step, before any detector can stop the flight.
   */
  @Test
  @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @DisplayName("A candidate whose drag model fails inside a step reads far above the target")
  void aCandidateWhoseDragFailsInsideAStepReadsAboveTheTarget() {
    SpacecraftState injected =
        state(
            "2026-10-11T00:28:37.206162824399925184Z",
            new Vector3D(1465927.7490842766, 6623411.752504541, -262620.4003025904),
            new Vector3D(-5837.270671551079, 1835.710050433254, -3464.635594838579),
            6657.668307652266);

    double reading =
        TranslunarInjectionPlan.perileneRadius(
            injected,
            date("2026-10-15T00:28:18.514563795350568384Z"),
            context(kourou(), kourouIgnition));

    assertTrue(reading > targetRadius(), "reading " + reading);
  }

  /**
   * An aim offset of 7 km from the Moon's centre on the 2026-10-06 Canaveral window: the flight
   * reaches the lunar surface, and the reading has to say so rather than report a passage through
   * the body.
   */
  @Test
  @DisplayName("A candidate hitting the Moon reads the surface it hit")
  void aCandidateHittingTheMoonReadsTheSurface() {
    SpacecraftState injected =
        state(
            "2026-10-06T11:19:34.216914283421253644Z",
            new Vector3D(6077866.777437299, 2525796.6195142907, 1665149.683100388),
            new Vector3D(-4055.4017352621872, 8847.614791563741, 4555.293838731953),
            6944.732841025833);

    double reading =
        TranslunarInjectionPlan.perileneRadius(
            injected,
            date("2026-10-10T11:19:16.018577090258986572Z"),
            context(canaveral(), canaveralIgnition));

    double lunarRadius = GravitationalContext.moon().equatorialRadius();
    assertTrue(reading <= lunarRadius, "reading " + reading);
    assertTrue(reading > lunarRadius - 10_000.0, "reading " + reading);
  }

  @Test
  @DisplayName("An injection beyond what the active stage carries is refused, with the figures")
  void anInjectionBeyondTheStageIsRefusedWithTheFigures() {
    SpacecraftState ignition = kourouIgnition;
    Mission mission = mission(kourou());
    MissionStage tli = tliStage(mission);
    FlightContext context = tli.flightContext(ignition, mission);
    NumericalPropagator ballistic =
        OrekitService.get()
            .createOptimizationPropagator(context, tli.maxStepSeconds(ignition, mission));
    ballistic.setInitialState(ignition);
    ReentryGuard.armQuiet(ballistic, context.gravity());
    SpacecraftState parking =
        ballistic.propagate(TranslunarInjectionPlan.departureFrom(ignition).injectionDate());
    ActiveStageInfo active = mission.getVehicle().resolveActiveStage(ignition.getMass());

    AbsoluteDate arrival =
        parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS);
    double required = TranslunarInjectionPlan.keplerianInjectionDeltaV(parking, arrival);
    double exhaustVelocity = active.propulsion().isp() * Constants.G0_STANDARD_GRAVITY;
    double mass = ignition.getMass();
    double available = exhaustVelocity * FastMath.log(mass / (mass - active.remainingFuel(mass)));
    Vector3D planeNormal =
        Vector3D.crossProduct(parking.getPosition(), parking.getPVCoordinates().getVelocity())
            .normalize();
    double beta =
        FastMath.toDegrees(
            FastMath.asin(
                planeNormal.dotProduct(
                    OrekitService.get()
                        .body(SolarSystemBody.MOON)
                        .getPosition(arrival, parking.getFrame())
                        .normalize())));

    OrbitlabException refusal =
        assertThrows(
            OrbitlabException.class,
            () ->
                TranslunarInjectionPlan.inject(
                    ignition, parking, TARGET_PERILUNE_ALTITUDE, active, context));

    String message = refusal.getMessage();
    assertTrue(
        required > available,
        "the fixture must ask for more than the stage carries: " + required + " vs " + available);
    assertTrue(
        message.contains(String.format(Locale.ROOT, "%.0f m/s", required)),
        "the refusal must quote the ΔV the injection needs: " + message);
    assertTrue(
        message.contains(String.format(Locale.ROOT, "%.0f m/s", available)),
        "the refusal must quote the ΔV the stage can deliver: " + message);
    assertTrue(
        message.contains(String.format(Locale.ROOT, "%.2f°", beta)),
        "the refusal must quote how far the Moon sits off the parking plane: " + message);
  }

  /**
   * A burn capped by the propellant ends on the depletion floor to within rounding, so judging it
   * on its end mass refused it or not on the last bit. It is refused on the cap itself.
   */
  @Test
  @DisplayName("A burn capped by its propellant is refused, whatever mass it ends at")
  void aCappedBurnIsRefusedWhateverItsEndMass() {
    Fixture fixture = new Fixture();
    TranslunarInjectionPlan.Calibrated capped =
        new TranslunarInjectionPlan.Calibrated(
            40.0, 3_200.0, fixture.parking.withMass(fixture.active.depletionFloor() + 1.0), true);

    OrbitlabException refusal =
        assertThrows(
            OrbitlabException.class,
            () ->
                TranslunarInjectionPlan.refuseOrReturn(
                    fixture.plan, capped, fixture.active, fixture.mass));
    assertTrue(refusal.getMessage().contains("3200 m/s"), refusal.getMessage());
  }

  @Test
  @DisplayName("A burn the propellant covers is returned as calibrated")
  void anUncappedBurnIsReturned() {
    Fixture fixture = new Fixture();
    double endMass = fixture.active.depletionFloor() + 1.0;
    TranslunarInjectionPlan.Calibrated calibrated =
        new TranslunarInjectionPlan.Calibrated(
            40.0, 3_200.0, fixture.parking.withMass(endMass), false);

    TranslunarInjectionPlan.Burn burn =
        TranslunarInjectionPlan.refuseOrReturn(
            fixture.plan, calibrated, fixture.active, fixture.mass);

    assertEquals(40.0, burn.duration());
    assertEquals(3_200.0, burn.commandedDeltaV());
    assertEquals(endMass, burn.endMass());
  }

  /** A synthetic upper stage and a plan to refuse or return, no propagation involved. */
  private static final class Fixture {
    private final double mass = 20_000.0;
    private final SpacecraftState parking =
        TranslunarInjectionPlan.parkingState(
            new AbsoluteDate(2026, 3, 31, 0, 0, 0.0, TimeScalesFactory.getUTC()), mass);
    private final ActiveStageInfo active =
        new LaunchVehicle(5_000.0, mass - 5_000.0, new PropulsionSystem(348, 981_000.0))
            .resolveActiveStage(mass);
    private final TranslunarInjectionPlan plan =
        new TranslunarInjectionPlan(
            parking,
            new Vector3D(3_100.0, 0.0, 0.0),
            parking.getDate().shiftedBy(TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS),
            Vector3D.ZERO,
            0.0,
            TARGET_PERILUNE_ALTITUDE,
            Double.NaN);
  }

  /**
   * The context the TLI stage flies in — gravity, perturbers and drag — resolved by the stage
   * itself from a state on the upper stage, since the candidates end at its depletion floor.
   */
  private static FlightContext context(Map<String, Object> site, SpacecraftState onUpperStage) {
    Mission mission = mission(site);
    return tliStage(mission).flightContext(onUpperStage, mission);
  }

  private static double targetRadius() {
    return GravitationalContext.moon().equatorialRadius() + TARGET_PERILUNE_ALTITUDE;
  }

  private static Mission mission(Map<String, Object> site) {
    return new MissionEntry(MissionFactory.specFromWizardValues(site, MissionType.LUNAR_ORBIT))
        .mission();
  }

  private static MissionStage tliStage(Mission mission) {
    return mission.getStages().stream()
        .filter(TLIBurnStage.class::isInstance)
        .findFirst()
        .orElseThrow();
  }

  private static SpacecraftState state(
      String date, Vector3D position, Vector3D velocity, double mass) {
    return new SpacecraftState(
            new CartesianOrbit(
                new TimeStampedPVCoordinates(date(date), position, velocity),
                OrekitService.get().gcrf(),
                Constants.WGS84_EARTH_MU))
        .withMass(mass);
  }

  private static AbsoluteDate date(String iso) {
    return new AbsoluteDate(iso, TimeScalesFactory.getUTC());
  }

  private static Map<String, Object> canaveral() {
    Map<String, Object> values = lunarOrbiter();
    values.put("LAUNCH_SITE_LAT", 28.562);
    values.put("LAUNCH_SITE_LONG", -80.577);
    values.put("LAUNCH_SITE_ALT", 3.0);
    return values;
  }

  private static Map<String, Object> kourou() {
    Map<String, Object> values = lunarOrbiter();
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.775);
    values.put("LAUNCH_SITE_ALT", 0.0);
    return values;
  }

  private static Map<String, Object> lunarOrbiter() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "guard");
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "LUNAR_ORBITER");
    values.put("PAYLOAD_MASS", 2_000.0);
    values.put("LUNAR_ORBIT_ALT", 100.0);
    return values;
  }
}
