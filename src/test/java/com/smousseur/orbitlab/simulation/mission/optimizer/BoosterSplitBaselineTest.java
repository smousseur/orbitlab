package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.operation.NodeBranch;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.StagePerformance;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * <b>PHY-8 / L0 — the booster-split baseline</b> (spec {@code docs/etagement/01-decoupage.md} §5).
 *
 * <p>PHY-8 splits the boosters from the core stage: {@code L2} turns the Falcon Heavy's aggregated
 * S1 into {@code [boosters ×2, core]}, {@code L3} throttles the core, and {@code L4} replaces the
 * Ariane 64 with an Ariane 64. The first two are meant to be iso-trajectory and the last two are
 * not, so every lot has to be able to say which numbers it moved. This fixture is the
 * <em>before</em> they are all read against, and it is re-run unchanged at each lot.
 *
 * <p><b>It measures, it does not gate.</b> The only assertions are that a cell flew to the end of
 * its chain — a baseline that failed on a tolerance would be establishing the tolerance it is
 * asserting. What is compared across lots is the written report, read by a human.
 *
 * <p><b>The launch dates are pinned, including the lunar ones.</b> The lunar fixtures pick their
 * epoch from a window search that screens on the vehicle, so PHY-8 changing the vehicle would also
 * change the date, and a MECO measured at one date against a MECO measured at another compares
 * nothing. See {@link #lunarEpoch()} for the date and for the measurement that shows the screening
 * is real.
 *
 * <p><b>What it deliberately does not cover.</b> The two profiles {@code AscentBaselineN2Test}
 * already snapshots — Falcon Heavy LEO-400 at the hand-written {@code {600 000, 100 000}} loads,
 * and the fully-loaded GEO — are read from {@code build/baseline/<profile>-n2.txt} instead. Flying
 * them twice per lot would buy a second copy of the same numbers, and the {@code gateTest} task
 * regenerates that snapshot on every run anyway.
 *
 * <p><b>Half of what L0 records needs no propagation.</b> {@code PropellantBudget} sizes only the
 * top stage — its own javadoc says "the ΔV left over by the fully-loaded lower stages" — so the
 * loads, the lift-off mass, the thrust-to-weight ratio and the first stage's burn duration all
 * follow from the catalog. They are reported first, and the flown section is what the propagation
 * adds.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class BoosterSplitBaselineTest {
  private static final Logger logger = LogManager.getLogger(BoosterSplitBaselineTest.class);

  /** The evaluation budget and the seed every mission optimization test of the repo uses. */
  private static final int MAX_EVALUATIONS = 40_000;

  private static final long SEED = 42L;

  private static final double G0 = Constants.G0_STANDARD_GRAVITY;

  private static final double KOUROU_LAT = 5.23;
  private static final double KOUROU_LON = -52.77;
  private static final double KOUROU_ALT = 0.0;

  private static final double CANAVERAL_LAT = 28.562;
  private static final double CANAVERAL_LON = -80.577;
  private static final double CANAVERAL_ALT = 3.0;

  private static final double PARKING_ALTITUDE = 400_000.0;
  private static final double LEO_ALTITUDE = 400_000.0;
  private static final double GEO_ALTITUDE = 35_786_000.0;
  private static final double MEO_ALTITUDE = 20_200_000.0;
  private static final double MEO_INCLINATION_DEG = 55.0;
  private static final double LUNAR_ALTITUDE = 100_000.0;

  /** Every ascent phase name starts with this, on all four mission types. */
  private static final String ASCENT_PHASE_PREFIX = "Gravity turn";

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  /** The epoch the Earth profiles of the repository all fly from. */
  private static AbsoluteDate earthEpoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  @Test
  void falconHeavyLeo400OnBudgetLoads() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000.0, 0.0);
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.FALCON_HEAVY,
            PropellantBudget.loadsForLeo(Launchers.FALCON_HEAVY, payload, LEO_ALTITUDE, KOUROU_LAT),
            payload);
    measure(
        "falcon-heavy-leo-400",
        "Earth observation satellite, 10 t dry",
        configuration,
        new EarthOrbitMission("Falcon Heavy LEO 400", configuration, LEO_ALTITUDE),
        earthEpoch());
  }

  @Test
  void ariane64Leo400OnBudgetLoads() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(5_000.0, 0.0);
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.ARIANE_64,
            PropellantBudget.loadsForLeo(Launchers.ARIANE_64, payload, LEO_ALTITUDE, KOUROU_LAT),
            payload);
    measure(
        "ariane-64-leo-400",
        "Earth observation satellite, 5 t dry",
        configuration,
        new EarthOrbitMission("Ariane 64 LEO 400", configuration, LEO_ALTITUDE),
        earthEpoch());
  }

  /**
   * The Galileo profile: the ascent is steered into the 55° plane, so the apogee burn only
   * circularizes.
   */
  @Test
  void ariane64MediumEarthOrbit() {
    PayloadModel model = Payloads.GEO_SAT;
    LaunchPlane plane = LaunchPlane.ofDegrees(MEO_INCLINATION_DEG, NodeBranch.ASCENDING);
    PropellantBudget.GeoLoads loads =
        PropellantBudget.loadsForHighOrbit(
            Launchers.ARIANE_64,
            model,
            model.defaultDryMass(),
            PARKING_ALTITUDE,
            MEO_ALTITUDE,
            KOUROU_LAT,
            0.0,
            plane.launchAzimuth(FastMath.toRadians(KOUROU_LAT)));
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.ARIANE_64,
            loads.launcherLoads(),
            model.toSpacecraft(model.defaultDryMass(), loads.akmLoad()),
            model.id());
    MissionSpec.EarthOrbit spec =
        new MissionSpec.EarthOrbit(
            "Ariane 64 MEO",
            configuration,
            MEO_ALTITUDE,
            MEO_ALTITUDE,
            plane.targetInclination(),
            plane.nodeBranch(),
            "Kourou",
            KOUROU_LAT,
            KOUROU_LON,
            KOUROU_ALT,
            null);
    measure(
        "ariane-64-meo",
        "GEO communications satellite, 2 t dry",
        configuration,
        MissionComposer.compose(spec, OptimizationType.FAST),
        earthEpoch());
  }

  /**
   * The cell no fixture covers today, and the one {@code L4} will produce an <em>after</em> for: an
   * Ariane 64 is a geostationary launcher where the Ariane 64 in the catalog is not specified to be
   * one. Whether this chain closes is itself the measurement.
   */
  @Test
  void ariane64Geostationary() {
    PayloadModel model = Payloads.GEO_SAT;
    PropellantBudget.GeoLoads loads =
        PropellantBudget.loadsForGeo(
            Launchers.ARIANE_64, model, model.defaultDryMass(), PARKING_ALTITUDE, KOUROU_LAT);
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.ARIANE_64,
            loads.launcherLoads(),
            model.toSpacecraft(model.defaultDryMass(), loads.akmLoad()),
            model.id());
    measure(
        "ariane-64-geo",
        "GEO communications satellite, 2 t dry",
        configuration,
        new GEOMission(
            "Ariane 64 GEO",
            configuration,
            PARKING_ALTITUDE,
            GEO_ALTITUDE,
            KOUROU_LAT,
            KOUROU_LON,
            KOUROU_ALT,
            0.0),
        earthEpoch());
  }

  @Test
  void falconHeavyLunarFlyby() {
    Spacecraft probe = Payloads.LUNAR_PROBE.toSpacecraft(2_000.0, 0.0);
    PropellantBudget.LunarLoads loads =
        PropellantBudget.loadsForLunar(
            Launchers.FALCON_HEAVY, probe, PARKING_ALTITUDE, CANAVERAL_LAT, FastMath.PI / 2);
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.FALCON_HEAVY, loads.launcherLoads(), probe, Payloads.LUNAR_PROBE.id());
    MissionSpec.Lunar spec =
        new MissionSpec.Lunar(
            "Falcon Heavy lunar flyby",
            configuration,
            PARKING_ALTITUDE,
            LUNAR_ALTITUDE,
            "Cape Canaveral",
            CANAVERAL_LAT,
            CANAVERAL_LON,
            CANAVERAL_ALT,
            null,
            null);
    measure(
        "falcon-heavy-lunar-flyby",
        "Lunar probe, 2 t dry, inert",
        configuration,
        MissionComposer.compose(spec, OptimizationType.FAST),
        lunarEpoch());
  }

  @Test
  void falconHeavyLunarOrbit() {
    PropellantBudget.LunarOrbitLoads loads =
        PropellantBudget.loadsForLunarOrbit(
            Launchers.FALCON_HEAVY,
            Payloads.LUNAR_ORBITER,
            2_000.0,
            PARKING_ALTITUDE,
            LUNAR_ALTITUDE,
            CANAVERAL_LAT,
            FastMath.PI / 2);
    LaunchConfiguration configuration =
        new LaunchConfiguration(
            Launchers.FALCON_HEAVY,
            loads.launcherLoads(),
            Payloads.LUNAR_ORBITER.toSpacecraft(2_000.0, loads.insertionLoad()),
            Payloads.LUNAR_ORBITER.id());
    MissionSpec.LunarOrbit spec =
        new MissionSpec.LunarOrbit(
            "Falcon Heavy lunar orbit",
            configuration,
            PARKING_ALTITUDE,
            LUNAR_ALTITUDE,
            "Cape Canaveral",
            CANAVERAL_LAT,
            CANAVERAL_LON,
            CANAVERAL_ALT,
            null,
            null);
    measure(
        "falcon-heavy-lunar-orbit",
        "Lunar orbiter, 2 t dry, propelled",
        configuration,
        MissionComposer.compose(spec, OptimizationType.FAST),
        lunarEpoch());
  }

  /** Flies one cell and writes its report. */
  private void measure(
      String cell,
      String payloadLabel,
      LaunchConfiguration configuration,
      Mission mission,
      AbsoluteDate launchDate) {
    mission.setCurrentState(mission.getInitialState(launchDate));

    long startedAt = System.nanoTime();
    MissionComputeResult result = new MissionOptimizer(mission, MAX_EVALUATIONS, SEED).optimize();
    double wallSeconds = (System.nanoTime() - startedAt) / 1.0e9;

    String report = format(cell, payloadLabel, configuration, launchDate, result, wallSeconds);
    logger.info("PHY-8 L0 baseline [{}]:\n{}", cell, report);
    write(cell, report);

    Assertions.assertTrue(
        result.ephemeris().isComplete(),
        "the " + cell + " cell did not fly to the end of its chain");
  }

  private static String format(
      String cell,
      String payloadLabel,
      LaunchConfiguration configuration,
      AbsoluteDate launchDate,
      MissionComputeResult result,
      double wallSeconds) {
    LauncherModel launcher = configuration.launcher();
    StageModel first = launcher.stages().getFirst();
    double[] loads = configuration.propellantLoads();
    double liftOffMass = configuration.toVehicleStack().getMass();
    double thrust = first.propulsion().thrust();
    double massFlow = thrust / (first.propulsion().isp() * G0);
    ActiveStageInfo padStage = configuration.toVehicleStack().resolveActiveStage(liftOffMass);

    StringBuilder sb = new StringBuilder(4_096);
    sb.append("cell                     : ").append(cell).append('\n');
    sb.append("launcher                 : ").append(launcher.displayName()).append('\n');
    sb.append("payload                  : ").append(payloadLabel).append('\n');
    sb.append("launch date              : ").append(launchDate).append('\n');
    sb.append("seed / evaluation budget : ")
        .append(SEED)
        .append(" / ")
        .append(MAX_EVALUATIONS)
        .append('\n');
    sb.append("optimize wall clock (s)  : ").append(fmt(wallSeconds, 1)).append('\n');

    sb.append("\n-- catalog, no propagation --\n");
    sb.append("propellant loads (kg)    : ").append(Arrays.toString(rounded(loads))).append('\n');
    sb.append("lift-off mass (kg)       : ").append(fmt(liftOffMass, 1)).append('\n');
    sb.append("stage 0 thrust (N)       : ").append(fmt(thrust, 0)).append('\n');
    sb.append("stage 0 Isp (s)          : ").append(fmt(first.propulsion().isp(), 1)).append('\n');
    sb.append("stage 0 section (m2)     : ")
        .append(fmt(padStage.aerodynamics().crossSection(), 3))
        .append('\n');
    sb.append("stage 0 mass flow (kg/s) : ").append(fmt(massFlow, 2)).append('\n');
    sb.append("lift-off T/W             : ")
        .append(fmt(thrust / (liftOffMass * G0), 4))
        .append('\n');
    sb.append("stage 0 burn (s)         : ").append(fmt(loads[0] / massFlow, 2)).append('\n');

    MissionEphemeris ephemeris = result.ephemeris();
    AbsoluteDate liftOff = ephemeris.firstPoint().time();
    sb.append("\n-- flown --\n");
    sb.append("ephemeris points         : ")
        .append(ephemeris.size())
        .append(" (complete=")
        .append(ephemeris.isComplete())
        .append(")\n");
    sb.append("stage 0 flame-out (s)    : ").append(flameOut(ephemeris, liftOff)).append('\n');
    MissionEphemerisPoint meco = lastAscentPoint(ephemeris);
    sb.append("MECO                     : t+")
        .append(fmt(meco.time().durationFrom(liftOff), 6))
        .append(" s, mass=")
        .append(fmt(meco.mass(), 3))
        .append(" kg\n");
    sb.append("achieved orbit (osc.)    : ")
        .append(
            result.achievedOrbit().hasOsculating()
                ? result.achievedOrbit().formatOsculating()
                : "unavailable")
        .append('\n');
    sb.append("achieved orbit (mean)    : ")
        .append(
            result.achievedOrbit().hasMean() ? result.achievedOrbit().formatMean() : "unavailable")
        .append('\n');
    sb.append("report total dV (m/s)    : ")
        .append(fmt(result.performanceReport().totalDeltaV(), 3))
        .append('\n');

    for (StagePerformance stage : result.performanceReport().stages()) {
      sb.append("  stage ")
          .append(String.format(Locale.ROOT, "%-24s", "'" + stage.stageName() + "'"))
          .append(" massIn=")
          .append(fmt(stage.massIn(), 1))
          .append(" kg, massOut=")
          .append(fmt(stage.massOut(), 1))
          .append(" kg, propellant=")
          .append(fmt(stage.propellantConsumed(), 1))
          .append(" kg, dV=")
          .append(fmt(stage.deltaV(), 1))
          .append(" m/s, duration=")
          .append(fmt(stage.durationSeconds(), 1))
          .append(" s\n");
    }
    for (StagePropellant propellant : result.performanceReport().stagePropellants()) {
      sb.append("  propellant [")
          .append(propellant.stageIndex())
          .append("] loaded=")
          .append(fmt(propellant.loaded(), 1))
          .append(" kg, consumed=")
          .append(fmt(propellant.consumed(), 1))
          .append(" kg, residual=")
          .append(fmt(propellant.residual(), 1))
          .append(" kg\n");
    }
    return sb.toString();
  }

  /**
   * The instant the bottom stage hands over, read off the ephemeris: the separation phase opens on
   * flame-out, so its first sample dates it. Reported as text because a chain that never staged has
   * no such instant, and printing that is more useful than throwing.
   */
  private static String flameOut(MissionEphemeris ephemeris, AbsoluteDate liftOff) {
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (AscentSequence.SEPARATION_NAME.equals(point.stageName())) {
        return fmt(point.time().durationFrom(liftOff), 2);
      }
    }
    return "never staged";
  }

  private static MissionEphemerisPoint lastAscentPoint(MissionEphemeris ephemeris) {
    MissionEphemerisPoint meco = null;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (point.stageName().startsWith(ASCENT_PHASE_PREFIX)) {
        meco = point;
      }
    }
    Assertions.assertNotNull(meco, "no ephemeris point belongs to an ascent phase");
    return meco;
  }

  private static long[] rounded(double[] values) {
    long[] out = new long[values.length];
    for (int i = 0; i < values.length; i++) {
      out[i] = FastMath.round(values[i]);
    }
    return out;
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }

  private static void write(String cell, String report) {
    try {
      Path directory = Path.of("build", "baseline", "phy8");
      Files.createDirectories(directory);
      Path file = directory.resolve(cell + ".txt");
      Files.writeString(file, report, StandardCharsets.UTF_8);
      logger.info("PHY-8 L0 baseline written to {}", file.toAbsolutePath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The epoch both sized lunar chains fly, measured 2026-09-08 at commit {@code 8beec1a}: the 26 h
   * search from 2026-03-31 offers it to the flyby at 3 124 m/s, and {@code
   * LunarLaunchWindowPlanner} offers the same date to the orbiter on its first attempt.
   *
   * <p><b>It is a property of the vehicle, which is what PHY-8 changes.</b> On the same search the
   * fully-loaded flyby — a different mass at injection — was <em>refused</em> this date by the
   * chain ("the aim did not reach the 100 km perilune: best is 132 km") and flew
   * 2026-03-31T22:31:30Z instead. A later lot that gets that refusal here has measured something,
   * and must not answer it by searching for another date: two MECOs at two epochs compare nothing.
   */
  private static AbsoluteDate lunarEpoch() {
    return new AbsoluteDate(2026, 3, 31, 16, 25, 25.0, TimeScalesFactory.getUTC());
  }
}
