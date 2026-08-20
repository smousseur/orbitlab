package com.smousseur.orbitlab.ui.mission;

import com.smousseur.orbitlab.simulation.mission.progress.MissionProgress;
import com.smousseur.orbitlab.simulation.mission.progress.OptimizationStep;
import com.smousseur.orbitlab.simulation.mission.progress.ProgressPhase;
import java.util.Locale;

/**
 * Every string the panel shows while a mission is being computed.
 *
 * <p>Same reason to exist as {@link MissionResultText}, and the same constraint: the widgets need a
 * JME context and cannot be unit-tested, while the decisions that can be wrong — which level of the
 * optimization is shown, how a count is grouped, what a queued mission reads as — live here and are
 * covered.
 *
 * <p><b>ASCII only</b>, like every other HUD string: the bundled bitmap fonts carry glyphs 32-127.
 * Thousands are grouped with a plain space, there being no narrow no-break space to group them
 * with.
 */
public final class MissionProgressText {

  /** What the status cell and the result line show for a computation that has not started. */
  public static final String QUEUED = "QUEUED";

  /**
   * What a running computation shows before its first transition is reported — a window of
   * microseconds, and the text the panel displayed for the whole computation before this chantier.
   */
  public static final String STARTING = "Computing...";

  private MissionProgressText() {}

  /**
   * The short form for the list's status column, e.g. {@code "1/2"} or {@code "LOAD 7/45"}.
   *
   * @param progress the live progress
   * @return the cell text, never null
   */
  public static String statusCell(MissionProgress progress) {
    if (progress.state() == MissionProgress.State.QUEUED) {
      return QUEUED;
    }
    return progress
        .phase()
        .map(
            phase ->
                switch (phase) {
                  case ProgressPhase.Trajectory t ->
                      String.format(Locale.ROOT, "%d/%d", t.stage(), t.stageCount());
                  case ProgressPhase.Sizing s ->
                      String.format(Locale.ROOT, "LOAD %d/%d", s.load(), s.loadBudget());
                })
        .orElse(STARTING);
  }

  /**
   * The detailed line for the footer of the selected mission, e.g. {@code "1/2  attempt 1/3
   * exploration   12 480 evals   0:42"}.
   *
   * @param progress the live progress
   * @return the line, never null
   */
  public static String detailLine(MissionProgress progress) {
    if (progress.state() == MissionProgress.State.QUEUED) {
      return QUEUED;
    }
    String tail =
        String.format(
            Locale.ROOT,
            "%s evals   %s",
            grouped(progress.evaluations()),
            MissionResultText.formatDuration(progress.elapsedSeconds()));
    return progress
        .phase()
        .map(
            phase ->
                switch (phase) {
                  case ProgressPhase.Trajectory t ->
                      String.format(
                          Locale.ROOT,
                          "%d/%d  attempt %d/%d  %s   %s",
                          t.stage(),
                          t.stageCount(),
                          t.attempt(),
                          t.attemptCount(),
                          stepLabel(t.step()),
                          tail);
                  case ProgressPhase.Sizing s ->
                      String.format(
                          Locale.ROOT,
                          "LOAD %d/%d  pass %d/%d   %s",
                          s.load(),
                          s.loadBudget(),
                          s.pass(),
                          s.passCount(),
                          tail);
                })
        .orElse(STARTING + "   " + tail);
  }

  /**
   * A count with its thousands separated by spaces, e.g. {@code "12 480"}.
   *
   * @param value the count, assumed non-negative
   * @return the grouped digits
   */
  public static String grouped(long value) {
    String digits = Long.toString(value);
    StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
    int lead = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
    out.append(digits, 0, lead);
    for (int i = lead; i < digits.length(); i += 3) {
      out.append(' ').append(digits, i, i + 3);
    }
    return out.toString();
  }

  private static String stepLabel(OptimizationStep step) {
    return switch (step) {
      case EXPLORATION -> "exploration";
      case REFINEMENT -> "refinement";
    };
  }
}
