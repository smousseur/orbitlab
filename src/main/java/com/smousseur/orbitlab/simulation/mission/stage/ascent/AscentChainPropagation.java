package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.Mission;
import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver;
import com.smousseur.orbitlab.simulation.mission.runtime.StageChainRunner;
import java.util.Objects;
import org.orekit.propagation.SpacecraftState;

/**
 * Flies a gravity-turn candidate as the three explicit ascent phases, through the very {@link
 * StageChainRunner} the ephemeris pass uses.
 *
 * <p><b>This is the point of the split</b> (spec {@code
 * specs/mission-stages/01-separations-implicites.md} §5.4). As long as the optimize pass built one
 * propagator for the whole turn while the ephemeris pass walked stage by stage, the two passes saw
 * different sequences of integrator restarts and were free to drift apart — the divergence closed
 * by bilan 11 §3.9. One traversal, used by both, cannot drift.
 *
 * <p><b>Nothing is shared between evaluations.</b> CMA-ES explores in parallel, so each {@link
 * #propagate} builds its own plan reference, its own three phases and its own altitude tracker, and
 * only reads immutable state from the mission (its vehicle). The tracker is handed back per thread,
 * the same contract the single-propagator path used.
 */
public final class AscentChainPropagation implements AscentPropagation {

  private final GravityTurnFirstBurnStage firstBurn;
  private final GravityTurnManeuver maneuver;
  private final Mission mission;

  // Read by GravityTurnProblem#computeCost on the thread that just propagated, exactly as
  // GravityTurnManeuver#lastAltitudeTracker is on the single-propagator path.
  private final ThreadLocal<MinAltitudeTracker> lastAltitudeTracker = new ThreadLocal<>();

  /**
   * Creates the chain-based propagation of one gravity-turn problem.
   *
   * @param firstBurn the phase owning the ascent; used as the template of the per-evaluation chain
   * @param maneuver the maneuver decoding the CMA-ES variables into a dated plan
   * @param mission the parent mission, read for its vehicle
   */
  public AscentChainPropagation(
      GravityTurnFirstBurnStage firstBurn, GravityTurnManeuver maneuver, Mission mission) {
    this.firstBurn = Objects.requireNonNull(firstBurn, "firstBurn");
    this.maneuver = Objects.requireNonNull(maneuver, "maneuver");
    this.mission = Objects.requireNonNull(mission, "mission");
  }

  @Override
  public SpacecraftState propagate(SpacecraftState entryState, double[] variables) {
    MinAltitudeTracker tracker = new MinAltitudeTracker(0.0, Double.POSITIVE_INFINITY);
    lastAltitudeTracker.set(tracker);

    AscentPlanRef planRef = new AscentPlanRef();
    // The plan is derived from the pre-kick entry state: the pitch kick preserves the date, so the
    // phase applying it later lands on exactly these dates.
    planRef.set(maneuver.plan(entryState, variables));

    return StageChainRunner.plain()
        .run(firstBurn.optimizationChain(planRef, tracker), entryState, mission);
  }

  @Override
  public MinAltitudeTracker lastAltitudeTracker() {
    return lastAltitudeTracker.get();
  }
}
