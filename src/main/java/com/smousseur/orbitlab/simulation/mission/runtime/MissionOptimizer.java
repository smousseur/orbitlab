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
import com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage;
import com.smousseur.orbitlab.simulation.mission.vehicle.ActiveStageInfo;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    // Propellant discarded with a stage dropped before depletion, keyed by stack index. Once the
    // mass has dropped it is indistinguishable from burnt propellant, so it must be read here.
    Map<Integer, Double> jettisonedResiduals = new LinkedHashMap<>();
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
      // Captured with massIn, before either branch propagates: the two branches below both leave
      // the flown end state in `propagated`, so this is the only reading of the entry instant that
      // is valid for both.
      AbsoluteDate stageEntryDate = mission.getCurrentState().getDate();
      logger.info("Current mass = {}", massIn);
      captureJettisonedResidual(stage, massIn, jettisonedResiduals);
      if (stage instanceof OptimizableMissionStage<?> optimizable) {
        logger.info("Optimizing stage '{}'...", stage.getName());

        // Captured BEFORE the optimizer runs. A problem that flies real mission stages (the ascent
        // chain, spec 01 §5.4) advances the shared mission as it goes — and does so from the
        // parallel CMA-ES exploration threads — so mission.getCurrentState() is no longer the stage
        // entry once optimize() returns. Reading it here, and restoring it below, keeps the loop on
        // the state the stage actually starts from.
        SpacecraftState entryState = mission.getCurrentState();

        TrajectoryProblem problem = optimizable.buildProblem(mission);
        long stageSeed = seedRng.nextLong();
        CMAESTrajectoryOptimizer optimizer =
            new CMAESTrajectoryOptimizer(problem, maxEvaluations, stageSeed);
        OptimizationResult result = optimizer.optimize();
        mission.setCurrentState(entryState);

        // Store the entry state so the runtime can start from exactly the same point
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

        SpacecraftState propagated;
        if (optimizable.advancesByReplay()) {
          // The problem flew a chain this loop is about to walk itself (spec 01 §5.6). Inject the
          // result now — the phases that follow read the plan it publishes — and advance one phase
          // at a time, so each gets its own accounting line instead of the chain's aggregate.
          optimizable.applyOptimization(result);
          propagated = stage.propagateStandalone(entryState, mission);
        } else {
          propagated = problem.propagate(result.bestVariables());
        }
        mission.setCurrentState(propagated);
        double duration = propagated.getDate().durationFrom(stageEntryDate);
        stagePerformances.add(
            buildStagePerformance(stage, massIn, propagated.getMass(), duration));
      } else {
        logger.info("Propagating non-optimizable stage '{}'...", stage.getName());
        SpacecraftState propagated = stage.propagateStandalone(mission.getCurrentState(), mission);
        mission.setCurrentState(propagated);
        double duration = propagated.getDate().durationFrom(stageEntryDate);
        stagePerformances.add(
            buildStagePerformance(stage, massIn, propagated.getMass(), duration));
        logger.info("Stage '{}' done.", stage.getName());
      }
    }

    MissionPerformanceReport report = buildReport(stagePerformances, jettisonedResiduals);
    logReport(report);

    AchievedOrbit achievedOrbit = AchievedOrbit.of(mission.getCurrentState());
    logAchievedOrbit(achievedOrbit);

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
    // Record the launch date on the flown mission: it is the telemetry MET base, and the caller may
    // adopt this mission (the sizing sweep's winning one) in place of the pre-sweep composition.
    mission.setInitialDate(launchDate);
    // Resolve the restitution horizon here rather than in the generator: this is the one place that
    // holds both ends of what the policy needs — the launch date and the insertion state — and it
    // already reads the achieved orbit off the very same state a few lines above. The generator
    // receives seconds, not an intent (spec specs/mission-horizon/01-horizon-explicite.md §4).
    //
    // mission.getCurrentState() is the insertion state at this point: the trailing CoastingStage
    // does not override propagateStandalone, so the stage walk above left the state at the end of
    // the last flown stage. That is also why this horizon cannot move an optimizer baseline — the
    // final coast is never flown on the optimize pass at all.
    double finalCoastSeconds =
        mission.getHorizon().finalCoastSeconds(launchDate, mission.getCurrentState());
    logger.info(
        "Restitution horizon: {} -> {} s of trailing coast",
        mission.getHorizon().describe(),
        String.format(Locale.ROOT, "%.0f", finalCoastSeconds));

    MissionEphemerisGenerator generator = new MissionEphemerisGenerator();
    MissionEphemeris ephemeris = generator.generate(mission, initialState, finalCoastSeconds);

    mission.setStatus(MissionStatus.READY);
    return new MissionComputeResult(optimResult, ephemeris, report, mission, achievedOrbit);
  }

  /**
   * Accounts one executed stage. Jettisoned dry mass (drop in remaining dry mass between entry and
   * exit) is excluded from the propellant consumption, and ΔV uses the entry stage's Isp.
   * Non-propulsive stages (coasts, separations) drop mass by jettison only — including any residual
   * propellant discarded with the spent stage — so they report zero consumption and zero ΔV.
   *
   * <p><b>No stage spans a jettison any more</b> (spec {@code
   * specs/mission-stages/01-separations-implicites.md}, S2), so the entry stage's Isp is the only
   * Isp burnt during the stage and this accounting is exact. It used to be an approximation: the
   * ascent was one stage carrying burn 1, a 66 t jettison and burn 2, and a single Tsiolkovsky
   * across a mass drop is not an approximation but a category error — on the Falcon Heavy LEO
   * profile the ascent reported 5 648 m/s where the staged computation gives 7 781 m/s. Every
   * jettison being its own non-propulsive phase is what makes the formula below correct rather than
   * indicative; a future stage that dropped mass mid-burn would silently reintroduce the error.
   */
  private StagePerformance buildStagePerformance(
      MissionStage stage, double massIn, double massOut, double durationSeconds) {
    if (!stage.isPropulsive()) {
      return new StagePerformance(stage.getName(), massIn, massOut, 0.0, 0.0, durationSeconds);
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
    return new StagePerformance(
        stage.getName(), massIn, massOut, propellantConsumed, deltaV, durationSeconds);
  }

  /**
   * Records the propellant discarded with a stage separated before it ran dry. {@code massIn} is
   * the mass before {@link StageSeparationStage#enter} drops it to the stack-above reference mass,
   * so it is the last moment the jettisoned stage's remaining fuel is observable.
   */
  private void captureJettisonedResidual(
      MissionStage stage, double massIn, Map<Integer, Double> jettisonedResiduals) {
    if (!(stage instanceof StageSeparationStage)) {
      return;
    }
    ActiveStageInfo dropped = mission.getVehicle().resolveActiveStage(massIn);
    double left = FastMath.max(0.0, dropped.remainingFuel(massIn));
    jettisonedResiduals.put(dropped.stageIndex(), left);
    logger.info(
        "Stage separation '{}': jettisoning stack stage {} with {} kg of propellant aboard",
        stage.getName(),
        dropped.stageIndex(),
        FastMath.round(left));
  }

  private MissionPerformanceReport buildReport(
      List<StagePerformance> stagePerformances, Map<Integer, Double> jettisonedResiduals) {
    double finalMass = mission.getCurrentState().getMass();
    Vehicle vehicle = mission.getVehicle();
    double residual =
        FastMath.max(0.0, finalMass - vehicle.resolveActiveStage(finalMass).remainingDryMass());
    double loaded = vehicle.propellantLoad();
    double totalDeltaV = stagePerformances.stream().mapToDouble(StagePerformance::deltaV).sum();
    return new MissionPerformanceReport(
        stagePerformances,
        totalDeltaV,
        loaded,
        residual,
        resolveStagePropellant(vehicle, finalMass, jettisonedResiduals));
  }

  /**
   * The per-stage propellant split at mission end, with the stages dropped early restored to the
   * residual observed at their separation (the mass model alone would report them as burnt out).
   */
  private static List<StagePropellant> resolveStagePropellant(
      Vehicle vehicle, double finalMass, Map<Integer, Double> jettisonedResiduals) {
    List<StagePropellant> perStage = new ArrayList<>(vehicle.resolveStagePropellant(finalMass));
    perStage.replaceAll(
        sp -> {
          Double jettisoned = jettisonedResiduals.get(sp.stageIndex());
          return jettisoned == null
              ? sp
              : new StagePropellant(sp.stageIndex(), sp.loaded(), jettisoned);
        });
    return List.copyOf(perStage);
  }

  private static void logReport(MissionPerformanceReport report) {
    for (StagePerformance sp : report.stages()) {
      logger.info(
          "Stage '{}': massIn={} kg, massOut={} kg, propellant={} kg, dV={} m/s, duration={} s",
          sp.stageName(),
          FastMath.round(sp.massIn()),
          FastMath.round(sp.massOut()),
          FastMath.round(sp.propellantConsumed()),
          FastMath.round(sp.deltaV()),
          FastMath.round(sp.durationSeconds()));
    }
    logger.info(
        "Mission performance: total dV={} m/s, propellant loaded={} kg, residual={} kg ({}%)",
        FastMath.round(report.totalDeltaV()),
        FastMath.round(report.totalPropellantLoaded()),
        FastMath.round(report.totalPropellantResidual()),
        String.format(java.util.Locale.ROOT, "%.1f", 100.0 * report.residualRatio()));
    // Per-stage split (bilan 10 §6): the margin that actually matters for a sized stage, which the
    // stack-wide ratio above cannot show on an S1-dominated stack.
    for (StagePropellant sp : report.stagePropellants()) {
      logger.info(
          "Stage propellant [{}]: loaded={} kg, consumed={} kg, residual={} kg ({}% of its load)",
          sp.stageIndex(),
          FastMath.round(sp.loaded()),
          FastMath.round(sp.consumed()),
          FastMath.round(sp.residual()),
          String.format(java.util.Locale.ROOT, "%.1f", 100.0 * sp.residualRatio()));
    }
  }

  /**
   * The orbit achieved at the end of the mission, in both conventions (spec orbit-reporting/01).
   *
   * <p>Reporting only: neither value is read back into the computation. Between insertion and the
   * end of the coast, the osculating orbit has oscillated by about 19 km under J2 while the mean
   * one only moved by its secular drift — a comparable swing on both lines would signal that the
   * conversion is wrong.
   *
   * <p>Reading note: the mean line of an insertion aimed circular is not circular, and the gap is
   * not a targeting miss. See {@link AchievedOrbit}.
   */
  private static void logAchievedOrbit(AchievedOrbit achieved) {
    logger.info("Achieved orbit (osculating): {}", achieved.formatOsculating());
    logger.info("Achieved orbit (mean):       {}", achieved.formatMean());
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
