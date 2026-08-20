package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.context.MissionEntry;
import com.smousseur.orbitlab.simulation.mission.objective.OrbitInsertionObjective;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgressListener;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionComputeResult;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionLoadEvaluator;
import com.smousseur.orbitlab.simulation.mission.runtime.PropellantLoadOptimizer;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import java.util.Objects;
import java.util.function.Function;
import org.hipparchus.util.FastMath;
import org.orekit.time.AbsoluteDate;

/**
 * Turns a {@link MissionEntry} into a computed {@link MissionPlan}, choosing the {@link
 * MissionPlanner} that matches the entry's {@link OptimizationType}. The stage composition is
 * already resolved upstream by {@link MissionComposer} (the entry holds the mission composed for
 * its mode); this class owns the orthogonal <em>load-handling</em> axis:
 *
 * <ul>
 *   <li>{@link OptimizationType#FAST} / {@link OptimizationType#BALANCED} — {@link
 *       FixedLoadPlanner}: fly the mission at its budgeted loads, a single CMA-ES pass. FAST flies
 *       the analytic composition, BALANCED the CMA-ES transfer; the planner is the same.
 *   <li>{@link OptimizationType#PRECISE} — {@link MinimizedLoadPlanner}: size the propellant first,
 *       wrapping many mission optimizations in the coordinate-wise load sweep. Requires a {@link
 *       MissionSpec} (to rebuild the mission per candidate load array); a legacy entry with no spec
 *       falls back to the fixed-load path.
 * </ul>
 */
public class MissionPlanOptimizer {
  private static final int MAX_EVALUATIONS = MissionLoadEvaluator.DEFAULT_OPTIMIZER_MAX_EVALUATIONS;

  /** Deterministic CMA-ES master seed, matching the legacy inline optimizer path. */
  private static final long SEED = 42L;

  /**
   * Feasibility half-band on the GEO radius (±50 km) the {@code MinimizedLoadPlanner} measures the
   * flown orbit against — the bar {@code GEOMissionOptimizationTest} asserts. The LEO ±7 % default
   * would accept an orbit thousands of km off GEO.
   */
  private static final double GEO_FEASIBILITY_TOLERANCE_M = 50_000.0;

  private final MissionEntry entry;
  private final AbsoluteDate launchEpoch;

  /** Progress sink carried down to whichever planner the mode selects, or {@code null}. */
  private final MissionProgressListener progress;

  /**
   * @param entry the mission entry to compute (its mission is already composed for its mode)
   * @param launchEpoch the launch date the mission's initial state is built at
   */
  public MissionPlanOptimizer(MissionEntry entry, AbsoluteDate launchEpoch) {
    this(entry, launchEpoch, null);
  }

  /**
   * @param entry the mission entry to compute (its mission is already composed for its mode)
   * @param launchEpoch the launch date the mission's initial state is built at
   * @param progress the sink the selected planner reports its advancement to, or {@code null}
   */
  public MissionPlanOptimizer(
      MissionEntry entry, AbsoluteDate launchEpoch, MissionProgressListener progress) {
    this.entry = Objects.requireNonNull(entry, "entry");
    this.launchEpoch = Objects.requireNonNull(launchEpoch, "launchEpoch");
    this.progress = progress;
  }

  /**
   * Runs the planner selected by the entry's optimization mode.
   *
   * @return the computed plan (always carrying a {@link MissionComputeResult})
   */
  public MissionPlan compute() {
    return planner().plan();
  }

  private MissionPlanner planner() {
    if (entry.getOptimizationType() == OptimizationType.PRECISE) {
      return entry.spec().map(this::minimizedLoadPlanner).orElseGet(this::fixedLoadPlanner);
    }
    return fixedLoadPlanner();
  }

  private MissionPlanner fixedLoadPlanner() {
    Mission mission = entry.mission();
    // MissionOptimizer reads mission.getCurrentState() as the launch epoch, so seed the launch
    // state before the planner runs (the optimizer re-records the launch date on the mission).
    mission.setCurrentState(mission.getInitialState(launchEpoch));
    return new FixedLoadPlanner(mission, MAX_EVALUATIONS, SEED, progress);
  }

  private MissionPlanner minimizedLoadPlanner(MissionSpec spec) {
    OptimizationType mode = entry.getOptimizationType();
    LauncherModel launcher = spec.configuration().launcher();
    double[] heuristicLoads = spec.configuration().propellantLoads();
    boolean[] lambdaScaled = PropellantLoadOptimizer.lambdaScaledMask(launcher);
    // Each evaluation rebuilds the mission (same composition as the entry's mode) at the candidate
    // launcher loads; the payload — and a GEO payload's fixed AKM — travels unchanged.
    Function<double[], Mission> missionBuilder =
        loads -> MissionComposer.compose(spec.withLauncherLoads(loads), mode);

    if (spec instanceof MissionSpec.Geo geo) {
      // GEO's recorded objective is (parking, GEO); feasibility must be measured against the flown
      // circular GEO orbit, at the ±50 km bar rather than the LEO default.
      OrbitInsertionObjective feasibility =
          OrbitInsertionObjective.circular(
              SolarSystemBody.EARTH,
              geo.targetAltitude(),
              FastMath.toRadians(geo.finalInclination()));
      return new MinimizedLoadPlanner(
          missionBuilder,
          heuristicLoads,
          lambdaScaled,
          launchEpoch,
          MAX_EVALUATIONS,
          SEED,
          GEO_FEASIBILITY_TOLERANCE_M / geo.targetAltitude(),
          MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
          feasibility,
          progress);
    }
    // LEO records the flown final orbit as its objective, so the mission's own objective and the
    // default tolerance apply.
    return new MinimizedLoadPlanner(
        missionBuilder,
        heuristicLoads,
        lambdaScaled,
        launchEpoch,
        MAX_EVALUATIONS,
        SEED,
        MissionLoadEvaluator.DEFAULT_OBJECTIVE_TOLERANCE_RATIO,
        MissionLoadEvaluator.DEFAULT_RESIDUAL_FLOOR_RATIO,
        null,
        progress);
  }
}
