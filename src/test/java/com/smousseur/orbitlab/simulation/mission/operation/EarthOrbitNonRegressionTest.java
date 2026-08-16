package com.smousseur.orbitlab.simulation.mission.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.Physics;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.GravityTurnFirstBurnStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
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

/**
 * <b>MIS-7 / P1, test T2 — the non-regression gate</b> (spec {@code
 * docs/earth-orbit/01-mission-terre-parametrable.md} §9.2). It must stay green at every step of P1.
 *
 * <p><b>What it guards.</b> MIS-7 renames {@code LEOMission} to {@link EarthOrbitMission}, threads a
 * {@link LaunchPlane} through the ascent and adds a commanded-plane attitude that can steer the
 * orbital plane. The Falcon Heavy and Ariane 62 calibrations rest on measured due-east
 * trajectories, so a due-east target must keep flying exactly what it flew before — the commanded
 * attitude being opt-in is what makes that possible (spec §4.2), and this fixture is what keeps the
 * door shut.
 *
 * <p><b>The risk it exists for</b> is spec §10's "calibration trap": the temptation, while
 * refactoring, to unify two paths that merely look alike. The two horizontal targets are <em>not</em>
 * numerically identical even when the planes coincide, so "the planes agree, use the new code" would
 * silently move every calibrated trajectory.
 *
 * <p>Everything here runs at <b>fixed variables</b>: no CMA-ES, no seed, no optimizer.
 */
class EarthOrbitNonRegressionTest {
  private static final Logger logger = LogManager.getLogger(EarthOrbitNonRegressionTest.class);

  private static final double LAT = 5.23;
  private static final double LON = -52.77;
  private static final double ALT = 0.0;

  /** Fixed second-burn length and pitch exponent, as the deleted T0 fixtures used. */
  private static final double BURN2_SECONDS = 250.0;

  private static final double TURN_EXPONENT = 0.32;

  /** The four circular targets of §9.2, in meters. */
  private static final double[] CIRCULAR_TARGETS = {200_000.0, 400_000.0, 700_000.0, 1_000_000.0};

  /**
   * The three elliptic targets of §9.2, as (perigee, apogee) pairs in meters.
   *
   * <p>All three stay under the direct chain's apogee ceiling, deliberately. A fourth shape tried
   * here — 300 km × 35 786 km on a Falcon Heavy — is not a non-regression case but an infeasible
   * one: its trim has to coast 5 h 15 to apogee against the 2 h its upper stage declares. The §6.1
   * rule refuses it, and {@code EarthOrbitValidationTest} is where that refusal is asserted.
   */
  private static final double[][] ELLIPTIC_TARGETS = {
    {200_000.0, 400_000.0}, {400_000.0, 1_000_000.0}, {300_000.0, 1_500_000.0}
  };

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  // ════════════════════════════════════════════════════════════════════════
  // The attitude mode — the seam the whole non-regression argument rests on
  // ════════════════════════════════════════════════════════════════════════

  /**
   * A due-east target must never install the commanded-plane attitude, on any of the seven shapes
   * and in either optimization mode. This is the single assertion the rest of the calibration
   * depends on: as long as it holds, the ascent runs the pre-MIS-7 code path.
   */
  @Test
  void everyDueEastTarget_fliesTheHistoricalAttitude() {
    for (double target : CIRCULAR_TARGETS) {
      for (OptimizationType mode : OptimizationType.values()) {
        assertNotCommanded(compose(circularSpec(target), mode), "circular " + target + " / " + mode);
      }
    }
    for (double[] target : ELLIPTIC_TARGETS) {
      for (OptimizationType mode : OptimizationType.values()) {
        assertNotCommanded(
            compose(ellipticSpec(target[0], target[1]), mode),
            "elliptic " + target[0] + "/" + target[1] + " / " + mode);
      }
    }
  }

  /**
   * The azimuth a due-east spec derives must be the historical constant to the last bit — not
   * merely to within a tolerance. The pitch kick is applied at the very first instant of the turn
   * and everything downstream is chaotic in it, so an ulp here is not an ulp at MECO.
   */
  @Test
  void theDerivedAzimuth_isTheHistoricalConstantBitForBit() {
    for (double latitude : new double[] {0.0, LAT, 28.5, -34.6}) {
      assertEquals(
          Physics.getLaunchAzimuth(),
          LaunchPlane.dueEast(latitude).launchAzimuth(FastMath.toRadians(latitude)),
          0.0,
          () -> "the free plane of latitude " + latitude + "° must give the historical azimuth");
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // The stage chains
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The mission a due-east spec composes must have the same phases, in the same order, as the one
   * the direct constructor builds — the path every pre-MIS-7 caller used and every calibration test
   * still uses.
   */
  @Test
  void theComposedChain_matchesTheDirectConstructor() {
    for (double target : CIRCULAR_TARGETS) {
      assertEquals(
          stageNames(
              new EarthOrbitMission("direct", falconHeavy(), target, target, LAT, LON, ALT)),
          stageNames(compose(circularSpec(target), OptimizationType.FAST)),
          () -> "analytic chain differs at " + target + " m");
      assertEquals(
          stageNames(
              EarthOrbitMission.circularWithOptimizedTransfer(
                  "direct", falconHeavy(), target, LAT, LON, ALT)),
          stageNames(compose(circularSpec(target), OptimizationType.BALANCED)),
          () -> "optimized circular chain differs at " + target + " m");
    }
    for (double[] target : ELLIPTIC_TARGETS) {
      assertEquals(
          stageNames(
              EarthOrbitMission.ellipticWithOptimizedTransfer(
                  "direct", falconHeavy(), target[0], target[1], LAT, LON, ALT)),
          stageNames(compose(ellipticSpec(target[0], target[1]), OptimizationType.BALANCED)),
          () -> "optimized elliptic chain differs at " + target[0] + "/" + target[1] + " m");
    }
  }

  // ════════════════════════════════════════════════════════════════════════
  // The trajectory itself
  // ════════════════════════════════════════════════════════════════════════

  /**
   * The headline check: fly the whole ascent to MECO, at fixed variables, through the spec path and
   * through the direct constructor, and require the two states to be <b>identical</b> — not close.
   * Both are deterministic and take the same code path, so any difference at all is the composer or
   * the plane plumbing having introduced something.
   *
   * <p>The achieved inclination is asserted separately against the site latitude: that is the
   * <em>physical</em> statement of "nothing moved", and it is what would break first if a due-east
   * launch ever slipped onto the commanded-plane attitude.
   */
  @Test
  void theFlownAscent_isIdenticalThroughBothPaths() {
    SpacecraftState viaSpec =
        flyAscent(compose(circularSpec(400_000.0), OptimizationType.FAST));
    SpacecraftState viaConstructor =
        flyAscent(new EarthOrbitMission("direct", falconHeavy(), 400_000.0));

    double deltaPosition = Vector3D.distance(viaSpec.getPosition(), viaConstructor.getPosition());
    double deltaVelocity =
        Vector3D.distance(
            viaSpec.getPVCoordinates().getVelocity(),
            viaConstructor.getPVCoordinates().getVelocity());

    logger.info(
        "T2 ascent through the spec path vs the direct constructor: Δpos {} m, Δvel {} m/s",
        fmt(deltaPosition, 6),
        fmt(deltaVelocity, 6));

    assertEquals(0.0, deltaPosition, 0.0, "the composed ascent must fly the very same trajectory");
    assertEquals(0.0, deltaVelocity, 0.0, "the composed ascent must fly the very same trajectory");
    assertEquals(
        LAT,
        Physics.inclinationDeg(viaSpec),
        0.1,
        "a due-east launch still reaches the site latitude and nothing else");
  }

  // ════════════════════════════════════════════════════════════════════════
  // Fixtures
  // ════════════════════════════════════════════════════════════════════════

  private static LaunchConfiguration falconHeavy() {
    return LaunchConfiguration.fullyLoaded(Launchers.FALCON_HEAVY, Spacecraft.LEGACY);
  }

  private static MissionSpec.EarthOrbit circularSpec(double target) {
    return MissionSpec.EarthOrbit.dueEast(
        "T2", falconHeavy(), target, target, "Kourou", LAT, LON, ALT, null);
  }

  private static MissionSpec.EarthOrbit ellipticSpec(double perigee, double apogee) {
    return MissionSpec.EarthOrbit.dueEast(
        "T2", falconHeavy(), perigee, apogee, "Kourou", LAT, LON, ALT, null);
  }

  private static Mission compose(MissionSpec spec, OptimizationType mode) {
    return MissionComposer.compose(spec, mode);
  }

  private static List<String> stageNames(Mission mission) {
    return mission.getStages().stream().map(MissionStage::getName).toList();
  }

  /**
   * Asserts that a mission's ascent plans no commanded plane. Reads the plan the first burn phase
   * would publish, rather than the trajectory, so the check is exact and instantaneous.
   */
  private static void assertNotCommanded(Mission mission, String label) {
    // Fails fast if the composition ever stops going through the explicit three-phase ascent.
    firstBurnOf(mission);
    SpacecraftState entry = mission.getInitialState(epoch());

    assertFalse(
        maneuverOf(mission, entry).plan(entry, new double[] {300.0, TURN_EXPONENT})
            .hasCommandedPlane(),
        () -> label + ": a due-east target must not steer its plane");
  }

  /** Rebuilds the maneuver the first burn phase would build, from the same inputs it reads. */
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
        plane.commands(latitude));
  }

  private static GravityTurnFirstBurnStage firstBurnOf(Mission mission) {
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof GravityTurnFirstBurnStage firstBurn) {
        return firstBurn;
      }
    }
    throw new AssertionError("no gravity-turn first burn in " + stageNames(mission));
  }

  /**
   * Flies the vertical ascent then the three gravity-turn phases at fixed variables, exactly as
   * {@code StageChainRunner} drives them on the replay path.
   *
   * @param mission the mission to fly
   * @return the state at MECO
   */
  private static SpacecraftState flyAscent(Mission mission) {
    mission.setCurrentState(mission.getInitialState(epoch()));
    List<MissionStage> stages = mission.getStages();
    SpacecraftState entry =
        stages.getFirst().propagateStandalone(mission.getCurrentState(), mission);

    GravityTurnFirstBurnStage firstBurn = firstBurnOf(mission);
    GravityTurnManeuver reference = maneuverOf(mission, entry);
    double[] variables = {reference.getStagingCompleteTime() + BURN2_SECONDS, TURN_EXPONENT};
    firstBurn.applyOptimization(new OptimizationResult(variables, 0.0, entry, 1, entry));

    List<MissionStage> ascent = stages.subList(1, 4);
    mission.setCurrentState(entry);
    SpacecraftState meco = StageChainRunner.plain().run(ascent, entry, mission);
    assertTrue(meco.getMass() < entry.getMass() - 1.0, "the ascent must actually fly");
    return meco;
  }

  private static AbsoluteDate epoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static String fmt(double value, int decimals) {
    return String.format(Locale.ROOT, "%." + decimals + "f", value);
  }
}
