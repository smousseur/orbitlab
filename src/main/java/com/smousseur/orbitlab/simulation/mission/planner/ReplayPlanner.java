package com.smousseur.orbitlab.simulation.mission.planner;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.OptimizationType;
import com.smousseur.orbitlab.simulation.mission.operation.MissionComposer;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgressListener;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer;
import com.smousseur.orbitlab.simulation.mission.runtime.MissionSolutions;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * The third planner, beside {@link FixedLoadPlanner} and {@link MinimizedLoadPlanner}: it flies a
 * mission whose solutions are already known, one propagation per stage instead of N (spec {@code
 * docs/scenario/01-persistance-missions.md} §5).
 *
 * <p>What it buys is time, not reproducibility — the CMA-ES seed is a hard-coded {@code 42L} and
 * determinism was already acquired. What it costs is nothing in fidelity: the vectors are flown
 * through the very problems that produced them.
 *
 * <p>It knows nothing about files. The optimization core does not depend on the scenario format;
 * this planner takes a {@link MissionSolutions} and would serve just as well a batch mode or any
 * other caller with a trajectory to re-fly.
 */
public final class ReplayPlanner implements MissionPlanner {

  private final Mission mission;
  private final MissionSpec spec;
  private final OptimizationType mode;
  private final MissionSolutions solutions;
  private final AbsoluteDate launchEpoch;
  private final int maxEvaluations;
  private final Long seed;
  private final MissionProgressListener progress;

  /**
   * @param mission the mission as composed at its budgeted loads
   * @param spec the spec it was composed from, required only when the solutions carry flown loads
   * @param mode the optimization mode the composition matches
   * @param solutions the vectors to fly, and the loads to fly them at
   * @param launchEpoch the launch date the initial state is built at
   * @param maxEvaluations the CMA-ES budget, unused while every stage is replayed
   * @param seed the CMA-ES master seed, likewise unused while every stage is replayed
   * @param progress the sink, or {@code null}
   */
  public ReplayPlanner(
      Mission mission,
      MissionSpec spec,
      OptimizationType mode,
      MissionSolutions solutions,
      AbsoluteDate launchEpoch,
      int maxEvaluations,
      Long seed,
      MissionProgressListener progress) {
    this.mission = Objects.requireNonNull(mission, "mission");
    this.spec = spec;
    this.mode = Objects.requireNonNull(mode, "mode");
    this.solutions = Objects.requireNonNull(solutions, "solutions");
    this.launchEpoch = Objects.requireNonNull(launchEpoch, "launchEpoch");
    this.maxEvaluations = maxEvaluations;
    this.seed = seed;
    this.progress = progress;
    if (solutions.hasLauncherLoads() && spec == null) {
      throw new IllegalArgumentException("Flown launcher loads need a spec to be applied to");
    }
  }

  @Override
  public MissionPlan plan() {
    Mission flown = flownMission();
    // MissionOptimizer reads mission.getCurrentState() as the launch epoch, exactly as the
    // fixed-load path does.
    flown.setCurrentState(flown.getInitialState(launchEpoch));
    return new MissionPlan(
        new MissionOptimizer(flown, maxEvaluations, seed, progress, solutions).optimize());
  }

  /**
   * The mission to fly: the one composed upstream, unless a sizing sweep had searched for its
   * loads.
   *
   * <p>The loads are taken from the solutions <b>as they are</b> — absolute kilograms, no
   * multiplication here (§2.3). That is the whole point of persisting the product rather than the
   * scale factors: what flies is the vehicle that flew, not whatever today's {@code
   * PropellantBudget} would rebuild under the same λ.
   */
  private Mission flownMission() {
    if (!solutions.hasLauncherLoads()) {
      return mission;
    }
    return MissionComposer.compose(spec.withLauncherLoads(solutions.launcherLoads()), mode);
  }
}
