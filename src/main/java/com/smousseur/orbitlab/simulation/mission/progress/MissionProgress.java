package com.smousseur.orbitlab.simulation.mission.progress;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live advancement of one mission computation: written by the optimization threads, read by the
 * JME render thread. One instance per computation, held by the {@code MissionEntry} for as long as
 * the computation lasts and dropped when it ends.
 *
 * <p><b>Why the queued state lives here and not in {@code MissionStatus}.</b> The orchestrator's
 * executor is single-threaded, so a second mission set to compute waits its turn while displaying
 * exactly what the running one displays. Distinguishing the two through the status enum would mean
 * teaching fifteen call sites a value that behaves identically to {@code COMPUTING} at every one of
 * them. Advancement is volatile, high-churn state; the mission's lifecycle status is not.
 *
 * <p><b>Threading.</b> {@link #onEvaluation()} is called from the parallel CMA-ES exploration
 * threads and is a single atomic increment. {@link #onProgress} is called only from the mission's
 * own optimization thread — the optimizers run their control flow there and only fan out inside
 * their objective function — so the composition below needs no lock, and the volatile publication
 * is what carries it to the render thread.
 */
public final class MissionProgress implements MissionProgressListener {

  /** Whether the computation is waiting for the optimizer thread or already running on it. */
  public enum State {
    QUEUED,
    RUNNING
  }

  private final AtomicLong evaluations = new AtomicLong();

  private volatile State state = State.QUEUED;
  private volatile ProgressPhase phase;
  private volatile long startedAtNanos;

  private int stage = 1;
  private int stageCount = 1;
  private int attempt = 1;
  // Read before the first AttemptStarted announces the real ceiling, which the optimizer does
  // microseconds after entering a stage — well below the display's refresh period.
  private int attemptCount = 1;
  private OptimizationStep step = OptimizationStep.EXPLORATION;

  /** Marks the computation as started and arms the elapsed-time clock. */
  public void start() {
    startedAtNanos = System.nanoTime();
    state = State.RUNNING;
  }

  /**
   * @return whether the computation is queued or running
   */
  public State state() {
    return state;
  }

  /**
   * @return how many objective function evaluations have been spent so far
   */
  public long evaluations() {
    return evaluations.get();
  }

  /**
   * @return where the computation currently is, empty until the first transition is reported
   */
  public Optional<ProgressPhase> phase() {
    return Optional.ofNullable(phase);
  }

  /**
   * @return seconds elapsed since the computation started running, 0 while it is still queued
   */
  public double elapsedSeconds() {
    long started = startedAtNanos;
    return started == 0L ? 0.0 : (System.nanoTime() - started) / 1e9;
  }

  @Override
  public void onProgress(MissionProgressEvent event) {
    switch (event) {
      case MissionProgressEvent.StageEntered e -> {
        stage = e.index();
        stageCount = e.count();
        attempt = 1;
        step = OptimizationStep.EXPLORATION;
        publishTrajectory();
      }
      case MissionProgressEvent.AttemptStarted e -> {
        attempt = e.attempt();
        attemptCount = e.count();
        step = OptimizationStep.EXPLORATION;
        publishTrajectory();
      }
      case MissionProgressEvent.StepStarted e -> {
        step = e.step();
        publishTrajectory();
      }
      case MissionProgressEvent.SizingAdvanced e ->
          phase = new ProgressPhase.Sizing(e.pass(), e.passCount(), e.load(), e.loadBudget());
    }
  }

  @Override
  public void onEvaluation() {
    evaluations.incrementAndGet();
  }

  private void publishTrajectory() {
    phase = new ProgressPhase.Trajectory(stage, stageCount, attempt, attemptCount, step);
  }
}
