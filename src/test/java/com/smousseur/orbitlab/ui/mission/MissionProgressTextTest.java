package com.smousseur.orbitlab.ui.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.progress.MissionProgress;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgressEvent;
import com.smousseur.orbitlab.simulation.mission.progress.OptimizationStep;
import org.junit.jupiter.api.Test;

/** Unit tests for the strings the panel shows while a mission is being computed. */
class MissionProgressTextTest {

  private static MissionProgress running() {
    MissionProgress progress = new MissionProgress();
    progress.start();
    return progress;
  }

  @Test
  void queuedReadsAsQueuedEverywhere() {
    MissionProgress progress = new MissionProgress();

    assertEquals("QUEUED", MissionProgressText.statusCell(progress));
    assertEquals("QUEUED", MissionProgressText.detailLine(progress));
  }

  @Test
  void aQueuedMissionSaysNothingOfTheStageItWillReach() {
    MissionProgress progress = new MissionProgress();
    progress.onProgress(new MissionProgressEvent.StageEntered(1, 2));

    assertEquals("QUEUED", MissionProgressText.statusCell(progress));
  }

  @Test
  void runningWithNoTransitionYetFallsBackOnTheOldText() {
    MissionProgress progress = running();

    assertEquals("Computing...", MissionProgressText.statusCell(progress));
    assertTrue(MissionProgressText.detailLine(progress).startsWith("Computing..."));
  }

  @Test
  void trajectoryStatusCellShowsTheStageAlone() {
    MissionProgress progress = running();
    progress.onProgress(new MissionProgressEvent.StageEntered(1, 2));
    progress.onProgress(new MissionProgressEvent.AttemptStarted(1, 3));

    assertEquals("1/2", MissionProgressText.statusCell(progress));
  }

  @Test
  void trajectoryDetailLineShowsStageAttemptStepAndCount() {
    MissionProgress progress = running();
    progress.onProgress(new MissionProgressEvent.StageEntered(1, 2));
    progress.onProgress(new MissionProgressEvent.AttemptStarted(1, 3));
    progress.onProgress(new MissionProgressEvent.StepStarted(OptimizationStep.EXPLORATION));
    for (int i = 0; i < 12_480; i++) {
      progress.onEvaluation();
    }

    String line = MissionProgressText.detailLine(progress);

    assertTrue(line.startsWith("1/2  attempt 1/3  exploration"), line);
    assertTrue(line.contains("12 480 evals"), line);
  }

  @Test
  void sizingReplacesTheStageWithTheSweepPosition() {
    MissionProgress progress = running();
    progress.onProgress(new MissionProgressEvent.SizingAdvanced(2, 3, 7, 45));

    assertEquals("LOAD 7/45", MissionProgressText.statusCell(progress));
    assertTrue(MissionProgressText.detailLine(progress).startsWith("LOAD 7/45  pass 2/3"), "sizing");
  }

  @Test
  void thousandsAreGroupedWithSpaces() {
    assertEquals("0", MissionProgressText.grouped(0));
    assertEquals("7", MissionProgressText.grouped(7));
    assertEquals("999", MissionProgressText.grouped(999));
    assertEquals("1 000", MissionProgressText.grouped(1_000));
    assertEquals("12 480", MissionProgressText.grouped(12_480));
    assertEquals("123 456", MissionProgressText.grouped(123_456));
    assertEquals("1 234 567", MissionProgressText.grouped(1_234_567));
  }

  @Test
  void everyLineStaysWithinTheBundledAsciiGlyphs() {
    MissionProgress progress = running();
    progress.onProgress(new MissionProgressEvent.StageEntered(1, 2));
    progress.onProgress(new MissionProgressEvent.AttemptStarted(2, 3));
    progress.onProgress(new MissionProgressEvent.StepStarted(OptimizationStep.REFINEMENT));

    for (String text :
        new String[] {
          MissionProgressText.statusCell(progress), MissionProgressText.detailLine(progress)
        }) {
      for (char c : text.toCharArray()) {
        assertTrue(c >= 32 && c < 127, "non-ASCII glyph " + (int) c + " in \"" + text + "\"");
      }
    }
  }
}
