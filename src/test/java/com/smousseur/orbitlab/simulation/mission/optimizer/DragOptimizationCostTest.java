package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.PropellantBudget;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * <b>PHY-1 / L2 — what an optimisation costs with the drag on</b> (spec {@code
 * docs/atmosphere/05-conception-L2.md} §4.2). A recorded figure, not a property: nothing here is
 * asserted, and nothing in the lot's closure depends on it.
 *
 * <p><b>Disabled, and meant to be launched by hand.</b> It is the one place in the lot that flies
 * an ascent from the pad with an atmosphere, which is exactly what §1.1 keeps out of every other
 * fixture: L0 §2.2 measured 982 497 integration steps and 487 s for a single day of coast at 130
 * km, so one CMA-ES candidate dipping low is enough to turn an evaluation from 0.2 s into eight
 * minutes. The altitude bound that would make this safe belongs to PHY-2.
 *
 * <p><b>Every outcome is the measurement</b>, which is why this fixture exists at all rather than
 * being deferred whole: it converges and the ratio of wall times is the overhead the découpage asks
 * for; it hangs and the timeout below prices the missing altitude bound on the real optimiser; or
 * it fails, which is what actually happened.
 *
 * <p><b>Measured 2026-08-21 — the drag-on run does not hang, it does not converge either: it
 * breaks, in 3 min 15 s.</b> In order:
 *
 * <ul>
 *   <li>the S1 gravity turn settles at cost <b>0.582</b> against an acceptable 0.0476, three
 *       independent CMA-ES explorations agreeing on it, and ends 58 km up with a 3.65° flight path
 *       angle where the drag-free profile ends flat;
 *   <li>{@code Gravity turn (S2)} then hits its {@code DepletionGuard} before its scheduled cutoff
 *       — the propellant loads were sized by {@code PropellantBudget} for an ascent with no
 *       atmosphere, so the stack no longer reaches orbit;
 *   <li>{@code AnalyticHohmannTransferStage} finally throws {@code minimal step size reached} on a
 *       trajectory that never got to orbit.
 * </ul>
 *
 * <p><b>The finding for PHY-2 is therefore not a percentage but a sequence</b>: turning the drag on
 * without re-sizing the propellant budget does not make the optimisation slow, it makes it
 * infeasible. And it exposes one approximation of L1 outside its stated domain — the catalogue
 * gives S2 the free-molecular Cd 2.2 "because an upper stage ignites above 70 km", while this
 * profile ignites it at 58 km, in continuum flow.
 *
 * <p>Nothing here is asserted: an outcome is recorded, not required.
 */
@Disabled("PHY-1 / L2 §4.2 — a recorded measurement, launched by hand; see the class javadoc")
class DragOptimizationCostTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(DragOptimizationCostTest.class);

  /** Kourou, the latitude the profile is sized and flown at. */
  private static final double LAUNCH_LATITUDE_DEG = 5.23;

  private static final double TARGET_ALTITUDE = 400_000.0;

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.MINUTES)
  @DisplayName("LEO 400 km optimised with and without an atmosphere, wall times recorded")
  void dragOnOptimization_costIsRecorded() {
    Attempt dragOff = optimise(AtmosphereModel.NONE);
    Attempt dragOn = optimise(AtmosphereModel.NRLMSISE);

    logger.info("L2 optimisation cost — Falcon Heavy, LEO 400 km, budget loads:");
    logger.info("  drag off              = {} ms, {}", dragOff.millis(), dragOff.outcome());
    logger.info("  drag on (NRLMSISE-00) = {} ms, {}", dragOn.millis(), dragOn.outcome());
    if (dragOff.converged() && dragOn.converged()) {
      logger.info(
          "  overhead = {} % (impact study announces +5 % to +50 %)",
          String.format(
              Locale.ROOT, "%+.1f", 100.0 * ((double) dragOn.millis() / dragOff.millis() - 1.0)));
    } else {
      logger.info(
          "  no overhead to report — what PHY-2 gets from this run is the failure itself, not a"
              + " percentage; see the class javadoc for how to read it");
    }
  }

  /** What one optimisation attempt produced: how long it ran, and how it ended. */
  private record Attempt(long millis, String outcome, boolean converged) {}

  /**
   * One full CMA-ES optimisation of the profile.
   *
   * <p><b>A failure is caught rather than thrown.</b> The drag-on run breaks somewhere in the stage
   * chain, and letting that propagate would lose the drag-off timing measured just before it — this
   * fixture records outcomes, and a crash is one of them.
   */
  private static Attempt optimise(AtmosphereModel atmosphere) {
    Spacecraft payload = Payloads.EARTH_OBSERVATION_SAT.toSpacecraft(10_000, 0.0);
    double[] loads =
        PropellantBudget.loadsForLeo(
            Launchers.FALCON_HEAVY, payload, TARGET_ALTITUDE, LAUNCH_LATITUDE_DEG);
    EarthOrbitMission mission =
        new EarthOrbitMission(
            "LEO 400 km / " + atmosphere,
            new LaunchConfiguration(Launchers.FALCON_HEAVY, loads, payload),
            TARGET_ALTITUDE);
    mission.setAtmosphere(atmosphere);

    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    long startedAt = System.currentTimeMillis();
    try {
      MissionComputeResult result = new MissionOptimizer(mission, 40_000, TEST_SEED).optimize();
      long elapsed = System.currentTimeMillis() - startedAt;
      logger.info(
          "  {} converged in {} ms, {} ephemeris points",
          atmosphere,
          elapsed,
          result.ephemeris().allPoints().size());
      return new Attempt(elapsed, "converged", true);
    } catch (RuntimeException failure) {
      long elapsed = System.currentTimeMillis() - startedAt;
      logger.warn("  {} failed after {} ms: {}", atmosphere, elapsed, failure.getMessage());
      return new Attempt(
          elapsed,
          "failed: " + failure.getClass().getSimpleName() + " — " + failure.getMessage(),
          false);
    }
  }
}
