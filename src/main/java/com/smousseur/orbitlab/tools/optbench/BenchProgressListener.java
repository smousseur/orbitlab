package com.smousseur.orbitlab.tools.optbench;

import com.smousseur.orbitlab.simulation.mission.progress.MissionProgressEvent;
import com.smousseur.orbitlab.simulation.mission.progress.MissionProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@link MissionProgressListener} the optimization benchmark ({@code OPT-1 / L0}) attaches to a
 * {@code MissionPlanOptimizer}. It records, per cell, a timestamped timeline of the cold progress
 * events and a total count of the hot evaluation callbacks — the two halves the phase breakdown is
 * reconstructed from, without any change to {@code src/main}.
 *
 * <p><b>Thread safety follows the listener contract.</b> {@link #onEvaluation()} is called tens of
 * thousands of times from the parallel exploration threads, so it only touches an {@link
 * AtomicLong}. {@link #onProgress} is cold and its timeline list is synchronized; {@code
 * MeasuredLoadPlanner} calls it from the single calc thread, but a listener that accumulates must
 * be safe for concurrent use regardless.
 */
final class BenchProgressListener implements MissionProgressListener {

  /** One recorded progress event, dated relative to the cell start. */
  record TimelineEntry(double seconds, String description) {}

  private final AtomicLong evaluations = new AtomicLong();
  private final List<TimelineEntry> timeline = new ArrayList<>();
  private volatile long startNanos = System.nanoTime();

  /** Flights of a sizing loop, counted as the entries into the first optimizable stage. */
  private int flights;

  /**
   * Clears the accumulated state and restarts the clock. Called before each cell so one listener
   * instance serves the whole run.
   */
  synchronized void reset() {
    evaluations.set(0);
    timeline.clear();
    flights = 0;
    startNanos = System.nanoTime();
  }

  long evaluations() {
    return evaluations.get();
  }

  synchronized int flights() {
    return flights;
  }

  synchronized List<TimelineEntry> timeline() {
    return List.copyOf(timeline);
  }

  @Override
  public void onEvaluation() {
    evaluations.incrementAndGet();
  }

  @Override
  public synchronized void onProgress(MissionProgressEvent event) {
    double seconds = (System.nanoTime() - startNanos) / 1e9;
    timeline.add(new TimelineEntry(seconds, describe(event)));
    // The entry into stage 1 marks the start of one flight; a flight with several optimizable
    // stages (BALANCED transfer) still enters stage 1 exactly once.
    if (event instanceof MissionProgressEvent.StageEntered stage && stage.index() == 1) {
      flights++;
    }
  }

  private static String describe(MissionProgressEvent event) {
    return switch (event) {
      case MissionProgressEvent.StageEntered e ->
          String.format(Locale.ROOT, "StageEntered %d/%d", e.index(), e.count());
      case MissionProgressEvent.AttemptStarted e ->
          String.format(Locale.ROOT, "AttemptStarted %d/%d", e.attempt(), e.count());
      case MissionProgressEvent.StepStarted e -> "StepStarted " + e.step();
      case MissionProgressEvent.SizingAdvanced e ->
          String.format(
              Locale.ROOT,
              "SizingAdvanced pass %d/%d, load %d/%d",
              e.pass(),
              e.passCount(),
              e.load(),
              e.loadBudget());
    };
  }
}
