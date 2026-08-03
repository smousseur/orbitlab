package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.GEOMission;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.StagePerformance;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/**
 * N2 non-regression baseline of the ascent, for the explicit-staging migration (spec
 * {@code specs/mission-stages/01-separations-implicites.md} §7.1, étape 0).
 *
 * <p>Splitting the gravity turn into {@code Gravity turn (S1) → S1 separation → Gravity turn (S2)}
 * restarts the adaptive integrator at each new phase boundary, so the refactor cannot be
 * bit-identical. Non-regression is therefore defined by measured tolerances on the quantities the
 * split could plausibly move: the MECO state, the final orbit, and the CMA-ES solution retained at
 * the fixed seed 42.
 *
 * <p>The recorded values in {@link #LEO_400_BASELINE} / {@link #GEO_BASELINE} were captured on the
 * pre-split code and are the reference each migration step is compared against. The measured
 * snapshot is also written to {@code build/baseline/<profile>-n2.txt} on every run, so two steps
 * can be diffed directly.
 *
 * <p><b>Slow.</b> Each fixture is a complete mission optimization (LEO ~1 min, GEO ~2 min), so the
 * class is opt-in like the other integration loops:
 *
 * <pre>{@code
 * gradlew test --tests "*AscentBaselineN2Test" -Dorbitlab.slowTests=true
 * }</pre>
 *
 * <p>The two fixtures are the ones the N1 hard criteria already fly: {@code
 * LEOMissionOptimizationTest#testFalconHeavy} (Falcon Heavy, LEO 400 km) and {@code
 * GEOMissionOptimizationTest#testGEOMission} (Falcon Heavy, parking 400 km → GEO).
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
public class AscentBaselineN2Test extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(AscentBaselineN2Test.class);

  /**
   * Fixed launch epoch, identical to the N1 optimization tests. Built on demand: the UTC scale is
   * only available once {@link OrekitService#initialize()} has run.
   */
  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static final int MAX_EVALUATIONS = 40_000;

  /** Every ascent phase name starts with this, before and after the split. */
  private static final String ASCENT_PHASE_PREFIX = "Gravity turn";

  /** Optimization key of the gravity-turn problem; unchanged by the split (spec §5.2). */
  private static final String GRAVITY_TURN_KEY = "Gravity turn";

  // ── N2 tolerances (spec §7.1) ────────────────────────────────────────────
  private static final double MECO_DATE_TOLERANCE_S = 1.0e-3;
  private static final double MECO_MASS_TOLERANCE_KG = 1.0;
  private static final double MECO_POSITION_TOLERANCE_M = 10.0;
  private static final double MECO_VELOCITY_TOLERANCE_MPS = 0.05;
  private static final double ORBIT_RELATIVE_TOLERANCE = 1.0e-3; // 0.1 %
  private static final double INCLINATION_TOLERANCE_DEG = 0.01;
  private static final double TRANSITION_TIME_TOLERANCE_S = 0.5;

  // ── Recorded baseline, pre-split (2026-08-03, seed 42) ───────────────────
  // Captured on the code at commit 1d53e83, before étape 1 of the migration. Full snapshots (ΔV,
  // per-stage accounting, ephemeris point counts) are in specs/mission-stages/02-baseline-n2.md.
  // Set a profile back to null to re-capture it after a deliberate behavior change (étape 5).

  private static final Baseline LEO_400_BASELINE =
      new Baseline(
          307.851992,
          new MecoState(
              314.851992,
              36178.699,
              new Vector3D(-3065843.366118, -5677509.391909, 577855.801484),
              new Vector3D(6964.499845, -3779.273106, -188.095349)),
          new MecoState(
              314.851992,
              36178.699,
              new Vector3D(-3065843.366118, -5677509.391909, 577855.801484),
              new Vector3D(6964.499845, -3779.273106, -188.095349)),
          new OrbitShape(381147.9, 419094.3, 5.304766),
          8.3);

  private static final Baseline GEO_BASELINE =
      new Baseline(
          329.529709,
          new MecoState(
              336.529709,
              64463.304,
              new Vector3D(-2970466.097849, -5651430.525919, 569881.183242),
              new Vector3D(7056.973316, -3729.500415, -198.078380)),
          new MecoState(
              336.529709,
              64463.304,
              new Vector3D(-2970466.097849, -5651430.525919, 569881.183242),
              new Vector3D(7056.973316, -3729.500415, -198.078380)),
          new OrbitShape(35784683.4, 35789627.6, 0.000034),
          11.5);

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void leo400Baseline() {
    LEOMission mission =
        new LEOMission(
            "Falcon Heavy",
            new LaunchConfiguration(
                Launchers.FALCON_HEAVY, new double[] {600_000, 100_000}, Spacecraft.LEGACY),
            400_000);
    Snapshot snapshot = capture("leo-400", mission);
    compare(LEO_400_BASELINE, snapshot);
  }

  @Test
  void geoBaseline() {
    GEOMission mission = new GEOMission("GTO mission", 400_000, 35_786_000);
    Snapshot snapshot = capture("geo", mission);
    compare(GEO_BASELINE, snapshot);
  }

  /** Flies one mission at the fixed seed and reads every N2 quantity off the result. */
  private Snapshot capture(String profile, Mission mission) {
    SpacecraftState initialState = mission.getInitialState(epoch());
    mission.setCurrentState(initialState);

    long startNanos = System.nanoTime();
    MissionComputeResult result =
        new MissionOptimizer(mission, MAX_EVALUATIONS, TEST_SEED).optimize();
    double optimizeSeconds = (System.nanoTime() - startNanos) / 1.0e9;

    MissionEphemeris ephemeris = result.ephemeris();
    OptimizationResult gt =
        result
            .optimizerResult()
            .findFor(GRAVITY_TURN_KEY)
            .orElseThrow(() -> new AssertionError("no result for stage " + GRAVITY_TURN_KEY));

    AbsoluteDate launchDate = ephemeris.firstPoint().time();
    MecoState mecoOptimize = mecoOf(gt.bestState(), launchDate);
    MecoState mecoEphemeris = mecoFromEphemeris(ephemeris, launchDate);

    MissionEphemerisPoint last = ephemeris.lastPoint();
    KeplerianOrbit finalOrbit =
        new KeplerianOrbit(
            new PVCoordinates(last.position(), last.velocity()),
            OrekitService.get().gcrf(),
            last.time(),
            Constants.WGS84_EARTH_MU);
    double semiMajorAxis = finalOrbit.getA();
    double eccentricity = finalOrbit.getE();
    double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
    OrbitShape shape =
        new OrbitShape(
            semiMajorAxis * (1.0 - eccentricity) - earthRadius,
            semiMajorAxis * (1.0 + eccentricity) - earthRadius,
            FastMath.toDegrees(finalOrbit.getI()));

    double[] variables = gt.bestVariables();
    Snapshot snapshot =
        new Snapshot(
            profile,
            variables[0],
            variables[1],
            gt.bestCost(),
            gt.evaluations(),
            mecoOptimize,
            mecoEphemeris,
            shape,
            last.mass(),
            result.performanceReport().totalDeltaV(),
            ephemeris.size(),
            ephemeris.isComplete(),
            optimizeSeconds);

    String report = format(snapshot, result);
    logger.info("N2 baseline snapshot [{}]:\n{}", profile, report);
    write(profile, report);
    return snapshot;
  }

  /** The MECO as the ephemeris sees it: the last sample of the last ascent phase. */
  private static MecoState mecoFromEphemeris(MissionEphemeris ephemeris, AbsoluteDate launchDate) {
    MissionEphemerisPoint meco = null;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (point.stageName().startsWith(ASCENT_PHASE_PREFIX)) {
        meco = point;
      }
    }
    Assertions.assertNotNull(meco, "no ephemeris point belongs to an ascent phase");
    return new MecoState(
        meco.time().durationFrom(launchDate), meco.mass(), meco.position(), meco.velocity());
  }

  private static MecoState mecoOf(SpacecraftState state, AbsoluteDate launchDate) {
    return new MecoState(
        state.getDate().durationFrom(launchDate),
        state.getMass(),
        state.getPosition(),
        state.getPVCoordinates().getVelocity());
  }

  /**
   * Compares a fresh snapshot to the recorded baseline at the §7.1 tolerances. When no baseline has
   * been recorded yet the run is a capture only: the report above is the material to record.
   */
  private static void compare(Baseline reference, Snapshot got) {
    if (reference == null) {
      logger.warn(
          "No N2 baseline recorded for profile '{}' — capture-only run, nothing compared",
          got.profile());
      return;
    }
    assertMeco("optimize-pass MECO", reference.mecoOptimize(), got.mecoOptimize());
    assertMeco("ephemeris-pass MECO", reference.mecoEphemeris(), got.mecoEphemeris());

    assertRelative(
        "final perigee altitude",
        reference.finalOrbit().perigeeAltitude(),
        got.finalOrbit().perigeeAltitude());
    assertRelative(
        "final apogee altitude",
        reference.finalOrbit().apogeeAltitude(),
        got.finalOrbit().apogeeAltitude());
    Assertions.assertEquals(
        reference.finalOrbit().inclinationDeg(),
        got.finalOrbit().inclinationDeg(),
        INCLINATION_TOLERANCE_DEG,
        "final inclination moved beyond the N2 tolerance");

    Assertions.assertEquals(
        reference.transitionTime(),
        got.transitionTime(),
        TRANSITION_TIME_TOLERANCE_S,
        "CMA-ES transitionTime moved beyond the N2 tolerance at the fixed seed");

    // N3 is watched, not enforced: wall-clock timings vary too much across machines to assert on.
    logger.info(
        "N3 [{}]: optimization took {} s (baseline {} s, ratio {})",
        got.profile(),
        fmt(got.optimizeSeconds(), 1),
        fmt(reference.optimizeSeconds(), 1),
        fmt(got.optimizeSeconds() / reference.optimizeSeconds(), 2));
  }

  private static void assertMeco(String label, MecoState reference, MecoState got) {
    Assertions.assertEquals(
        reference.timeSinceLaunch(),
        got.timeSinceLaunch(),
        MECO_DATE_TOLERANCE_S,
        label + ": date moved beyond the N2 tolerance");
    Assertions.assertEquals(
        reference.mass(), got.mass(), MECO_MASS_TOLERANCE_KG, label + ": mass moved beyond 1 kg");
    double deltaPosition = Vector3D.distance(reference.position(), got.position());
    Assertions.assertTrue(
        deltaPosition < MECO_POSITION_TOLERANCE_M,
        () -> String.format(Locale.ROOT, "%s: Δpos %.3f m exceeds 10 m", label, deltaPosition));
    double deltaVelocity = Vector3D.distance(reference.velocity(), got.velocity());
    Assertions.assertTrue(
        deltaVelocity < MECO_VELOCITY_TOLERANCE_MPS,
        () ->
            String.format(
                Locale.ROOT, "%s: Δvelocity %.5f m/s exceeds 0.05 m/s", label, deltaVelocity));
  }

  private static void assertRelative(String label, double reference, double got) {
    double relative = FastMath.abs(got - reference) / FastMath.abs(reference);
    Assertions.assertTrue(
        relative < ORBIT_RELATIVE_TOLERANCE,
        () ->
            String.format(
                Locale.ROOT,
                "%s: %.1f m vs baseline %.1f m — relative gap %.4f%% exceeds 0.1%%",
                label,
                got,
                reference,
                100.0 * relative));
  }

  /** Human-readable and paste-ready report of one snapshot. */
  private static String format(Snapshot s, MissionComputeResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("profile                  : ").append(s.profile()).append('\n');
    sb.append("seed                     : ").append(TEST_SEED).append('\n');
    sb.append("epoch                    : ").append(epoch()).append('\n');
    sb.append("optimize wall clock (s)  : ").append(fmt(s.optimizeSeconds(), 1)).append('\n');
    sb.append("ephemeris points         : ")
        .append(s.ephemerisPoints())
        .append(" (complete=")
        .append(s.complete())
        .append(")\n");
    sb.append("CMA-ES transitionTime (s): ").append(fmt(s.transitionTime(), 6)).append('\n');
    sb.append("CMA-ES exponent          : ").append(fmt(s.exponent(), 6)).append('\n');
    sb.append("CMA-ES cost / evaluations: ")
        .append(fmt(s.gravityTurnCost(), 6))
        .append(" / ")
        .append(s.gravityTurnEvaluations())
        .append('\n');
    appendMeco(sb, "MECO (optimize pass)", s.mecoOptimize());
    appendMeco(sb, "MECO (ephemeris pass)", s.mecoEphemeris());
    MecoState optimize = s.mecoOptimize();
    MecoState ephemeris = s.mecoEphemeris();
    sb.append("MECO optimize↔ephemeris  : Δpos=")
        .append(fmt(Vector3D.distance(optimize.position(), ephemeris.position()), 3))
        .append(" m, Δvel=")
        .append(fmt(Vector3D.distance(optimize.velocity(), ephemeris.velocity()), 5))
        .append(" m/s, Δmass=")
        .append(fmt(s.mecoOptimize().mass() - s.mecoEphemeris().mass(), 3))
        .append(" kg\n");
    sb.append("final perigee (m)        : ")
        .append(fmt(s.finalOrbit().perigeeAltitude(), 1))
        .append('\n');
    sb.append("final apogee (m)         : ")
        .append(fmt(s.finalOrbit().apogeeAltitude(), 1))
        .append('\n');
    sb.append("final inclination (deg)  : ")
        .append(fmt(s.finalOrbit().inclinationDeg(), 6))
        .append('\n');
    sb.append("final mass (kg)          : ").append(fmt(s.finalMass(), 3)).append('\n');
    sb.append("report total ΔV (m/s)    : ").append(fmt(s.totalDeltaV(), 3)).append('\n');
    for (StagePerformance sp : result.performanceReport().stages()) {
      sb.append("  stage ")
          .append(String.format(Locale.ROOT, "%-22s", "'" + sp.stageName() + "'"))
          .append(" massIn=")
          .append(fmt(sp.massIn(), 1))
          .append(" kg, massOut=")
          .append(fmt(sp.massOut(), 1))
          .append(" kg, propellant=")
          .append(fmt(sp.propellantConsumed(), 1))
          .append(" kg, ΔV=")
          .append(fmt(sp.deltaV(), 1))
          .append(" m/s\n");
    }
    for (StagePropellant sp : result.performanceReport().stagePropellants()) {
      sb.append("  propellant [")
          .append(sp.stageIndex())
          .append("] loaded=")
          .append(fmt(sp.loaded(), 1))
          .append(" kg, consumed=")
          .append(fmt(sp.consumed(), 1))
          .append(" kg, residual=")
          .append(fmt(sp.residual(), 1))
          .append(" kg\n");
    }
    return sb.toString();
  }

  private static void appendMeco(StringBuilder sb, String label, MecoState meco) {
    sb.append(String.format(Locale.ROOT, "%-25s: ", label))
        .append("t+")
        .append(fmt(meco.timeSinceLaunch(), 6))
        .append(" s, mass=")
        .append(fmt(meco.mass(), 3))
        .append(" kg\n")
        .append("      position (m)       : ")
        .append(vector(meco.position()))
        .append('\n')
        .append("      velocity (m/s)     : ")
        .append(vector(meco.velocity()))
        .append('\n');
  }

  private static String vector(Vector3D v) {
    return String.format(Locale.ROOT, "%.6f, %.6f, %.6f", v.getX(), v.getY(), v.getZ());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }

  private static void write(String profile, String report) {
    try {
      Path directory = Path.of("build", "baseline");
      Files.createDirectories(directory);
      Path file = directory.resolve(profile + "-n2.txt");
      Files.writeString(file, report, StandardCharsets.UTF_8);
      logger.info("N2 baseline written to {}", file.toAbsolutePath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The spacecraft state at main-engine cutoff, relative to the launch date. */
  private record MecoState(
      double timeSinceLaunch, double mass, Vector3D position, Vector3D velocity) {}

  /** Shape of the orbit reached at mission end. */
  private record OrbitShape(
      double perigeeAltitude, double apogeeAltitude, double inclinationDeg) {}

  /** Everything §7.1 asks to record, measured on one run. */
  private record Snapshot(
      String profile,
      double transitionTime,
      double exponent,
      double gravityTurnCost,
      int gravityTurnEvaluations,
      MecoState mecoOptimize,
      MecoState mecoEphemeris,
      OrbitShape finalOrbit,
      double finalMass,
      double totalDeltaV,
      int ephemerisPoints,
      boolean complete,
      double optimizeSeconds) {}

  /** The recorded reference a later run is compared against. */
  private record Baseline(
      double transitionTime,
      MecoState mecoOptimize,
      MecoState mecoEphemeris,
      OrbitShape finalOrbit,
      double optimizeSeconds) {}
}
