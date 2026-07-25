package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import java.util.Objects;
import java.util.function.Function;

import com.smousseur.orbitlab.simulation.mission.runtime.MissionLoadEvaluator;
import com.smousseur.orbitlab.simulation.mission.runtime.MultiStageLoadOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.PropellantLoadOptimizer;
import org.orekit.time.AbsoluteDate;

/**
 * {@link MissionPlanner} that sizes the propellant before flying: it drives the coordinate-wise
 * load sweep ({@link MultiStageLoadOptimizer}), each evaluation a full mission optimization, and
 * reports the resolved per-stage scaling as {@link PropellantSizing}.
 *
 * <p>All the T3 plumbing is enclosed here — the mission builder, the heuristic loads, the scaling
 * mask, the launch epoch and the feasibility settings are assembled into the internal {@link
 * MissionLoadEvaluator} by {@link #plan()}, so the caller supplies inputs, not machinery.
 *
 * <p>An under-dotée mission (the heuristic loads themselves fail) has nothing to shrink; {@link
 * #plan()} raises {@link OrbitlabException} rather than returning a plan with no computation.
 */
public final class MinimizedLoadPlanner implements MissionPlanner {
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
   * Creates a planner with the spec-09 defaults, measuring feasibility against the mission's own
   * objective (the LEO case, whose recorded objective is the flown final orbit).
   *
   * @param missionBuilder assembles a fresh mission from a per-stage launcher load array
   * @param heuristicLoads the baseline per-stage loads (kg), same order as the launcher stages
   * @param lambdaScaled which stages carry their own λ ({@link
   *     PropellantLoadOptimizer#allVariableLoadMask})
   * @param launchEpoch the launch date the mission's initial state is built at
   */
  public MinimizedLoadPlanner(
      Function<double[], Mission> missionBuilder,
      double[] heuristicLoads,
      boolean[] lambdaScaled,
      AbsoluteDate launchEpoch) {
    this(
        missionBuilder,
        heuristicLoads,
        lambdaScaled,
        launchEpoch,
        MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS,
        42L,
        MissionLoadEvaluator.DEFAULT_OBJECTIVE_TOLERANCE_RATIO,
        MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        null);
  }

  /**
   * Creates a planner with explicit settings and an explicit feasibility objective, required for
   * missions whose recorded objective is not the flown final orbit (GEO records {@code (parking,
   * GEO)} while its end state is circular GEO).
   *
   * @param missionBuilder assembles a fresh mission from a per-stage launcher load array
   * @param heuristicLoads the baseline per-stage loads (kg), same order as the launcher stages
   * @param lambdaScaled which stages carry their own λ
   * @param launchEpoch the launch date the mission's initial state is built at
   * @param optimizerMaxEvaluations the inner CMA-ES evaluation budget per stage
   * @param seed the CMA-ES master seed, or {@code null} for non-deterministic
   * @param objectiveToleranceRatio the ± band on perigee/apogee the objective must land within
   * @param residualFloorRatio the minimum end-of-mission residual as a fraction of the sized
   *     stage's load
   * @param feasibilityObjective the orbit the terminal coast is measured against, or {@code null}
   *     to use the mission's own {@link OrbitInsertionObjective}
   */
  public MinimizedLoadPlanner(
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
    this.optimizerMaxEvaluations = optimizerMaxEvaluations;
    this.seed = seed;
    this.objectiveToleranceRatio = objectiveToleranceRatio;
    this.residualFloorRatio = residualFloorRatio;
    this.feasibilityObjective = feasibilityObjective;
  }

  @Override
  public MissionPlan plan() {
    MissionLoadEvaluator evaluator =
        new MissionLoadEvaluator(
            missionBuilder,
            heuristicLoads,
            lambdaScaled,
            launchEpoch,
            optimizerMaxEvaluations,
            seed,
            objectiveToleranceRatio,
            residualFloorRatio,
            feasibilityObjective);
    MultiStageLoadOptimizer.Result result =
        new MultiStageLoadOptimizer().minimize(evaluator::evaluate, lambdaScaled, heuristicLoads);
    if (!result.feasible()) {
      throw new OrbitlabException(
          "Propellant sizing infeasible: the heuristic loads themselves fail — mission under-dotée,"
              + " nothing to shrink");
    }
    return new MissionPlan(result.best().result(), PropellantSizing.from(result));
  }
}
