package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.FlightContext;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentPlan;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropulsionSystem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.*;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

/**
 * <b>PHY-8 / L1 and L2 — the iso-trajectory gate</b> (spec {@code
 * docs/etagement/03-conception-L1.md} §6.2 and {@code 04-conception-L2.md} §5).
 *
 * <p>Splitting the Falcon Heavy into {@code [boosters ×2, core, S2]} and flying it at full thrust
 * must move nothing — <b>to the bit</b>, not to a tolerance. {@code L1} proved it with the split as
 * a fixture against the catalog; {@code L2} moved the split <em>into</em> the catalog, so the
 * reference swapped sides: {@link #AGGREGATED} is now the frozen pre-L2 entry and the subject is
 * {@code Launchers.FALCON_HEAVY}. Neither side is a pinned literal, so the comparison survives
 * {@code L3} and {@code L4} untouched.
 *
 * <p><b>Why bit equality is reachable.</b> Every figure of the split is an exact integer and the
 * boosters are twice the core, so the aggregate thrust is 22 800 000 N exactly, {@code ΣF/Σ(F/Isp)}
 * lands on 296 s exactly, and the block's dry mass, depletion floor, burn duration and jettison
 * mass all reproduce the former S1's (spec L1 §2.4).
 *
 * <p>The profile flown is the one the two zero-tolerance gates use — hand-written loads {@code {600
 * 000, 100 000}} on {@code Spacecraft.LEGACY} — which split at the exact 2/3–1/3 pro rata.
 *
 * <p><b>One figure does move, and it is inert.</b> The former entry rounded its aggregate section
 * to 31.6 m²; three exemplars of 10.5 m² give 31.5, and the exact value is 31.56. Nothing reads it
 * — no production mission declares an atmosphere — so the trajectory is unaffected (spec L2 §3.5).
 */
class ParallelBlockAscentTest {
  private static final Logger logger = LogManager.getLogger(ParallelBlockAscentTest.class);

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double ALT = 0.0;
  private static final double BURN2_SECONDS = 250.0;
  private static final double TURN_EXPONENT = 0.32;
  private static final double G0 = Constants.G0_STANDARD_GRAVITY;

  /** The hand-written loads of the two zero-tolerance gates, and their 2/3 – 1/3 split. */
  private static final double[] SEQUENTIAL_LOADS = {600_000, 100_000};

  private static final double[] SPLIT_LOADS = {400_000, 200_000, 100_000};

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // ── The block, before any flight ──────────────────────────────────────────

  @Test
  void theCatalogBlockReproducesTheFormerFirstStageBitForBit() {
    var aggregated = AGGREGATED.instantiate(SEQUENTIAL_LOADS, Spacecraft.LEGACY);
    var split = Launchers.FALCON_HEAVY.instantiate(SPLIT_LOADS, Spacecraft.LEGACY);

    var reference = aggregated.resolveActiveStage(aggregated.getMass());
    var block = split.resolveActiveStage(split.getMass());

    assertEquals(reference.propulsion().thrust(), block.propulsion().thrust(), 0.0);
    assertEquals(reference.propulsion().isp(), block.propulsion().isp(), 0.0);
    assertEquals(reference.dryMass(), block.dryMass(), 0.0);
    assertEquals(reference.depletionFloor(), block.depletionFloor(), 0.0);
    assertEquals(reference.massAfterJettison(), block.massAfterJettison(), 0.0);
    assertEquals(aggregated.getMass(), split.getMass(), 0.0);
  }

  @Test
  void atFullThrustTheCoreEmptiesWithTheBoosters_soOneJettisonDropsBoth() {
    var split = Launchers.FALCON_HEAVY.instantiate(SPLIT_LOADS, Spacecraft.LEGACY);

    assertTrue(split.stagingPlan().hasParallelBlock());
    assertTrue(split.stagingPlan().parallelBlock().groupedJettison());
    assertEquals(0.0, split.stagingPlan().parallelBlock().coreLeftAtBoosterBurnout(), 0.0);
  }

  @Test
  void aGroupedBlockKeepsTheThreeHistoricalAscentPhases() {
    assertEquals(
        stageNames(missionOf(AGGREGATED, SEQUENTIAL_LOADS)),
        stageNames(missionOf(Launchers.FALCON_HEAVY, SPLIT_LOADS)));
  }

  // ── The flight ────────────────────────────────────────────────────────────

  /**
   * The headline check: fly the vertical ascent then the gravity-turn phases at fixed variables,
   * through the catalog launcher and through the split one, and require the two states to be
   * <b>identical</b>. Same shape as {@code EarthOrbitNonRegressionTest}: two compositions of the
   * same mission, one run, tolerance zero.
   */
  @Test
  void theSplitFalconHeavyFliesTheVerySameAscent() {
    SpacecraftState sequential = flyAscent(missionOf(AGGREGATED, SEQUENTIAL_LOADS));
    SpacecraftState split = flyAscent(missionOf(Launchers.FALCON_HEAVY, SPLIT_LOADS));

    double deltaPosition = Vector3D.distance(sequential.getPosition(), split.getPosition());
    double deltaVelocity =
        Vector3D.distance(
            sequential.getPVCoordinates().getVelocity(), split.getPVCoordinates().getVelocity());
    logger.info(
        "Catalog (split) vs aggregated Falcon Heavy at MECO: Δpos {} m, Δvel {} m/s, Δmass {} kg",
        String.format(Locale.ROOT, "%.3e", deltaPosition),
        String.format(Locale.ROOT, "%.3e", deltaVelocity),
        String.format(Locale.ROOT, "%.3e", FastMath.abs(sequential.getMass() - split.getMass())));

    assertEquals(0.0, deltaPosition, 0.0, "splitting the block must move nothing");
    assertEquals(0.0, deltaVelocity, 0.0, "splitting the block must move nothing");
    assertEquals(sequential.getMass(), split.getMass(), 0.0);
    assertEquals(sequential.getDate(), split.getDate());
  }

  // ── The five-phase path, which is not iso-trajectory and is not meant to be ──

  @Test
  void aThrottledCoreOutlastsTheBoostersAndGetsItsOwnPhase() {
    var split = throttledFalconHeavy(0.81).instantiate(fullLoads(), Spacecraft.LEGACY);

    // 822 t of boosters drain 0.81 × 7.6/15.2 = 0.405 of that from the core, leaving 78 t of the
    // 411 t it carries — the figure the découpage derives (spec 01-decoupage.md §2.3).
    assertEquals(78_090, split.stagingPlan().parallelBlock().coreLeftAtBoosterBurnout(), 1.0);
    assertFalse(split.stagingPlan().parallelBlock().groupedJettison());
  }

  @Test
  void theThrottledAscentIsFivePhases() {
    List<String> names = stageNames(missionOf(throttledFalconHeavy(0.81), fullLoads()));

    assertTrue(names.contains(AscentSequence.BOOSTER_SEPARATION_NAME), names.toString());
    assertTrue(names.contains(AscentSequence.CORE_BURN_NAME), names.toString());
    assertEquals(
        List.of(
            AscentSequence.FIRST_BURN_NAME,
            AscentSequence.BOOSTER_SEPARATION_NAME,
            AscentSequence.CORE_BURN_NAME,
            AscentSequence.SEPARATION_NAME,
            AscentSequence.SECOND_BURN_NAME),
        names.subList(1, 6));
  }

  @Test
  void theBurnDurationsReproduceTheDecoupageFigures() {
    Mission mission = missionOf(throttledFalconHeavy(0.81), fullLoads());
    SpacecraftState entry = mission.getInitialState(epoch());
    AscentPlan plan = maneuverOf(mission, entry).plan(entry, new double[] {600.0, 0.32});

    // Boosters run dry at 822 000 / 5 236.4 kg/s; the core then burns its 78 t at full thrust.
    double boosterFlow = 15_200_000 / (296 * G0);
    double coreFlow = 7_600_000 / (296 * G0);

    assertEquals(822_000 / boosterFlow, plan.burn1Duration(), 1e-6);
    assertEquals(78_090 / coreFlow, plan.coreBurnDuration(), 0.05);
    logger.info(
        "Throttled Falcon Heavy: block burn {} s, core-only {} s, core total {} s",
        String.format(Locale.ROOT, "%.1f", plan.burn1Duration()),
        String.format(Locale.ROOT, "%.1f", plan.coreBurnDuration()),
        String.format(Locale.ROOT, "%.1f", plan.burn1Duration() + plan.coreBurnDuration()));
    assertEquals(
        187.0,
        plan.burn1Duration() + plan.coreBurnDuration(),
        0.5,
        "the core flies the ~187 s the decoupage derives from f ≈ 0.81");
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  /**
   * The Falcon Heavy as {@code L2} will declare it: three identical kerolox cores, two of them
   * strapped on. Per-exemplar figures are exactly a third of the catalog aggregate.
   */
  /**
   * The catalog Falcon Heavy with its centre core throttled, which is what {@code L3} will declare.
   * Built from the catalog stages rather than from figures of its own, so it tracks whatever the
   * catalog says.
   */
  private static LauncherModel throttledFalconHeavy(double coreThrottle) {
    AscentProfile catalog = Launchers.FALCON_HEAVY.ascentProfile();
    return new LauncherModel(
        "FALCON_HEAVY_THROTTLED",
        "Falcon Heavy (throttled core)",
        Launchers.FALCON_HEAVY.stages(),
        new AscentProfile(
            catalog.verticalAscentDuration(),
            catalog.pitchKickAngleDeg(),
            catalog.interstageCoastDuration(),
            coreThrottle));
  }

  /**
   * The Falcon Heavy as the catalog declared it before {@code L2}: one entry aggregating the three
   * identical kerolox cores. Frozen here because it is the reference the split must reproduce, and
   * because nothing else in the repository states it any more.
   */
  private static final LauncherModel AGGREGATED =
      new LauncherModel(
          "FALCON_HEAVY_AGGREGATED",
          "Falcon Heavy (aggregated first stage)",
          List.of(
              new StageModel(
                  "S1 (3 cores aggregated)",
                  66_000,
                  1_233_000,
                  new PropulsionSystem(296, 22_800_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND,
                      0,
                      ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC,
                      0.0,
                      StageRole.CORE),
                  new AerodynamicProperties(31.6, 0.4)),
              Launchers.FALCON_HEAVY.stages().getLast()),
          Launchers.FALCON_HEAVY.ascentProfile());

  private static double[] fullLoads() {
    return new double[] {822_000, 411_000, 107_500};
  }

  private static Mission missionOf(LauncherModel launcher, double[] loads) {
    return new EarthOrbitMission(
        "L1 gate",
        new LaunchConfiguration(launcher, loads, Spacecraft.LEGACY),
        400_000.0,
        400_000.0,
        LAT,
        LON,
        ALT);
  }

  private static List<String> stageNames(Mission mission) {
    return mission.getStages().stream().map(MissionStage::getName).toList();
  }

  private static GravityTurnManeuver maneuverOf(Mission mission, SpacecraftState entry) {
    AscentProfile profile = Launchers.FALCON_HEAVY.ascentProfile();
    LaunchPlane plane = LaunchPlane.dueEast(LAT);
    double latitude = FastMath.toRadians(LAT);
    return new GravityTurnManeuver(
        mission.getVehicle(),
        entry.getMass(),
        FastMath.toRadians(profile.pitchKickAngleDeg()),
        plane.launchAzimuth(latitude),
        profile.interstageCoastDuration(),
        plane.commands(latitude),
        FlightContext.earth());
  }

  private static GravityTurnFirstBurnStage firstBurnOf(Mission mission) {
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof GravityTurnFirstBurnStage firstBurn) {
        return firstBurn;
      }
    }
    throw new AssertionError("no gravity-turn first burn in " + stageNames(mission));
  }

  /** Flies the vertical ascent then every gravity-turn phase, at fixed variables. */
  private static SpacecraftState flyAscent(Mission mission) {
    mission.setCurrentState(mission.getInitialState(epoch()));
    List<MissionStage> stages = mission.getStages();
    SpacecraftState entry =
        stages.getFirst().propagateStandalone(mission.getCurrentState(), mission);

    GravityTurnFirstBurnStage firstBurn = firstBurnOf(mission);
    GravityTurnManeuver reference = maneuverOf(mission, entry);
    double[] variables = {reference.getStagingCompleteTime() + BURN2_SECONDS, TURN_EXPONENT};
    firstBurn.applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));

    int lastAscentPhase = stages.indexOf(firstBurn) + ascentPhaseCount(stages);
    List<MissionStage> ascent = stages.subList(1, lastAscentPhase);
    mission.setCurrentState(entry);
    SpacecraftState meco = StageChainRunner.plain().run(ascent, entry, mission);
    assertTrue(meco.getMass() < entry.getMass() - 1.0, "the ascent must actually fly");
    return meco;
  }

  private static int ascentPhaseCount(List<MissionStage> stages) {
    return stages.stream()
            .map(MissionStage::getName)
            .anyMatch(AscentSequence.CORE_BURN_NAME::equals)
        ? 5
        : 3;
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }
}
