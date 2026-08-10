package com.smousseur.orbitlab.simulation.mission.stage.ascent;

import com.smousseur.orbitlab.simulation.mission.detector.MinAltitudeTracker;
import org.orekit.propagation.SpacecraftState;

/**
 * How the gravity-turn optimization problem flies a candidate:
 *
 * <ul>
 *   <li>{@link AscentChainPropagation} — the three explicit phases flown through {@code
 *       StageChainRunner}, i.e. the very chain the ephemeris pass replays. <b>This is what every
 *       mission uses.</b>
 *   <li>{@link
 *       com.smousseur.orbitlab.simulation.mission.maneuver.GravityTurnManeuver#asPropagation} — the
 *       pre-split single propagator carrying burn 1, the jettison detector and burn 2. Kept as the
 *       numeric reference the migration's non-regression fixtures compare against (spec {@code
 *       docs/mission-stages/02-baseline-n2.md} §5).
 * </ul>
 *
 * <p>Injecting the strategy is what let the split land in two commits rather than one: the phases
 * and their optimize path were built and measured while the missions still flew the old stage, so
 * no single step changed both the structure and the trajectory.
 *
 * <p><b>Concurrency.</b> CMA-ES explores in parallel, so implementations must tolerate concurrent
 * {@link #propagate} calls and must report {@link #lastAltitudeTracker()} per thread.
 */
public interface AscentPropagation {

  /**
   * Flies one candidate from gravity-turn entry to MECO, returning a penalizing state (in practice
   * the entry state, so the cost function sees ~zero elapsed time) when the propagation fails.
   *
   * @param entryState the pre-kick state at gravity-turn entry
   * @param variables the raw CMA-ES variables (transitionTime, exponent)
   * @return the state at MECO, or a penalty state
   */
  SpacecraftState propagate(SpacecraftState entryState, double[] variables);

  /**
   * Returns the altitude tracker of the most recent {@link #propagate} on the calling thread, or
   * {@code null} if this thread has flown nothing yet. The cost function reads it to grade a
   * trajectory that dipped underground instead of returning a flat penalty.
   *
   * @return the tracker, or {@code null}
   */
  MinAltitudeTracker lastAltitudeTracker();
}
