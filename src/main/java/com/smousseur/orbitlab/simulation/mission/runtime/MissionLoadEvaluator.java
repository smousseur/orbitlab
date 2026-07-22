package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.StagePropellant;
import java.util.Objects;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Production {@link PropellantLoadOptimizer.Evaluator} of the I7 outer loop (spec 09 §6 task 2):
 * one evaluation rebuilds the mission with {@code loads(λ)}, runs a full {@link MissionOptimizer}
 * and decides feasibility from the result.
 *
 * <p><b>Reconstruction.</b> The λ scaling is applied to the heuristic loads over the liquid stages
 * only (SOLID stages and the payload AKM stay off λ, {@link PropellantLoadOptimizer#scaledLoads});
 * the scaled loads are handed to an injected {@code missionBuilder} that assembles a fresh {@link
 * Mission} — a fresh one every time, because {@link MissionOptimizer#optimize()} mutates the mission
 * it optimizes.
 *
 * <p><b>Feasibility.</b> The mission is feasible when both hold:
 *
 * <ul>
 *   <li><b>objective</b> — the final coast orbit lands within {@code objectiveToleranceRatio} of the
 *       target perigee and apogee, measured from the ephemeris exactly as the mission optimization
 *       tests do (min/max altitude of the terminal {@code "Coasting"} stage). The target is the
 *       mission's own {@link OrbitInsertionObjective} by default; missions whose recorded objective
 *       is not the flown final orbit must pass an explicit feasibility objective — {@code
 *       GEOMission} records {@code (parking, GEO)}, the two mission phases, while the flown end
 *       state is circular GEO;
 *   <li><b>residual floor</b> — the end-of-mission residual is at least {@code residualFloorRatio}
 *       of the <em>sized stage's</em> load (spec 09 §5, "≥ 1 % de la charge par étage liquide"). The
 *       denominator is the load of the λ-scaled top stage, not the whole stack: on an S1-dominated
 *       stack the stack-wide residual ratio is meaningless (~0.3 %), but against the sized stage's
 *       own load it is the real margin (~10 % at the heuristic load). Without this floor the loop
 *       drives the sized stage to exact flame-out (residual 0), and the ephemeris replay trips the
 *       {@code DepletionGuard} on a truncated burn — a knife-edge solution that defeats I7's realism
 *       goal (observed on the first FH LEO integration run).
 * </ul>
 *
 * <p>The residual of the sized stage is read from its own entry in {@link
 * MissionPerformanceReport#stagePropellants()} (bilan 10 §6), so the predicate holds even when the
 * sized stage is not the final active stage — the stack-wide total would then also count the
 * propellant of whatever sits above it and mask an emptied sized stage.
 *
 * <p>An optimization that <em>throws</em> (an under-dotée load whose ascent/transfer cannot reach
 * orbit makes CMA-ES fail) is caught and reported as infeasible — that is the signal the bisection
 * needs to keep λ up, not an error to propagate.
 *
 * <p><b>Warm-start.</b> The {@code previous} evaluation is available for warm-starting, but each
 * rebuilt mission's stages already warm-start their inner CMA-ES from a reliable analytic seed
 * (spec 09 §5, commit {@code a8fb39e}), recomputed for the new load, so cross-λ reinjection of the
 * previous {@code bestVariables} is a further speed-up gated on the optimizer exposing a seed hook
 * — deliberately not done here to keep the FH-neutral optimizer core untouched.
 */
public final class MissionLoadEvaluator implements PropellantLoadOptimizer.Evaluator {
  private static final Logger logger = LogManager.getLogger(MissionLoadEvaluator.class);

  /** Terminal coasting stage both LEO and GEO profiles end with — where the orbit is measured. */
  private static final String FINAL_COAST_STAGE = "Coasting";

  /** Default objective tolerance: the ±7 % band the mission optimization tests assert on. */
  public static final double DEFAULT_OBJECTIVE_TOLERANCE_RATIO = 0.07;

  /** Default residual floor: the sized stage keeps ≥ 1 % of its own load, off flame-out (spec §5). */
  public static final double DEFAULT_RESIDUAL_FLOOR_RATIO = 0.01;

  /** Default per-stage CMA-ES evaluation budget, matching the mission optimization tests. */
  public static final int DEFAULT_OPTIMIZER_MAX_EVALUATIONS = 40_000;

  private final Function<double[], Mission> missionBuilder;
  private final double[] heuristicLoads;
  private final boolean[] lambdaScaled;
  private final AbsoluteDate launchEpoch;
  private final int optimizerMaxEvaluations;
  private final Long seed;
  private final double objectiveToleranceRatio;
  private final double residualFloorRatio;
  // The orbit the terminal coast is measured against; null → the mission's own objective.
  private final OrbitInsertionObjective feasibilityObjective;

  /**
   * Creates an evaluator with the spec-09 defaults (±7 % objective, 40 000 inner evals, deterministic
   * seed).
   *
   * @param missionBuilder assembles a fresh mission from a per-stage launcher load array
   * @param heuristicLoads the baseline per-stage loads (kg), same order as the launcher stages
   * @param lambdaScaled which stages the λ scaling applies to ({@link
   *     PropellantLoadOptimizer#lambdaScaledMask})
   * @param launchEpoch the launch date the mission's initial state is built at
   */
  public MissionLoadEvaluator(
      Function<double[], Mission> missionBuilder,
      double[] heuristicLoads,
      boolean[] lambdaScaled,
      AbsoluteDate launchEpoch) {
    this(
        missionBuilder,
        heuristicLoads,
        lambdaScaled,
        launchEpoch,
        DEFAULT_OPTIMIZER_MAX_EVALUATIONS,
        42L,
        DEFAULT_OBJECTIVE_TOLERANCE_RATIO,
        DEFAULT_RESIDUAL_FLOOR_RATIO);
  }

  /**
   * Creates an evaluator with explicit settings, measuring feasibility against the mission's own
   * objective.
   *
   * <p>See {@link #MissionLoadEvaluator(Function, double[], boolean[], AbsoluteDate, int, Long,
   * double, double, OrbitInsertionObjective) the full overload} for the parameter documentation.
   */
  public MissionLoadEvaluator(
      Function<double[], Mission> missionBuilder,
      double[] heuristicLoads,
      boolean[] lambdaScaled,
      AbsoluteDate launchEpoch,
      int optimizerMaxEvaluations,
      Long seed,
      double objectiveToleranceRatio,
      double residualFloorRatio) {
    this(
        missionBuilder,
        heuristicLoads,
        lambdaScaled,
        launchEpoch,
        optimizerMaxEvaluations,
        seed,
        objectiveToleranceRatio,
        residualFloorRatio,
        null);
  }

  /**
   * Creates an evaluator with explicit settings and an explicit feasibility objective.
   *
   * @param missionBuilder assembles a fresh mission from a per-stage launcher load array
   * @param heuristicLoads the baseline per-stage loads (kg), same order as the launcher stages
   * @param lambdaScaled which stages the λ scaling applies to
   * @param launchEpoch the launch date the mission's initial state is built at
   * @param optimizerMaxEvaluations the inner CMA-ES evaluation budget per stage
   * @param seed the CMA-ES master seed, or {@code null} for non-deterministic
   * @param objectiveToleranceRatio the ± band on perigee/apogee the objective must land within
   * @param residualFloorRatio the minimum end-of-mission residual as a fraction of the sized stage's
   *     load; keeps the sized stage off flame-out
   * @param feasibilityObjective the orbit the terminal coast is measured against, or {@code null}
   *     to use the mission's own {@link OrbitInsertionObjective}. Required for missions whose
   *     recorded objective is not the flown final orbit (a GEO mission records {@code (parking,
   *     GEO)} while its end state is circular GEO)
   */
  public MissionLoadEvaluator(
      Function<double[], Mission> missionBuilder,
      double[] heuristicLoads,
      boolean[] lambdaScaled,
      AbsoluteDate launchEpoch,
      int optimizerMaxEvaluations,
      Long seed,
      double objectiveToleranceRatio,
      double residualFloorRatio,
      OrbitInsertionObjective feasibilityObjective) {
    this.missionBuilder = Objects.requireNonNull(missionBuilder, "missionBuilder");
    this.heuristicLoads = heuristicLoads.clone();
    this.lambdaScaled = lambdaScaled.clone();
    this.launchEpoch = Objects.requireNonNull(launchEpoch, "launchEpoch");
    if (this.heuristicLoads.length != this.lambdaScaled.length) {
      throw new IllegalArgumentException("heuristicLoads and lambdaScaled length mismatch");
    }
    if (!(objectiveToleranceRatio >= 0)) {
      throw new IllegalArgumentException("objectiveToleranceRatio must be >= 0");
    }
    if (!(residualFloorRatio >= 0)) {
      throw new IllegalArgumentException("residualFloorRatio must be >= 0");
    }
    this.optimizerMaxEvaluations = optimizerMaxEvaluations;
    this.seed = seed;
    this.objectiveToleranceRatio = objectiveToleranceRatio;
    this.residualFloorRatio = residualFloorRatio;
    this.feasibilityObjective = feasibilityObjective;
  }

  @Override
  public PropellantLoadOptimizer.Evaluation evaluate(
      double lambda, PropellantLoadOptimizer.Evaluation previous) {
    double[] loads = PropellantLoadOptimizer.scaledLoads(lambda, heuristicLoads, lambdaScaled);
    Mission mission = missionBuilder.apply(loads);
    mission.setCurrentState(mission.getInitialState(launchEpoch));

    MissionComputeResult result;
    try {
      result = new MissionOptimizer(mission, optimizerMaxEvaluations, seed).optimize();
    } catch (RuntimeException e) {
      // An under-dotée load whose ascent/transfer cannot reach orbit makes the inner optimizer
      // fail. That is a feasibility signal (keep λ up), not an error to bubble up.
      logger.info("λ={} evaluation failed to optimize ({}); treating as infeasible", lambda, e.toString());
      return new PropellantLoadOptimizer.Evaluation(lambda, false, null);
    }

    OrbitInsertionObjective objective =
        feasibilityObjective != null
            ? feasibilityObjective
            : orbitInsertionObjective(mission.getObjective());
    boolean objectiveMet = objectiveMet(result.ephemeris(), objective, objectiveToleranceRatio);

    MissionPerformanceReport report = result.performanceReport();
    int sizedStageIndex = sizedStageIndex();
    boolean residualOk = residualSufficient(report, sizedStageIndex, residualFloorRatio);
    boolean feasible = objectiveMet && residualOk;

    // Report the sized stage's own numbers when available — the stack-wide total counts everything
    // above it too, and would overstate the margin of the stage the loop is actually sizing.
    StagePropellant sized = report.residualForStage(sizedStageIndex).orElse(null);
    double residual = sized != null ? sized.residual() : report.totalPropellantResidual();
    double sizedStageLoad = sized != null ? sized.loaded() : sizedStageLoad(loads);
    logger.info(
        "λ={} evaluation: objectiveMet={}, sized stage [{}] residual={} kg of its {} kg load"
            + " ({} vs floor {}) → feasible={}",
        lambda,
        objectiveMet,
        sizedStageIndex,
        Math.round(residual),
        Math.round(sizedStageLoad),
        sizedStageLoad > 0 ? residual / sizedStageLoad : Double.NaN,
        residualFloorRatio,
        feasible);

    return new PropellantLoadOptimizer.Evaluation(lambda, feasible, result);
  }

  /**
   * Whether the terminal coast orbit meets the insertion objective within tolerance, measured from
   * the ephemeris the same way the mission optimization tests do: the min and max altitude of the
   * final {@code "Coasting"} stage must land within {@code toleranceRatio} of the target perigee and
   * apogee respectively.
   *
   * @param ephemeris the computed mission ephemeris
   * @param objective the orbit insertion objective (perigee/apogee targets)
   * @param toleranceRatio the ± band, as a fraction of each target altitude
   * @return {@code true} when both perigee and apogee land within tolerance
   */
  public static boolean objectiveMet(
      MissionEphemeris ephemeris, OrbitInsertionObjective objective, double toleranceRatio) {
    double minAltitude = Double.POSITIVE_INFINITY;
    double maxAltitude = Double.NEGATIVE_INFINITY;
    for (MissionEphemerisPoint point : ephemeris.allPoints()) {
      if (FINAL_COAST_STAGE.equals(point.stageName())) {
        minAltitude = Math.min(minAltitude, point.altitudeMeters());
        maxAltitude = Math.max(maxAltitude, point.altitudeMeters());
      }
    }
    if (!Double.isFinite(minAltitude) || !Double.isFinite(maxAltitude)) {
      return false; // no terminal coast samples — the mission did not reach its final orbit
    }
    boolean apogeeOk =
        Math.abs(maxAltitude - objective.apogeeAltitude())
            <= toleranceRatio * objective.apogeeAltitude();
    boolean perigeeOk =
        Math.abs(minAltitude - objective.perigeeAltitude())
            <= toleranceRatio * objective.perigeeAltitude();
    return apogeeOk && perigeeOk;
  }

  /**
   * Whether the sized stage's <em>own</em> residual clears the floor: {@code residual ≥ floorRatio ·
   * load}, both read from the stage's entry in {@link MissionPerformanceReport#stagePropellants()}
   * (bilan 10 §6). This keeps the sized (λ-scaled top) stage off flame-out — the whole-stack
   * residual ratio is meaningless on an S1-dominated stack, but against the sized stage's own load
   * it is the real margin (spec 09 §5).
   *
   * <p>Reading the stage's own entry rather than {@link
   * MissionPerformanceReport#totalPropellantResidual()} makes the predicate exact even when the
   * sized stage is not the final active stage: the stack-wide total also counts whatever sits above
   * it (a payload kick motor's load, say), which would mask an emptied sized stage.
   *
   * <p>A negative {@code sizedStageIndex} (no λ-scaled stage) disables the floor, as does a stage
   * loaded with nothing. A report carrying no per-stage split falls back to the stack-wide total —
   * the pre-bilan-10 approximation, valid only when the sized stage is the final active one.
   *
   * @param report the mission performance report
   * @param sizedStageIndex the stack index of the λ-scaled top stage, or negative when there is none
   * @param floorRatio the minimum residual as a fraction of that stage's own load
   * @return {@code true} when the residual is at or above the floor
   */
  public static boolean residualSufficient(
      MissionPerformanceReport report, int sizedStageIndex, double floorRatio) {
    if (sizedStageIndex < 0) {
      return true; // no sized liquid stage to guard
    }
    return report
        .residualForStage(sizedStageIndex)
        .map(sp -> !(sp.loaded() > 0) || sp.residual() >= floorRatio * sp.loaded())
        .orElseGet(
            () -> {
              // No per-stage split available (hand-built report): fall back to the stack-wide total.
              double load = report.totalPropellantLoaded();
              return !(load > 0) || report.totalPropellantResidual() >= floorRatio * load;
            });
  }

  /** The load of the sized stage in {@code loads}, or 0 when no stage is scaled. */
  private double sizedStageLoad(double[] loads) {
    int index = sizedStageIndex();
    return index >= 0 ? loads[index] : 0.0;
  }

  /** The stack index of the sized (last λ-scaled) stage, or -1 when no stage is scaled. */
  private int sizedStageIndex() {
    for (int i = lambdaScaled.length - 1; i >= 0; i--) {
      if (lambdaScaled[i]) {
        return i;
      }
    }
    return -1;
  }

  private static OrbitInsertionObjective orbitInsertionObjective(MissionObjective objective) {
    if (objective instanceof OrbitInsertionObjective insertion) {
      return insertion;
    }
    throw new IllegalArgumentException(
        "MissionLoadEvaluator supports orbit-insertion objectives only, got " + objective);
  }
}
