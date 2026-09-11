package com.smousseur.orbitlab.simulation.mission.ephemeris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * <b>PHY-4 / L3 — the multi-arc ephemeris gate</b> (spec {@code
 * docs/multi-corps/05-conception-L3.md} §6.4).
 *
 * <p><b>What it guards.</b> L3 makes the frame of a trajectory explicit: {@link
 * MissionEphemerisPoint} gains an arc, {@link TrajectoryPolyline} gains a second partition beside
 * its phase runs, and the decimation budget has to reserve headroom for the <em>union</em> of the
 * two sets of forced vertices rather than for their sum (spec §4.1). Nothing produces a second arc,
 * so the drawn line of a real mission must come out of the lot identical — vertex for vertex.
 *
 * <p><b>Why a flown mission and not the synthetic argument.</b> Spec §4.1 shows that with a single
 * arc the union is the run starts, so the selection is unchanged <em>by construction</em>, and
 * {@code TrajectoryPolylineTest.overTheBudget_theKeptVerticesArePinned} pins that reasoning at the
 * unit level in milliseconds. L2 was allowed to stop there because its production code was
 * untouched; here it is rewritten, so the argument bears on what is being changed. Spec §5.2 of L1
 * is the precedent: a structural claim about coverage turned out to be false and only the
 * measurement showed it.
 *
 * <p><b>The fixture is copied, not borrowed.</b> {@code CentralBodyBaselineTest} makes the rule
 * explicit for itself — "a gate must not rest on another test's fixture: a change over there would
 * move the reference over here with nobody seeing it" — and it applies symmetrically. This class
 * re-declares the LEO-400 profile of {@code 02-baseline-L0.md} §3 rather than sharing it, and the
 * two are free to diverge: what is pinned here is that <em>this</em> trajectory's polyline does not
 * move, not that it is the same trajectory the L1 gate flies.
 *
 * <p><b>What it does not pin.</b> No evaluation count, no timing, no optimizer output — the rule of
 * {@code 02-baseline-L0.md} §6. The gravity turn's two variables are the fixed literals of the
 * baseline, injected the way {@code EarthOrbitNonRegressionTest} injects them; no CMA-ES runs.
 */
class MissionPolylineBaselineTest {

  private static final Logger logger = LogManager.getLogger(MissionPolylineBaselineTest.class);

  /** The LEO-400 optimum of {@code 02-baseline-L0.md} §3, at full precision. */
  private static final double TRANSITION_TIME = 307.193166;

  private static final double TURN_EXPONENT = 0.127161;

  /**
   * The trailing coast, passed as a resolved duration rather than left to the mission's horizon.
   *
   * <p>One sidereal day, which is the legacy default a mission built outside {@code
   * MissionComposer} carries. Passed explicitly because the horizon resolves itself from {@code
   * mission.getCurrentState()}, which this fixture has already moved to the gravity-turn entry
   * state: a gate must not depend on where the arming left the mission.
   */
  private static final double FINAL_COAST_SECONDS = 86_164.0;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  /**
   * One pinned vertex of the drawn line: where a phase run opens, plus the final one.
   *
   * <p>The run starts are the interesting vertices and not an arbitrary sample of them: they are
   * the ones decimation is forced to keep, so they are exactly where a change in the budget formula
   * would show up first. {@code index} is pinned alongside the position because a stride that
   * shifted while keeping the same forced vertices would move it and nothing else.
   */
  record Vertex(
      int index, String stage, boolean propulsive, double t, double x, double y, double z) {}

  // ════════════════════════════════════════════════════════════════════════
  // The pinned polyline — re-measured at PHY-8 / L3, the core throttled to 0.81
  // ════════════════════════════════════════════════════════════════════════

  /**
   * <b>The fixture decimates, and that was not a given.</b> 9 993 raw samples against a budget of 8
   * 192: the drawn line is strided down to 5 000 vertices. So this gate does not merely check that
   * an under-budget trail is copied through — it exercises the stride path that spec §4.1 names as
   * the one numerical risk of the lot, on a real trajectory rather than a synthetic one.
   *
   * <p>9 992 until {@code PHY-8 / L3}: throttling the core adds two ascent phases, hence one more
   * boundary sample.
   */
  private static final int RAW_POINTS = 9992;

  private static final int TRAIL_SIZE = 4999;

  /**
   * When {@code -Dorbitlab.recordBaseline=true}, {@link #leo400_polylineHasNotMoved} logs the
   * measured constants (via {@link #logMeasured}) and skips the assertions, so a legitimate
   * re-baseline is copied from the log rather than transcribed. Off by default: the gate asserts.
   */
  private static final boolean RECORD_BASELINE = Boolean.getBoolean("orbitlab.recordBaseline");

  private static final List<Vertex> LEO_400_VERTICES =
      List.of(
          new Vertex(
              0,
              "Vertical Ascent",
              true,
              0.0,
              -4244514.369709017,
              -4724005.098859251,
              588436.3918143848),
          new Vertex(
              5,
              "Gravity turn (S1)",
              true,
              7.0,
              -4242405.289840548,
              -4726509.002388168,
              588472.18229543),
          new Vertex(
              41,
              "Booster separation",
              false,
              76.90778157894736,
              -4164668.161345898,
              -4827349.195040115,
              590353.9300294485),
          new Vertex(
              42,
              "Gravity turn (core)",
              true,
              76.90878157894737,
              -4164665.6137515674,
              -4827352.183849979,
              590353.9702239139),
          new Vertex(
              50,
              "S1 separation",
              false,
              91.52269007894738,
              -4123430.053562891,
              -4874463.296481038,
              590918.7142749168),
          new Vertex(
              51,
              "Gravity turn (S2)",
              true,
              93.52269007894738,
              -4117176.949542237,
              -4881412.30259238,
              590991.1273167201),
          new Vertex(
              162,
              "Transfert",
              true,
              314.193166,
              -3062082.8082133904,
              -5655492.57496921,
              576217.1601048842),
          new Vertex(
              1506,
              "Trim",
              true,
              2999.837896963097,
              3151090.0526107037,
              5970753.55016088,
              -602485.5684339062),
          new Vertex(
              4279,
              "Coasting",
              false,
              8543.082874665895,
              3112932.5671697115,
              5991136.479803182,
              -599963.7405803913),
          new Vertex(
              4998,
              "Coasting",
              false,
              94707.0828746659,
              -2114779.2729038135,
              -6418769.634823774,
              523984.50998403283));

  @Test
  void leo400_polylineHasNotMoved() {
    MissionEphemeris ephemeris = flyLeo400();
    TrajectoryPolyline trail = ephemeris.displayTrail();

    logMeasured(ephemeris, trail);

    if (RECORD_BASELINE) {
      return;
    }

    assertTrue(ephemeris.isComplete(), "the LEO-400 baseline must fly to the end of every stage");
    assertEquals(RAW_POINTS, ephemeris.size(), "the raw sample count moved");
    assertEquals(TRAIL_SIZE, trail.size(), "the drawn vertex count moved");
    assertPinned(verticesOf(trail), LEO_400_VERTICES);

    // A LEO mission is one arc, and stays one until L4 produces a second. Pinned here rather than
    // merely asserted as a size, because an arc that split would also change the forced-vertex
    // union and therefore the stride — the two failures would arrive together, and this names one.
    assertEquals(1, trail.arcs().size(), "nothing produces a second arc yet");
    assertEquals(TrajectoryArc.earth(), trail.arcs().get(0).arc());
    assertEquals(0, trail.arcs().get(0).firstVertex());
    assertEquals(TRAIL_SIZE, trail.arcs().get(0).vertexCount(), "the arc must span the whole line");
    assertEquals(TrajectoryArc.earth(), ephemeris.firstPoint().arc());
    assertEquals(TrajectoryArc.earth(), ephemeris.lastPoint().arc());
  }

  private static void assertPinned(List<Vertex> actual, List<Vertex> expected) {
    assertEquals(expected.size(), actual.size(), "the trail gained or lost a phase run");
    for (int i = 0; i < expected.size(); i++) {
      int index = i;
      assertEquals(
          expected.get(i),
          actual.get(i),
          () -> "vertex " + index + " '" + expected.get(index).stage() + "' moved");
    }
  }

  /** Every run's opening vertex, in flight order, then the last vertex of the line. */
  private static List<Vertex> verticesOf(TrajectoryPolyline trail) {
    List<Vertex> vertices = new ArrayList<>();
    AbsoluteDate launch = trail.timeAt(0);
    for (PhaseRun run : trail.runs()) {
      vertices.add(vertexOf(trail, run.firstVertex(), run.stageName(), run.propulsive(), launch));
    }
    int last = trail.size() - 1;
    PhaseRun lastRun = trail.runs().get(trail.runOf(last));
    vertices.add(vertexOf(trail, last, lastRun.stageName(), lastRun.propulsive(), launch));
    return vertices;
  }

  private static Vertex vertexOf(
      TrajectoryPolyline trail, int index, String stage, boolean propulsive, AbsoluteDate launch) {
    return new Vertex(
        index,
        stage,
        propulsive,
        trail.timeAt(index).durationFrom(launch),
        trail.positionAt(index, SolarSystemBody.EARTH).getX(),
        trail.positionAt(index, SolarSystemBody.EARTH).getY(),
        trail.positionAt(index, SolarSystemBody.EARTH).getZ());
  }

  /**
   * Flies the LEO-400 baseline and samples it, exactly as {@code MissionOptimizer} does for the
   * ephemeris pass: arm the gravity turn with the fixed variables, then generate.
   */
  private static MissionEphemeris flyLeo400() {
    Mission mission = new EarthOrbitMission("Falcon Heavy", falconHeavyBaselineLoads(), 400_000.0);
    SpacecraftState initial = mission.getInitialState(epoch());
    armFirstBurn(mission, initial);
    return new MissionEphemerisGenerator().generate(mission, initial, FINAL_COAST_SECONDS);
  }

  /**
   * Flies the vertical ascent and hands the gravity turn its fixed variables — the mechanism {@code
   * EarthOrbitNonRegressionTest.flyAscent} uses. Only the gravity turn takes variables; the
   * analytic stages plan themselves.
   */
  private static void armFirstBurn(Mission mission, SpacecraftState initial) {
    mission.setCurrentState(initial);
    SpacecraftState entry = mission.getStages().getFirst().propagateStandalone(initial, mission);
    double[] variables = {TRANSITION_TIME, TURN_EXPONENT};
    firstBurnOf(mission).applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));
  }

  private static GravityTurnFirstBurnStage firstBurnOf(Mission mission) {
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof GravityTurnFirstBurnStage firstBurn) {
        return firstBurn;
      }
    }
    throw new AssertionError("no gravity-turn first burn in this mission");
  }

  /**
   * The Falcon Heavy of the LEO-400 baseline: 600 t and 100 t of propellant, <em>not</em> the tank
   * capacities. A fully loaded stack flown at these variables re-enters during the second
   * gravity-turn phase — see {@code CentralBodyBaselineTest.falconHeavyBaselineLoads}.
   */
  private static LaunchConfiguration falconHeavyBaselineLoads() {
    return new LaunchConfiguration(
        Launchers.FALCON_HEAVY, new double[] {400_000, 200_000, 100_000}, Spacecraft.LEGACY);
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  /**
   * Prints the pinnable form of what was just flown, so the constants above are copied, not typed.
   */
  private static void logMeasured(MissionEphemeris ephemeris, TrajectoryPolyline trail) {
    StringBuilder sb = new StringBuilder("\n=== MEASURED ===\n");
    sb.append(String.format(Locale.ROOT, "RAW_POINTS = %d;%n", ephemeris.size()));
    sb.append(String.format(Locale.ROOT, "TRAIL_SIZE = %d;%n", trail.size()));
    sb.append("LEO_400_VERTICES = List.of(\n");
    for (Vertex v : verticesOf(trail)) {
      sb.append(
          String.format(
              Locale.ROOT,
              "    new Vertex(%d, \"%s\", %b, %s, %s, %s, %s),%n",
              v.index(),
              v.stage(),
              v.propulsive(),
              v.t(),
              v.x(),
              v.y(),
              v.z()));
    }
    sb.append(");");
    logger.info(sb.toString());
  }
}
