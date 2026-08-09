package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.operation.LEOMission;
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

/**
 * The Ariane 62 catalog entry flies a LEO mission, and stages where the model says it does (spec
 * {@code specs/launchers/01-ariane-62.md} §5).
 *
 * <p><b>This started as a measurement probe with no accuracy assertion</b>, because whether the
 * entry could close a mission at all was genuinely unknown: aggregating the boosters and the
 * Vulcain into one stage makes it flame out around 128 s instead of the ~8 min the real core
 * burns. The spec recorded three admissible outcomes, including "it does not close", and forbade
 * inventing a tolerance before reading the numbers.
 *
 * <p><b>Measured 2026-08-09: it closes, and comfortably</b> — 400 km requested, flown band
 * 399.6–400.8 km, 21.7 % of the sized upper-stage load left over. The risk the spec quantified
 * turned out to rest on a wrong premise: it assumed a fully-loaded 31 t ULPM handing over at a
 * thrust-to-weight of 0.44, but {@code PropellantBudget} sizes that stage to 6.7 t, so the real
 * hand-over is near 1.04. The assertions below are therefore the ordinary house criterion, written
 * after the fact on observed numbers rather than guessed before them.
 */
public class Ariane62MissionTest extends AbstractTrajectoryOptimizerTest {

  private static final Logger logger = LogManager.getLogger(Ariane62MissionTest.class);

  /** Kourou, the latitude the mission actually flies from — and the one the budget is sized at. */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  private static final double TARGET_ALTITUDE = 400_000.0;

  /**
   * Bracket on the aggregated first stage's flame-out, in seconds from lift-off. Measured at
   * 128.2 s; the ±10 s band is wide enough to survive an optimizer re-tune and tight enough to
   * catch a change in the aggregate's mass, thrust or ISP. It exists because that instant is the
   * one documented modelling distortion of this entry — the real Ariane 62 core burns ~490 s — and
   * a claim carried only by a Javadoc is a claim nothing checks.
   */
  private static final double STAGING_INSTANT_S = 128.2;

  private static final double STAGING_TOLERANCE_S = 10.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void ariane62_leo400km_insertsAndStagesWhereTheModelSaysItDoes() {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(5_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(
            Launchers.ARIANE_62, payload, TARGET_ALTITUDE, LAUNCH_LATITUDE_DEG);
    logger.info("[A62] Budget loads: S1 {} kg, S2 {} kg", loads[0], loads[1]);

    LEOMission mission =
        new LEOMission(
            "Ariane 62 (budget loads)",
            new LaunchConfiguration(Launchers.ARIANE_62, loads, payload),
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
    MissionEphemerisPoint liftOff = ephemeris.allPoints().getFirst();
    MissionEphemerisPoint separation = firstPointOfStage(ephemeris, AscentSequence.SEPARATION_NAME);
    Assertions.assertNotNull(
        separation,
        "no '" + AscentSequence.SEPARATION_NAME + "' samples: the ascent never reached staging");

    double stagingInstant = separation.time().durationFrom(liftOff.time());
    logger.info(
        "[A62] S1 flame-out at T+{} s (real Ariane 62 core burn ~490 s)", stagingInstant);
    Assertions.assertEquals(
        STAGING_INSTANT_S,
        stagingInstant,
        STAGING_TOLERANCE_S,
        "the aggregated first stage no longer flames out where the catalog figures put it");
  }

  /** Per-stage propellant left aboard: what the aggregation leaves on the table, in kilograms. */
  private static void logResiduals(MissionComputeResult result) {
    for (int stage = 0; stage < 2; stage++) {
      StagePropellant residual = result.performanceReport().residualForStage(stage).orElse(null);
      if (residual == null) {
        logger.info("[A62] Stage {}: no propellant split reported", stage);
        continue;
      }
      logger.info(
          "[A62] Stage {}: {} kg left of {} kg loaded ({}%)",
          stage, residual.residual(), residual.loaded(), 100.0 * residual.residualRatio());
    }
  }

  /** The first sample tagged with the given stage, or {@code null} if the mission never got there. */
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
