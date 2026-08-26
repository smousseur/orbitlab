package com.smousseur.orbitlab.simulation.mission.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the assembly of a computation's advancement. No JME and no optimizer: the events
 * are pushed by hand, the way {@code AppMenuModelTest} exercises the menu model.
 */
class MissionProgressTest {

  @Test
  void startsQueuedWithNoPhaseAndNoElapsedTime() {
    MissionProgress progress = new MissionProgress();

    assertEquals(MissionProgress.State.QUEUED, progress.state());
    assertTrue(progress.phase().isEmpty());
    assertEquals(0L, progress.evaluations());
    assertEquals(0.0, progress.elapsedSeconds());
  }

  @Test
  void startingArmsTheClockAndSwitchesToRunning() {
    MissionProgress progress = new MissionProgress();

    progress.start();

    assertEquals(MissionProgress.State.RUNNING, progress.state());
    assertTrue(progress.elapsedSeconds() >= 0.0);
  }

  @Test
  void stageThenAttemptThenStepComposeOneTrajectoryPhase() {
    MissionProgress progress = new MissionProgress();

    progress.onProgress(new MissionProgressEvent.StageEntered(2, 2));
    progress.onProgress(new MissionProgressEvent.AttemptStarted(3, 3));
    progress.onProgress(new MissionProgressEvent.StepStarted(OptimizationStep.REFINEMENT));

    ProgressPhase.Trajectory phase =
        assertInstanceOf(ProgressPhase.Trajectory.class, progress.phase().orElseThrow());
    assertEquals(2, phase.stage());
    assertEquals(2, phase.stageCount());
    assertEquals(3, phase.attempt());
    assertEquals(3, phase.attemptCount());
    assertEquals(OptimizationStep.REFINEMENT, phase.step());
  }

  @Test
  void enteringAStageRewindsTheAttemptAndTheStep() {
    MissionProgress progress = new MissionProgress();

    progress.onProgress(new MissionProgressEvent.StageEntered(1, 2));
    progress.onProgress(new MissionProgressEvent.AttemptStarted(3, 3));
    progress.onProgress(new MissionProgressEvent.StepStarted(OptimizationStep.REFINEMENT));
    progress.onProgress(new MissionProgressEvent.StageEntered(2, 2));

    ProgressPhase.Trajectory phase =
        assertInstanceOf(ProgressPhase.Trajectory.class, progress.phase().orElseThrow());
    assertEquals(2, phase.stage());
    assertEquals(1, phase.attempt(), "a new stage starts at its first attempt");
    assertEquals(OptimizationStep.EXPLORATION, phase.step());
    assertEquals(3, phase.attemptCount(), "the ceiling already announced still holds");
  }

  @Test
  void sizingReplacesTheTrajectoryFormRatherThanNestingUnderIt() {
    MissionProgress progress = new MissionProgress();

    progress.onProgress(new MissionProgressEvent.StageEntered(1, 2));
    progress.onProgress(new MissionProgressEvent.SizingAdvanced(2, 3, 7, 45));

    ProgressPhase.Sizing phase =
        assertInstanceOf(ProgressPhase.Sizing.class, progress.phase().orElseThrow());
    assertEquals(2, phase.pass());
    assertEquals(3, phase.passCount());
    assertEquals(7, phase.load());
    assertEquals(45, phase.loadBudget());
  }

  @Test
  void malformedTransitionsAreRejectedAtTheEvent() {
    assertThrows(IllegalArgumentException.class, () -> new MissionProgressEvent.StageEntered(3, 2));
    assertThrows(
        IllegalArgumentException.class, () -> new MissionProgressEvent.AttemptStarted(0, 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MissionProgressEvent.SizingAdvanced(1, 3, -1, 45));
  }

  @Test
  void evaluationCountIsExactUnderConcurrentIncrements() throws Exception {
    int threads = 8;
    int perThread = 20_000;
    MissionProgress progress = new MissionProgress();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

    try {
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < perThread; i++) {
                    progress.onEvaluation();
                  }
                  return null;
                }));
      }
      start.countDown();
      for (java.util.concurrent.Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertEquals((long) threads * perThread, progress.evaluations());
  }

  @Test
  void evaluationsOnlyDropsTransitionsAndKeepsCounting() {
    MissionProgress progress = new MissionProgress();
    MissionProgressListener inner = MissionProgressListener.evaluationsOnly(progress);

    inner.onProgress(new MissionProgressEvent.StageEntered(1, 2));
    inner.onEvaluation();
    inner.onEvaluation();

    assertTrue(progress.phase().isEmpty(), "an inner optimization must not publish its stages");
    assertEquals(2L, progress.evaluations());
  }
}
