package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * <b>MIS-7 / P1.d, test T8</b> — a medium Earth orbit (Galileo/GPS altitude, 20 200 km, i = 55°)
 * flown on Ariane 62 (spec {@code docs/earth-orbit/01-mission-terre-parametrable.md} §6 and §9.2).
 *
 * <p><b>What makes a MEO different from a taller LEO.</b> Nothing about the target says so — it is
 * still a circular orbit with an inclination. What says so is the <em>vehicle</em>: reaching it
 * needs a parking orbit and a Hohmann transfer whose coast to apogee lasts 2 h 58, and an upper
 * stage has to survive shut down for all of it. The Ariane 62 ULPM declares 6 h and does; the Falcon
 * Heavy second stage declares 2 h and does not, which {@code EarthOrbitValidationTest} asserts as an
 * explicit refusal. The roadmap fiche called MEO "free" once the inclination was parametrable; it is
 * not, and that is why §6 gave it a lot of its own.
 *
 * <p><b>Nothing here is MEO-specific in the code.</b> No mission type, no objective, no stage: the
 * composition rule of §6.1 routes the target to the chain GEO already flies, with 20 200 km and 55°
 * where GEO passes 35 786 km and 0°. This fixture is what checks that the routing produces a mission
 * that actually closes.
 *
 * <p><b>Slow</b> — a complete mission optimization, like the LEO and GEO loops next to it.
 */
public class MeoMissionTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(MeoMissionTest.class);

  /** Galileo / GPS altitude. */
  private static final double MEO_ALTITUDE = 20_200_000.0;

  /** The constellation inclination §6 names. */
  private static final double MEO_INCLINATION_DEG = 55.0;

  private static final double PARKING_ALTITUDE = 400_000.0;

  private static final double LAT_DEG = 5.23;
  private static final double LON_DEG = -52.77;

  /** Spec §9.2's tolerances for this fixture. */
  private static final double ALTITUDE_TOLERANCE_RATIO = 0.07;

  private static final double INCLINATION_TOLERANCE_DEG = 0.5;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void ariane62_reachesMediumEarthOrbitThroughTheParkingChain() {
    MissionSpec.EarthOrbit spec = meoSpec();
    Mission mission = MissionComposer.compose(spec, OptimizationType.FAST);

    logger.info("[MEO] Stages: {}", mission.getStages().stream().map(s -> s.getName()).toList());
    logger.info(
        "[MEO] Budget loads: S1 {} kg, S2 {} kg, payload propellant {} kg",
        fmt(spec.configuration().propellantLoads()[0]),
        fmt(spec.configuration().propellantLoads()[1]),
        fmt(spec.configuration().payload().propellantLoad()));

    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    mission.setCurrentState(mission.getInitialState(epoch));
    MissionComputeResult result = new MissionOptimizer(mission, 40_000, TEST_SEED).optimize();
    MissionEphemeris ephemeris = result.ephemeris();

    MinMaxAltitudeResults coast = extractMinMaxAltitudes(ephemeris, "Coasting");
    MissionEphemerisPoint last = ephemeris.lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    double achievedInclinationDeg = FastMath.toDegrees(finalOrbit.getI());

    logger.info(
        "[MEO] Flown band {} - {} km (target {} km), inclination {}° (target {}°)",
        fmt(coast.minAltitude / 1000.0),
        fmt(coast.maxAltitude / 1000.0),
        fmt(MEO_ALTITUDE / 1000.0),
        String.format(Locale.ROOT, "%.4f", achievedInclinationDeg),
        fmt(MEO_INCLINATION_DEG));

    double margin = ALTITUDE_TOLERANCE_RATIO * MEO_ALTITUDE;
    Assertions.assertTrue(
        FastMath.abs(coast.maxAltitude - MEO_ALTITUDE) <= margin,
        () ->
            String.format(
                Locale.ROOT,
                "Max flown altitude %.0f m not within %.0f m of the %.0f m target",
                coast.maxAltitude,
                margin,
                MEO_ALTITUDE));
    Assertions.assertTrue(
        FastMath.abs(coast.minAltitude - MEO_ALTITUDE) <= margin,
        () ->
            String.format(
                Locale.ROOT,
                "Min flown altitude %.0f m not within %.0f m of the %.0f m target",
                coast.minAltitude,
                margin,
                MEO_ALTITUDE));
    Assertions.assertEquals(
        MEO_INCLINATION_DEG,
        achievedInclinationDeg,
        INCLINATION_TOLERANCE_DEG,
        "the commanded plane must survive the transfer and the trim");
  }

  /**
   * The spec, built by hand — which is what P1 is for (§0: "specs built by hand in the unit tests",
   * the wizard being P2). The plane is commanded at 55°, so the ascent steers into it and the
   * composition picks up the plane trim on its own.
   */
  private static MissionSpec.EarthOrbit meoSpec() {
    PayloadModel model = Payloads.GEO_SAT;
    double payloadDryMass = model.defaultDryMass();
    LaunchPlane plane = LaunchPlane.ofDegrees(MEO_INCLINATION_DEG, NodeBranch.ASCENDING);
    double azimuth = plane.launchAzimuth(FastMath.toRadians(LAT_DEG));

    // The plane change charged at apogee is zero: the ascent already flew into the target plane
    // (MIS-7 §4), so only the residual is left, and the plane trim takes that.
    PropellantBudget.GeoLoads loads =
        PropellantBudget.loadsForHighOrbit(
            Launchers.ARIANE_62,
            model,
            payloadDryMass,
            PARKING_ALTITUDE,
            MEO_ALTITUDE,
            LAT_DEG,
            0.0,
            azimuth);
    Spacecraft payload = model.toSpacecraft(payloadDryMass, loads.akmLoad());

    return new MissionSpec.EarthOrbit(
        "MEO Galileo",
        new LaunchConfiguration(
            Launchers.ARIANE_62, loads.launcherLoads(), payload, model.id()),
        MEO_ALTITUDE,
        MEO_ALTITUDE,
        plane.targetInclination(),
        plane.nodeBranch(),
        "Kourou",
        LAT_DEG,
        LON_DEG,
        0.0,
        null);
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
