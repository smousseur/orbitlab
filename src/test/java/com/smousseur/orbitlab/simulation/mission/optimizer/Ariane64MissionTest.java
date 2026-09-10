package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.stage.ascent.AscentSequence;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The Ariane 64 catalog entry flies a LEO mission, and stages where the model says it does (spec
 * {@code docs/launchers/01-ariane-64.md} §5).
 *
 * <p><b>This started as a measurement probe with no accuracy assertion</b>, because whether the
 * entry could close a mission at all was genuinely unknown: aggregating the boosters and the
 * Vulcain into one stage makes it flame out around 128 s instead of the ~8 min the real core burns.
 * The spec recorded three admissible outcomes, including "it does not close", and forbade inventing
 * a tolerance before reading the numbers.
 *
 * <p><b>Measured 2026-08-09: it closes, and comfortably</b> — 400 km requested, flown band
 * 399.6–400.8 km, 21.7 % of the sized upper-stage load left over. The risk the spec quantified
 * turned out to rest on a wrong premise: it assumed a fully-loaded 31 t ULPM handing over at a
 * thrust-to-weight of 0.44, but {@code PropellantBudget} sizes that stage to 6.7 t, so the real
 * hand-over is near 1.04. The assertions below are therefore the ordinary house criterion, written
 * after the fact on observed numbers rather than guessed before them.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class Ariane64MissionTest extends AbstractTrajectoryOptimizerTest {

  private static final Logger logger = LogManager.getLogger(Ariane64MissionTest.class);

  /** Kourou, the latitude the mission actually flies from — and the one the budget is sized at. */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  private static final double TARGET_ALTITUDE = 400_000.0;

  /**
   * Bracket on the aggregated first stage's flame-out, in seconds from lift-off. Measured at 128.2
   * s; the ±10 s band is wide enough to survive an optimizer re-tune and tight enough to catch a
   * change in the aggregate's mass, thrust or ISP. It exists because that instant is the one
   * documented modelling distortion of this entry — the real Ariane 64 core burns ~490 s — and a
   * claim carried only by a Javadoc is a claim nothing checks.
   */
  /** The boosters run dry: 564 t at the flow the catalog's thrust and ISP give. */
  private static final double BOOSTER_SEPARATION_S = 130.0;

  /** And the core flies on alone until its own tanks are empty, which is the point of L4. */
  private static final double CORE_SEPARATION_S = 480.0;

  private static final double STAGING_TOLERANCE_S = 10.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void ariane64_leo400km_insertsAndStagesWhereTheModelSaysItDoes() {
    // 20 t, measured rather than reasoned. At 5 t this launcher is so over-powered that the
    // optimizer stops igniting the upper stage at all (cost 300x acceptable); at 40 t — the ideal-
    // ΔV capacity §2.4 computes — it cannot make orbit and hands over on the re-entry floor. The
    // ideal figure ignores every loss the flight actually pays, so the flown capacity sits well
    // below it, and 20 t is where this profile behaves (spec docs/etagement/06-conception-L4.md
    // §6).
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(20_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(
            Launchers.ARIANE_64, payload, TARGET_ALTITUDE, LAUNCH_LATITUDE_DEG);
    logger.info(
        "[A64] Budget loads: boosters {} kg, core {} kg, ULPM {} kg", loads[0], loads[1], loads[2]);

    EarthOrbitMission mission =
        new EarthOrbitMission(
            "Ariane 64 (budget loads)",
            new LaunchConfiguration(Launchers.ARIANE_64, loads, payload),
            TARGET_ALTITUDE);

    // The flown-band criterion of every other LEO mission test, applied unchanged: a second
    // launcher earns no bespoke tolerance.
    MissionComputeResult result = testMission(mission, TARGET_ALTITUDE, TARGET_ALTITUDE);

    assertStagingInstant(result.ephemeris());
    logResiduals(result);
  }

  /**
   * The aggregated first stage hands over when its propellant runs out, and that instant is a
   * property of the catalog figures alone. The separation phase opens on flame-out, so its first
   * sample dates it.
   */
  private static void assertStagingInstant(MissionEphemeris ephemeris) {
    double boosters = instantOf(ephemeris, AscentSequence.BOOSTER_SEPARATION_NAME);
    double core = instantOf(ephemeris, AscentSequence.SEPARATION_NAME);

    logger.info(
        "[A64] boosters dry at T+{} s, core at T+{} s — the real vehicle drops its P120C around"
            + " 130 s and burns its Vulcain for ~8 min",
        boosters,
        core);
    Assertions.assertEquals(
        BOOSTER_SEPARATION_S,
        boosters,
        STAGING_TOLERANCE_S,
        "the boosters no longer run dry where the catalog figures put them");
    // The control the aggregate could never satisfy. The Ariane 62 entry this one replaces flamed
    // its whole first stage out at 128 s and said so in its own javadoc: "wrong for the core, which
    // really burns ~8 min, so the ascent shape is not this vehicle's". It is now.
    Assertions.assertEquals(
        CORE_SEPARATION_S,
        core,
        STAGING_TOLERANCE_S,
        "the core no longer flies the ~8 minutes that splitting the block bought");
  }

  private static double instantOf(MissionEphemeris ephemeris, String stageName) {
    MissionEphemerisPoint liftOff = ephemeris.allPoints().getFirst();
    MissionEphemerisPoint point = firstPointOfStage(ephemeris, stageName);
    Assertions.assertNotNull(point, "no '" + stageName + "' samples: the ascent never got there");
    return point.time().durationFrom(liftOff.time());
  }

  /** Per-stage propellant left aboard: what the aggregation leaves on the table, in kilograms. */
  private static void logResiduals(MissionComputeResult result) {
    for (int stage = 0; stage < 3; stage++) {
      StagePropellant residual = result.performanceReport().residualForStage(stage).orElse(null);
      if (residual == null) {
        logger.info("[A64] Stage {}: no propellant split reported", stage);
        continue;
      }
      logger.info(
          "[A64] Stage {}: {} kg left of {} kg loaded ({}%)",
          stage, residual.residual(), residual.loaded(), 100.0 * residual.residualRatio());
    }
  }

  /**
   * The first sample tagged with the given stage, or {@code null} if the mission never got there.
   */
  private static MissionEphemerisPoint firstPointOfStage(
      MissionEphemeris ephemeris, String stageName) {
    for (MissionEphemerisPoint pt : ephemeris.allPoints()) {
      if (stageName.equals(pt.stageName())) {
        return pt;
      }
    }
    return null;
  }
}
