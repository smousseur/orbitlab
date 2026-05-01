package com.smousseur.orbitlab.simulation.mission.optimizer;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.LEOMission;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizerResult;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

/**
 * Phase 0.2 reference parametric sweep — see {@code
 * specs/optimizer/03-robustness-roadmap.md}.
 *
 * <p>Pure measurement: no JUnit assertions. Each altitude produces a single
 * {@code SWEEP_BASELINE | …} log line that captures the convergence indicators
 * needed as a baseline before any tuning of the optimizer or its bounds. The
 * detailed per-stage instrumentation (bound saturation, Δv breakdown, barriers,
 * end-state vs Hohmann) is emitted by {@code MissionOptimizer} above each
 * summary line.
 *
 * <p>Tagged {@code slow} so it stays out of the default {@code ./gradlew test}
 * loop. Run explicitly with
 * {@code ./gradlew test --tests LEOAltitudeSweepTest}.
 */
@Tag("slow")
public class LEOAltitudeSweepTest extends AbstractTrajectoryOptimizerTest {
  private static final Logger logger = LogManager.getLogger(LEOAltitudeSweepTest.class);

  private static final String COASTING_STAGE = "Coasting";
  private static final String TRANSFER_STAGE = "Transfert";

  @BeforeAll
  static void init() {
    OrekitService.get().initialize();
  }

  @ParameterizedTest(name = "targetAltitude={0}m")
  @ValueSource(
      doubles = {185_000, 250_000, 400_000, 600_000, 800_000, 1_200_000, 1_500_000, 2_000_000})
  void sweepAltitudes(double targetAltitude) {
    AbsoluteDate epoch = new AbsoluteDate(2026, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
    int km = (int) (targetAltitude / 1000);
    Mission mission = new LEOMission("LEO sweep " + km + "km", targetAltitude);
    SpacecraftState initialState = mission.getInitialState(epoch);
    mission.setCurrentState(initialState);

    MissionOptimizer optimizer = new MissionOptimizer(mission, 40_000);
    MissionComputeResult computeResult = optimizer.optimize();
    MissionEphemeris ephemeris = computeResult.ephemeris();

    MinMaxAltitudeResults coast = extractMinMaxAltitudes(ephemeris, COASTING_STAGE);

    MissionOptimizerResult optimResult = computeResult.optimizerResult();
    Map<String, OptimizationResult> byStage = optimResult.resultsByStageKey();

    StringBuilder line = new StringBuilder();
    line.append(
        String.format(
            Locale.ROOT, "SWEEP_BASELINE | targetAlt=%d", km * 1000));

    int totalEvals = 0;
    double totalCost = 0.0;
    for (Map.Entry<String, OptimizationResult> e : byStage.entrySet()) {
      OptimizationResult r = e.getValue();
      totalEvals += r.evaluations();
      totalCost += r.bestCost();
      line.append(
          String.format(
              Locale.ROOT,
              " | %s.cost=%.6g | %s.evals=%d | %s.params=%s",
              e.getKey(),
              r.bestCost(),
              e.getKey(),
              r.evaluations(),
              e.getKey(),
              Arrays.toString(r.bestVariables())));
    }
    line.append(
        String.format(
            Locale.ROOT,
            " | totalCost=%.6g | totalEvals=%d | maxAlt=%.0f | minAlt=%.0f",
            totalCost,
            totalEvals,
            coast.maxAltitude,
            coast.minAltitude));

    OptimizationResult transfer = byStage.get(TRANSFER_STAGE);
    if (transfer != null) {
      line.append(
          String.format(
              Locale.ROOT,
              " | apoErr=%.0f | periErr=%.0f",
              coast.maxAltitude - targetAltitude,
              coast.minAltitude - targetAltitude));
    }

    logger.info(line.toString());
  }
}
