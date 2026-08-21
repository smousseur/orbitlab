package com.smousseur.orbitlab.simulation.mission.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.simulation.OrbitElements;
import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionFactory;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.optimizer.AbstractTrajectoryOptimizerTest;
import com.smousseur.orbitlab.simulation.mission.planner.FixedLoadPlanner;
import com.smousseur.orbitlab.simulation.mission.planner.MissionPlan;
import com.smousseur.orbitlab.simulation.mission.planner.ReplayPlanner;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionSolutions;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * The measurement the whole replay path rests on: a mission whose vectors are known reaches the
 * same orbit as the optimization that produced them, without a single CMA-ES evaluation.
 *
 * <p>It compares the <b>orbits reached</b>, not the vectors. Identical vectors would prove only
 * that a map survived a copy; identical orbits prove that flying them reproduces the trajectory,
 * which is what a reopened scenario has to do.
 *
 * <p>Opt-in — the reference run is a real optimization and costs minutes. Enable with {@code
 * -Dorbitlab.slowTests=true}.
 */
@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")
class ScenarioReplayTest extends AbstractTrajectoryOptimizerTest {

  private static final Logger logger = LogManager.getLogger(ScenarioReplayTest.class);

  /**
   * The replay re-propagates the very same problems at the very same vectors, so the two orbits
   * should agree to the propagator's own repeatability rather than to a physical tolerance. A metre
   * of semi-major axis over a 6 778 km orbit is 1.5e-7 relative, tight enough that any real
   * divergence — a load applied differently, a stage entered from another state — fails it.
   */
  private static final double SEMI_MAJOR_AXIS_TOLERANCE_M = 1.0;

  private static final int MAX_EVALUATIONS = 40_000;

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(
        OrekitService.class.getClassLoader().getResource("orekit-data.zip") != null,
        "orekit-data.zip not on classpath — skipping");
    OrekitService.get().initialize();
  }

  private static AbsoluteDate launchEpoch() {
    return new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
  }

  private static MissionSpec leoSpec() {
    Map<String, Object> values = new HashMap<>();
    values.put("MISSION_NAME", "Replayed LEO");
    values.put("LAUNCH_SITE_LAT", 5.236);
    values.put("LAUNCH_SITE_LONG", -52.769);
    values.put("LAUNCH_SITE_ALT", 14.0);
    values.put("LAUNCHER_TYPE", "FALCON_HEAVY");
    values.put("PAYLOAD_TYPE", "EARTH_OBS_SAT");
    values.put("PAYLOAD_MASS", 8_000.0);
    values.put("LEO_PERIGEE_ALT", 400.0);
    values.put("LEO_APOGEE_ALT", 400.0);
    return MissionFactory.specFromWizardValues(values, MissionType.LEO);
  }

  private static Mission compose(MissionSpec spec) {
    Mission mission = MissionComposer.compose(spec, OptimizationType.BALANCED);
    mission.setCurrentState(mission.getInitialState(launchEpoch()));
    return mission;
  }

  @Test
  void replayReachesTheOrbitTheOptimizationFound() {
    MissionSpec spec = leoSpec();

    MissionPlan optimized =
        new FixedLoadPlanner(compose(spec), MAX_EVALUATIONS, TEST_SEED).plan();
    MissionSolutions solutions =
        MissionSolutions.from(optimized.computation().optimizerResult(), null);

    Mission replayed = compose(spec);
    assertTrue(
        solutions.covers(replayed),
        "the optimization must describe exactly the composition it flew");

    MissionPlan plan =
        new ReplayPlanner(
                replayed,
                spec,
                OptimizationType.BALANCED,
                solutions,
                launchEpoch(),
                MAX_EVALUATIONS,
                TEST_SEED,
                null)
            .plan();

    OrbitElements reference = optimized.computation().achievedOrbit().osculating();
    OrbitElements flown = plan.computation().achievedOrbit().osculating();
    assertNotNull(reference, "the reference run reached no readable orbit");
    assertNotNull(flown, "the replay reached no readable orbit");
    logger.info("Reference orbit: {}", reference.format());
    logger.info("Replayed  orbit: {}", flown.format());

    assertEquals(
        reference.semiMajorAxis(),
        flown.semiMajorAxis(),
        SEMI_MAJOR_AXIS_TOLERANCE_M,
        "semi-major axis");
    assertEquals(reference.eccentricity(), flown.eccentricity(), 1e-9, "eccentricity");
    assertEquals(reference.inclination(), flown.inclination(), 1e-9, "inclination");
  }

  /** {@code evaluations = 0} is how a replayed stage says, honestly, that it was not optimized. */
  @Test
  void replayedStagesReportNoEvaluation() {
    MissionSpec spec = leoSpec();
    MissionPlan optimized =
        new FixedLoadPlanner(compose(spec), MAX_EVALUATIONS, TEST_SEED).plan();
    MissionSolutions solutions =
        MissionSolutions.from(optimized.computation().optimizerResult(), null);

    MissionPlan plan =
        new ReplayPlanner(
                compose(spec),
                spec,
                OptimizationType.BALANCED,
                solutions,
                launchEpoch(),
                MAX_EVALUATIONS,
                TEST_SEED,
                null)
            .plan();

    plan.computation()
        .optimizerResult()
        .resultsByStageKey()
        .forEach(
            (key, result) ->
                assertEquals(0, result.evaluations(), "stage '" + key + "' was optimized"));
  }
}
