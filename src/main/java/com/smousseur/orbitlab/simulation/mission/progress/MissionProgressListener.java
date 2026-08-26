package com.smousseur.orbitlab.simulation.mission.progress;

/**
 * Sink the optimization chain reports its advancement to. Handed down as a nullable constructor
 * parameter from the orchestrator to {@code CMAESRunExecutor}, on the pattern of {@code
 * StageChainRunner.StageListener}: everything mutable lives in the caller's listener, so a listener
 * that accumulates must be safe for concurrent use.
 *
 * <p>The two methods sit on opposite sides of the cost divide, which is why they are two:
 *
 * <ul>
 *   <li>{@link #onProgress} is cold — a few dozen calls over a whole mission — and carries a sealed
 *       event.
 *   <li>{@link #onEvaluation} is hot — tens of thousands of calls per stage, issued from the
 *       parallel exploration threads — and must neither allocate nor block.
 * </ul>
 */
public interface MissionProgressListener {

  /**
   * Reports a transition in the optimization.
   *
   * @param event what just happened
   */
  void onProgress(MissionProgressEvent event);

  /** Reports one objective function evaluation. Called from arbitrary optimizer threads. */
  void onEvaluation();

  /**
   * Wraps a listener so that only its evaluation counting survives, transitions being dropped.
   *
   * <p>This is what the propellant load sweep hands to the mission optimizations it wraps: each of
   * its evaluations runs a full one, so their stage and attempt transitions would recycle up to a
   * hundred and thirty-five times under a sweep position that is the only monotone reading
   * available. The evaluation count, on the other hand, is meaningful cumulated across them.
   *
   * @param delegate the listener to forward evaluations to, or {@code null}
   * @return a listener forwarding only {@link #onEvaluation()}, or {@code null} if {@code delegate}
   *     is null
   */
  static MissionProgressListener evaluationsOnly(MissionProgressListener delegate) {
    if (delegate == null) {
      return null;
    }
    return new MissionProgressListener() {
      @Override
      public void onProgress(MissionProgressEvent event) {}

      @Override
      public void onEvaluation() {
        delegate.onEvaluation();
      }
    };
  }
}
