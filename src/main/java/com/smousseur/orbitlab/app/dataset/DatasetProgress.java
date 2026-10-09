package com.smousseur.orbitlab.app.dataset;

import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Live advancement of one installation of the dataset: fed by its events on the installing thread,
 * read by the render thread every frame.
 *
 * <p>Every event replaces an immutable {@link Snapshot} published through a volatile reference, so
 * a reader takes a consistent picture without a lock. The rate is measured over a sliding window of
 * about five seconds of the phase's bytes; the time left is the bytes left divided by that rate,
 * and stays unknown until a rate is measured. Both are computed on events only: during a silence
 * they keep the value of the last event.
 *
 * <p>One instance follows one installation; a new attempt from the user is followed by a new one.
 */
public final class DatasetProgress implements DatasetInstallListener {

  private static final long WINDOW_NANOS = Duration.ofSeconds(5).toNanos();

  /**
   * Where an installation stands.
   *
   * @param phase the current phase, or {@code null} before the first one
   * @param file the current file, or {@code null} before the phase's first file
   * @param fileIndex the current file's rank in the phase, from 1; 0 before its first file
   * @param fileCount how many files the phase handles
   * @param fileBytes how many bytes of the current file are on disk and checked, kept ones included
   * @param phaseBytes how many bytes the phase has read or downloaded
   * @param phaseTotal how many bytes the phase has to read or download
   * @param bytesPerSecond the rate over the last seconds, 0 until one is measured
   * @param remaining the time left in the phase at that rate, or {@code null} until a rate is
   *     measured
   * @param lastFailure the last failed attempt on the current file, or {@code null} if none
   * @param outcome how the installation ended, or {@code null} while it runs
   */
  public record Snapshot(
      Phase phase,
      DatasetFile file,
      int fileIndex,
      int fileCount,
      long fileBytes,
      long phaseBytes,
      long phaseTotal,
      double bytesPerSecond,
      Duration remaining,
      AttemptFailed lastFailure,
      DatasetInstallOutcome outcome) {

    /**
     * Tells whether a phase has started.
     *
     * @return true when {@link #phase()} is not null
     */
    public boolean hasPhase() {
      return phase != null;
    }

    /**
     * Tells whether a file is being handled.
     *
     * @return true when {@link #file()} is not null
     */
    public boolean hasFile() {
      return file != null;
    }

    /**
     * Tells whether the time left is known.
     *
     * @return true when {@link #remaining()} is not null
     */
    public boolean hasRemaining() {
      return remaining != null;
    }

    /**
     * Tells whether an attempt on the current file has failed.
     *
     * @return true when {@link #lastFailure()} is not null
     */
    public boolean hasLastFailure() {
      return lastFailure != null;
    }

    /**
     * Tells whether the installation has ended.
     *
     * @return true when {@link #outcome()} is not null
     */
    public boolean finished() {
      return outcome != null;
    }

    /**
     * The share of the phase done.
     *
     * @return between 0 and 1; 0 for a phase with nothing to do
     */
    public double phaseFraction() {
      return phaseTotal <= 0 ? 0 : Math.min(1.0, (double) phaseBytes / phaseTotal);
    }
  }

  private static final Snapshot INITIAL =
      new Snapshot(null, null, 0, 0, 0, 0, 0, 0, null, null, null);

  private final LongSupplier nanoTime;

  /** Pairs of {time in nanoseconds, phase bytes}, oldest first. Touched by the installer only. */
  private final Deque<long[]> samples = new ArrayDeque<>();

  private volatile Snapshot snapshot = INITIAL;

  /** Creates a progress measured on the system's monotonic clock. */
  public DatasetProgress() {
    this(System::nanoTime);
  }

  /**
   * Creates a progress measured on another clock.
   *
   * @param nanoTime a monotonic clock in nanoseconds
   */
  public DatasetProgress(LongSupplier nanoTime) {
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
  }

  /**
   * The current state, safe to read from any thread.
   *
   * @return the last published snapshot
   */
  public Snapshot snapshot() {
    return snapshot;
  }

  @Override
  public void onEvent(DatasetInstallEvent event) {
    Snapshot s = snapshot;
    switch (event) {
      case DatasetInstallEvent.PhaseStarted e -> {
        samples.clear();
        addSample(0);
        snapshot =
            new Snapshot(e.phase(), null, 0, e.fileCount(), 0, 0, e.bytes(), 0, null, null, null);
      }
      case DatasetInstallEvent.FileStarted e ->
          snapshot =
              new Snapshot(
                  s.phase(),
                  e.file(),
                  e.index(),
                  e.count(),
                  e.presentBytes(),
                  s.phaseBytes(),
                  s.phaseTotal(),
                  s.bytesPerSecond(),
                  s.remaining(),
                  null,
                  null);
      case DatasetInstallEvent.BytesProcessed e -> {
        addSample(e.phaseBytes());
        snapshot = withRate(s, e.fileBytes(), e.phaseBytes(), s.lastFailure());
      }
      case DatasetInstallEvent.AttemptFailed e -> {
        addSample(s.phaseBytes());
        snapshot = withRate(s, s.fileBytes(), s.phaseBytes(), e);
      }
      case DatasetInstallEvent.FileCompleted e -> {
        // Nothing changes: the file's last bytes were reported just before.
      }
      case DatasetInstallEvent.Finished e ->
          snapshot =
              new Snapshot(
                  s.phase(),
                  s.file(),
                  s.fileIndex(),
                  s.fileCount(),
                  s.fileBytes(),
                  s.phaseBytes(),
                  s.phaseTotal(),
                  s.bytesPerSecond(),
                  s.remaining(),
                  s.lastFailure(),
                  e.outcome());
    }
  }

  private Snapshot withRate(
      Snapshot s, long fileBytes, long phaseBytes, AttemptFailed lastFailure) {
    double rate = rate();
    Duration remaining = null;
    if (rate > 0) {
      long left = Math.max(0, s.phaseTotal() - phaseBytes);
      remaining = Duration.ofNanos((long) (left / rate * 1e9));
    }
    return new Snapshot(
        s.phase(),
        s.file(),
        s.fileIndex(),
        s.fileCount(),
        fileBytes,
        phaseBytes,
        s.phaseTotal(),
        rate,
        remaining,
        lastFailure,
        null);
  }

  /**
   * Adds a sample, and keeps as the oldest one the last sample taken at least a window ago, so the
   * rate spans the whole window once the phase has lasted that long.
   */
  private void addSample(long phaseBytes) {
    long now = nanoTime.getAsLong();
    samples.addLast(new long[] {now, phaseBytes});
    long[] anchor = samples.removeFirst();
    while (samples.size() > 1 && samples.peekFirst()[0] <= now - WINDOW_NANOS) {
      anchor = samples.removeFirst();
    }
    samples.addFirst(anchor);
  }

  private double rate() {
    long[] first = samples.peekFirst();
    long[] last = samples.peekLast();
    if (first == null || last == null || last[0] <= first[0]) {
      return 0;
    }
    return Math.max(0, (last[1] - first[1]) / ((last[0] - first[0]) / 1e9));
  }
}
