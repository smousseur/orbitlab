package com.smousseur.orbitlab.simulation.mission.runtime;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemeris;
import com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisPoint;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
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
 * <p><b>Feasibility.</b> The mission is feasible when the objective is met <em>and</em> the residual
 * floor is respected (spec 09 §1, §5):
 *
 * <ul>
 *   <li><b>objective</b> — the final coast orbit lands within {@code objectiveToleranceRatio} of the
 *       target perigee and apogee, measured from the ephemeris exactly as the mission optimization
 *       tests do (min/max altitude of the terminal {@code "Coasting"} stage);
 *   <li><b>residual</b> — the propellant still aboard at mission end is at least {@code
 *       residualFloorRatio} of the loaded propellant, so the sized (top liquid) stage is not flown
 *       bone-dry, which would be brittle under the un-modelled losses the optimizer cannot see.
 * </ul>
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

  /** Default residual floor: the sized stage must land with at least 1 % of its load (spec 09 §5). */
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

  /**
   * Creates an evaluator with the spec-09 defaults (±7 % objective, 1 % residual floor, 40 000 inner
   * evals, deterministic seed).
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
   * Creates an evaluator with explicit settings.
   *
   * @param missionBuilder assembles a fresh mission from a per-stage launcher load array
   * @param heuristicLoads the baseline per-stage loads (kg), same order as the launcher stages
   * @param lambdaScaled which stages the λ scaling applies to
   * @param launchEpoch the launch date the mission's initial state is built at
   * @param optimizerMaxEvaluations the inner CMA-ES evaluation budget per stage
   * @param seed the CMA-ES master seed, or {@code null} for non-deterministic
   * @param objectiveToleranceRatio the ± band on perigee/apogee the objective must land within
   * @param residualFloorRatio the minimum residual (fraction of loaded propellant) for feasibility
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

    OrbitInsertionObjective objective = orbitInsertionObjective(mission.getObjective());
    boolean objectiveMet =
        objectiveMet(result.ephemeris(), objective, objectiveToleranceRatio);
    boolean residualOk =
        residualSufficient(result.performanceReport(), residualFloorRatio);
    boolean feasible = objectiveMet && residualOk;

    logger.info(
        "λ={} evaluation: objectiveMet={}, residualRatio={} (floor {}), feasible={}",
        lambda,
        objectiveMet,
        result.performanceReport().residualRatio(),
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
   * Whether the propellant still aboard at mission end clears the residual floor — the sized stage
   * is not flown bone-dry (spec 09 §5). Uses {@link MissionPerformanceReport#residualRatio()}, the
   * residual of the final active (top liquid) stage that the λ scaling sizes.
   *
   * @param report the mission performance report
   * @param floorRatio the minimum residual, as a fraction of loaded propellant
   * @return {@code true} when the residual ratio is at or above the floor
   */
  public static boolean residualSufficient(
      MissionPerformanceReport report, double floorRatio) {
    return report.residualRatio() >= floorRatio;
  }

  private static OrbitInsertionObjective orbitInsertionObjective(MissionObjective objective) {
    if (objective instanceof OrbitInsertionObjective insertion) {
      return insertion;
    }
    throw new IllegalArgumentException(
        "MissionLoadEvaluator supports orbit-insertion objectives only, got " + objective);
  }
}
