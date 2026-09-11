package com.smousseur.orbitlab.simulation.mission.optimizer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.mission.operation.EarthOrbitMission;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.LaunchConfiguration;
import com.smousseur.orbitlab.simulation.mission.vehicle.Spacecraft;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * PHY-2 / L1 proof 2 (spec {@code docs/atmosphere/08-conception-L1-PHY-2.md} §5): a drag-on ascent
 * optimization <em>terminates</em>. Before the socle a candidate that piqued low ran 982 497
 * integration steps and never returned ({@code docs/atmosphere/07-baseline-L0-PHY-2.md} §1.2); the
 * descent-gated drag stop of {@code ReentryGuard} bounds it, so a Falcon Heavy LEO optimization
 * flown under NRLMSISE now runs to a result.
 *
 * <p><b>Termination is the only assertion, and that is deliberate.</b> The achieved orbit is
 * <em>not</em> checked against the target: the catalog Isp still double-counts the drag it was a
 * proxy for (DT-13), so a drag-on ascent inserts low until {@code L2} re-calibrates it. What L1
 * closes is that the optimization bounds its cost and returns at all — which it could not do before.
 *
 * <p><b>This is also the {@code a} vs {@code c} measurement (§6).</b> This mission flies candidate
 * {@code a} — the optimizer mounts the mission's own model, NRLMSISE, with no substitution. Its wall
 * clock against the drag-off Falcon Heavy analytic (~7 s, {@code AscentBaselineN2Test}) is the +50 %
 * check that decides whether {@code a} stands or the composite {@code c} is needed. The number is
 * recorded in the log, not asserted — it is an input of {@code L2}+, not a property of {@code L1}.
 *
 * <p>Flies the <b>analytic</b> Falcon Heavy LEO profile (the {@code testFalconHeavy} configuration),
 * not the optimized transfer: that path is broken independently of drag ({@code docs/bugs.md}
 * BUG-25, fixed with {@code L3}), so exercising it here would measure that regression instead of the
 * ascent.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class AscentDragTerminationTest extends AbstractTrajectoryOptimizerTest {

  private static final Logger logger = LogManager.getLogger(AscentDragTerminationTest.class);

  /**
   * Generous wall-clock bound. It is not a performance target — it turns a non-terminating run back
   * into a failure. A drag-on ascent within the announced +50 % finishes in seconds; a preemptive
   * timeout here means either the socle failed to bound a low-diving candidate, or the ascent's own
   * cost is so far past +50 % that candidate {@code a} is not viable (Axis 1, §3.3).
   */
  private static final Duration TERMINATION_BUDGET = Duration.ofMinutes(10);

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @Test
  void falconHeavyLeo_underNrlmsise_optimizationTerminates() {
    EarthOrbitMission mission =
        new EarthOrbitMission(
            "Falcon Heavy (drag-on NRLMSISE ascent)",
            new LaunchConfiguration(
                Launchers.FALCON_HEAVY, new double[] {400_000, 200_000, 100_000}, Spacecraft.LEGACY),
            400_000);
    mission.setAtmosphere(AtmosphereModel.NRLMSISE);

    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    mission.setCurrentState(mission.getInitialState(epoch));

    long start = System.nanoTime();
    MissionComputeResult result =
        assertTimeoutPreemptively(
            TERMINATION_BUDGET,
            () -> new MissionOptimizer(mission, 40_000, TEST_SEED).optimize(),
            "the drag-on ascent optimization did not terminate within the budget");
    double seconds = (System.nanoTime() - start) / 1e9;

    assertNotNull(result, "the optimization must return a result under drag");
    logger.info(
        "[PHY-2 L1 a/c] Falcon Heavy LEO-400 drag-on (NRLMSISE) optimization terminated in {} s "
            + "(candidate a; compare to the ~7 s drag-off analytic for the +50 % check)",
        seconds);
  }
}
