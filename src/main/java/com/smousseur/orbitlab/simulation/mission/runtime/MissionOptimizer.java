package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.MissionStage;
import com.smousseur.orbitlab.simulation.mission.MissionStatus;
import com.smousseur.orbitlab.simulation.mission.OptimizableMissionStage;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransferResult;
import com.smousseur.orbitlab.simulation.mission.maneuver.TransfertTwoManeuver;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.optimizer.CMAESTrajectoryOptimizer;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizationResult;
import com.smousseur.orbitlab.simulation.mission.optimizer.OptimizerDiagnostics;
import com.smousseur.orbitlab.simulation.mission.optimizer.StageEndStateDiagnostic;
import com.smousseur.orbitlab.simulation.mission.optimizer.TrajectoryProblem;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.smousseur.orbitlab.simulation.mission.optimizer.problems.GravityTurnProblem;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferProblem;
import com.smousseur.orbitlab.simulation.mission.optimizer.problems.TransferTwoManeuverProblem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.random.MersenneTwister;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/**
 * Orchestrates the sequential optimization of all stages in a {@link Mission}.
 *
 * <p>Iterates through the mission's stages in order. For each {@link OptimizableMissionStage}, it
 * builds the corresponding {@link TrajectoryProblem}, runs a {@link CMAESTrajectoryOptimizer}, and
 * advances the mission state with the optimal solution. Non-optimizable stages are propagated
 * directly.
 */
public class MissionOptimizer {
  private static final Logger logger = LogManager.getLogger(MissionOptimizer.class);
  private static final long DEFAULT_SEED = 42L;

  private final Mission mission;
  private final int maxEvaluations;
  private final Long seed;

  /**
   * Creates a mission optimizer with a specified evaluation budget per stage and a
   * non-deterministic CMA-ES seed.
   *
   * @param mission the mission whose stages will be optimized
   * @param maxEvaluations maximum number of objective function evaluations per optimizable stage
   */
  public MissionOptimizer(Mission mission, int maxEvaluations) {
    this(mission, maxEvaluations, DEFAULT_SEED);
  }

  /**
   * Creates a mission optimizer with an explicit master seed driving CMA-ES randomness. When {@code
   * seed} is non-null, the same seed yields bit-identical optimization results across runs. When
   * null, a {@link System#nanoTime()} value is used and logged for traceability.
   *
   * @param mission the mission whose stages will be optimized
   * @param maxEvaluations maximum number of objective function evaluations per optimizable stage
   * @param seed master seed for CMA-ES randomness, or null for non-deterministic
   */
  public MissionOptimizer(Mission mission, int maxEvaluations, Long seed) {
    this.mission = mission;
    this.maxEvaluations = maxEvaluations;
    this.seed = seed;
  }

  /**
   * Optimizes all stages of the mission sequentially.
   *
   * <p>Each optimizable stage produces a trajectory problem that is solved via CMA-ES. The
   * resulting optimal parameters and spacecraft state are recorded and used to advance the mission.
   * Non-optimizable stages are propagated in standalone mode.
   *
   * @return the collected optimization results for all optimizable stages, keyed by stage name
   */
  public MissionComputeResult optimize() {
    Map<String, OptimizationResult> results = new LinkedHashMap<>();
    List<StagePerformance> stagePerformances = new ArrayList<>();
    AbsoluteDate launchDate = mission.getCurrentState().getDate();

    long effectiveSeed = seed != null ? seed : System.nanoTime();
    if (seed == null) {
      logger.info("MissionOptimizer running with non-deterministic seed={}", effectiveSeed);
    } else {
      logger.info("MissionOptimizer running with explicit seed={}", effectiveSeed);
    }
    MersenneTwister seedRng = new MersenneTwister(effectiveSeed);

    for (MissionStage stage : mission.getStages()) {
      double massIn = mission.getCurrentState().getMass();
      logger.info("Current mass = {}", massIn);
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        logger.info("Optimizing stage '{}'...", stage.getName());

        TrajectoryProblem problem = optimizable.buildProblem(mission);
        long stageSeed = seedRng.nextLong();
        CMAESTrajectoryOptimizer optimizer =
            new CMAESTrajectoryOptimizer(problem, maxEvaluations, stageSeed);
        OptimizationResult result = optimizer.optimize();

        // Store the entry state so the runtime can start from exactly the same point
        SpacecraftState entryState = mission.getCurrentState();
        result =
            new OptimizationResult(
                result.bestVariables(),
                result.bestCost(),
                result.bestState(),
                result.evaluations(),
                entryState);
        results.put(optimizable.optimizationKey(), result);
        logger.info(
            "Stage '{}' optimized: cost={}, values={}, evaluations={}",
            stage.getName(),
            result.bestCost(),
            result.bestVariables(),
            result.evaluations());

        // ── Phase 0.1 instrumentation: bound saturation ───────────────────
        String[] paramNames = paramNamesFor(problem);
        List<OptimizerDiagnostics.BoundFlag> boundFlags =
            OptimizerDiagnostics.evaluateBounds(
                result.bestVariables(), problem.getLowerBounds(), problem.getUpperBounds());
        OptimizerDiagnostics.logBoundReport(logger, stage.getName(), boundFlags, paramNames);

        if (problem instanceof TransferTwoManeuverProblem transferProblem) {
          // Re-propagate on the calling thread: TransferTwoManeuverProblem's lastResult is
          // ThreadLocal so post-optimization callers running on a different thread (the parallel
          // exploration workers) wouldn't see the worker-thread state.
          transferProblem.propagate(result.bestVariables());
          TransferResult transferResult = transferProblem.getLastTransferResult();
          logger.info(
              "Post burn1 orbit: {}",
              transferResult != null ? transferResult.orbitPostBurn1() : null);
          TransfertTwoManeuver.ResolvedCircularizationBurn burn =
              transferResult != null ? transferResult.circularizationBurn() : null;
          logger.info("Circularization burn: {}", burn);

          // ── Phase 0.1: Δv decomposition + active barriers ──
          TransferTwoManeuverProblem.DvBreakdown dv =
              transferProblem.computeDvBreakdown(result.bestVariables());
          logger.info(
              "Transfert Δv breakdown: total1={} m/s, useful1={} m/s, wasted1={} m/s, dv2={} m/s",
              dv.dvBurn1Total(),
              dv.dvBurn1Useful(),
              dv.dvBurn1Wasted(),
              dv.dvBurn2());
          TransferTwoManeuverProblem.BarrierReport barriers =
              transferProblem.diagnoseBarriers(result.bestVariables());
          logger.info(
              "Transfert barriers: peri={}({}), altMin={}({}), altMax={}({})",
              barriers.periapsisFloor(),
              barriers.periapsisContribution(),
              barriers.altMin(),
              barriers.altMinContribution(),
              barriers.altMax(),
              barriers.altMaxContribution());

          // ── I7 §5.1: propellant-awareness contribution of the retained solution ──
          TransferProblem.PropellantReport prop =
              transferProblem.diagnosePropellant(result.bestVariables());
          logger.info(
              "Transfert propellant term: consumedΔv={} m/s, HohmannΔv={} m/s, excessΔv={} m/s, "
                  + "availableΔv={} m/s, costContribution={}",
              prop.consumedDv(),
              prop.hohmannDv(),
              prop.excessDv(),
              prop.availableDv(),
              prop.costContribution());
        }

        if (problem instanceof GravityTurnProblem) {
          // ── Phase 0.1: GT exit state vs. ideal Hohmann handoff ──
          StageEndStateDiagnostic.EndState actual =
              StageEndStateDiagnostic.from(result.bestState());
          double targetAlt = resolveTargetAltitude(mission);
          if (Double.isFinite(targetAlt)) {
            StageEndStateDiagnostic.EndState ideal =
                StageEndStateDiagnostic.idealHohmannHandoff(targetAlt, actual.altitude());
            logger.info(
                "Gravity turn end-state vs ideal Hohmann: {}",
                StageEndStateDiagnostic.format(actual, ideal));
          } else {
            logger.info(
                "Gravity turn end-state: alt={} m, vTan={} m/s, vRad={} m/s, FPA={}°",
                actual.altitude(),
                actual.vTan(),
                actual.vRad(),
                actual.fpaDeg());
          }
        }

        SpacecraftState propagated = problem.propagate(result.bestVariables());
        mission.setCurrentState(propagated);
        stagePerformances.add(buildStagePerformance(stage, massIn, propagated.getMass()));
      } else {
        logger.info("Propagating non-optimizable stage '{}'...", stage.getName());
        SpacecraftState propagated = stage.propagateStandalone(mission.getCurrentState(), mission);
        mission.setCurrentState(propagated);
        stagePerformances.add(buildStagePerformance(stage, massIn, propagated.getMass()));
        logger.info("Stage '{}' done.", stage.getName());
      }
    }

    MissionPerformanceReport report = buildReport(stagePerformances);
    logReport(report);

    // Inject optimization results into stages for replay
    MissionOptimizerResult optimResult = new MissionOptimizerResult(results);
    for (MissionStage stage : mission.getStages()) {
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        optimResult
            .findFor(optimizable.optimizationKey())
            .ifPresent(optimizable::applyOptimization);
      }
    }

    // Generate the full ephemeris from the original launch date
    SpacecraftState initialState = mission.getInitialState(launchDate);
    MissionEphemerisGenerator generator = new MissionEphemerisGenerator();
    MissionEphemeris ephemeris = generator.generate(mission, initialState);

    mission.setStatus(MissionStatus.READY);
    return new MissionComputeResult(optimResult, ephemeris, report);
  }

  /**
   * Accounts one executed stage. Jettisoned dry mass (drop in remaining dry mass between entry
   * and exit) is excluded from the propellant consumption; ΔV uses the entry stage's Isp, an
   * approximation for stages spanning a jettison. Non-propulsive stages (coasts, separations)
   * drop mass by jettison only — including any residual propellant discarded with the spent
   * stage — so they report zero consumption and zero ΔV.
   */
  private StagePerformance buildStagePerformance(MissionStage stage, double massIn, double massOut) {
    if (!stage.isPropulsive()) {
      return new StagePerformance(stage.getName(), massIn, massOut, 0.0, 0.0);
    }
    Vehicle vehicle = mission.getVehicle();
    double dryIn = vehicle.resolveActiveStage(massIn).remainingDryMass();
    double dryOut = vehicle.resolveActiveStage(massOut).remainingDryMass();
    double jettisonedDry = FastMath.max(0.0, dryIn - dryOut);
    double propellantConsumed = FastMath.max(0.0, massIn - massOut - jettisonedDry);
    double deltaV = 0.0;
    if (propellantConsumed > 0) {
      double isp = vehicle.resolveActiveStage(massIn).propulsion().isp();
      deltaV =
          isp
              * Constants.G0_STANDARD_GRAVITY
              * FastMath.log(massIn / (massIn - propellantConsumed));
    }
    return new StagePerformance(stage.getName(), massIn, massOut, propellantConsumed, deltaV);
  }

  private MissionPerformanceReport buildReport(List<StagePerformance> stagePerformances) {
    double finalMass = mission.getCurrentState().getMass();
    double residual =
        FastMath.max(
            0.0, finalMass - mission.getVehicle().resolveActiveStage(finalMass).remainingDryMass());
    double loaded = mission.getVehicle().propellantLoad();
    double totalDeltaV = stagePerformances.stream().mapToDouble(StagePerformance::deltaV).sum();
    return new MissionPerformanceReport(stagePerformances, totalDeltaV, loaded, residual);
  }

  private static void logReport(MissionPerformanceReport report) {
    for (StagePerformance sp : report.stages()) {
      logger.info(
          "Stage '{}': massIn={} kg, massOut={} kg, propellant={} kg, dV={} m/s",
          sp.stageName(),
          FastMath.round(sp.massIn()),
          FastMath.round(sp.massOut()),
          FastMath.round(sp.propellantConsumed()),
          FastMath.round(sp.deltaV()));
    }
    logger.info(
        "Mission performance: total dV={} m/s, propellant loaded={} kg, residual={} kg ({}%)",
        FastMath.round(report.totalDeltaV()),
        FastMath.round(report.totalPropellantLoaded()),
        FastMath.round(report.totalPropellantResidual()),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * report.residualRatio()));
  }

  private static String[] paramNamesFor(TrajectoryProblem problem) {
    if (problem instanceof GravityTurnProblem) {
      return new String[] {"transitionTime", "exponent"};
    }
    if (problem instanceof TransferProblem) {
      return new String[] {"t1", "dt1", "α1", "β1"};
    }
    int n = problem.getNumVariables();
    String[] names = new String[n];
    for (int i = 0; i < n; i++) names[i] = "x" + i;
    return names;
  }

  private static double resolveTargetAltitude(Mission mission) {
    MissionObjective objective = mission.getObjective();
    if (objective instanceof OrbitInsertionObjective insertion) {
      return 0.5 * (insertion.perigeeAltitude() + insertion.apogeeAltitude());
    }
    return Double.NaN;
  }
}
