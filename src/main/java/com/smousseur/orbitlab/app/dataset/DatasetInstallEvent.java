package com.smousseur.orbitlab.app.dataset;

import java.time.Duration;
import java.util.Objects;

/**
 * What an installation of the dataset reports while it runs, in the order it happens. Every event
 * is issued on the thread running the installation.
 */
public sealed interface DatasetInstallEvent {

  /** The two kinds of work an installation does, each reported as a phase of its own. */
  enum Phase {
    /** Reading files already on disk through SHA-256, to adopt them without downloading them. */
    VERIFYING,
    /** Downloading what is missing from the release. */
    DOWNLOADING
  }

  /**
   * A phase begins. A phase with nothing to do is not reported.
   *
   * @param phase which phase
   * @param fileCount how many files it handles
   * @param bytes how many bytes it has to read or download
   */
  record PhaseStarted(Phase phase, int fileCount, long bytes) implements DatasetInstallEvent {

    /** Rejects a missing phase. */
    public PhaseStarted {
      Objects.requireNonNull(phase, "phase");
    }
  }

  /**
   * A file begins.
   *
   * @param file the file
   * @param index its rank in the phase, from 1
   * @param count how many files the phase handles
   * @param presentBytes how many of its bytes are already on disk and kept: what a partial download
   *     left, and that the phase does not count; 0 when verifying
   */
  record FileStarted(DatasetFile file, int index, int count, long presentBytes)
      implements DatasetInstallEvent {

    /** Rejects a missing file. */
    public FileStarted {
      Objects.requireNonNull(file, "file");
    }
  }

  /**
   * Bytes were read or downloaded. Issued at most every quarter of a second for a file, and once
   * more at its end.
   *
   * @param file the file
   * @param fileBytes how many bytes of the file are on disk and checked so far, kept ones included
   * @param phaseBytes how many bytes the phase has read or downloaded so far, kept ones excluded
   */
  record BytesProcessed(DatasetFile file, long fileBytes, long phaseBytes)
      implements DatasetInstallEvent {

    /** Rejects a missing file. */
    public BytesProcessed {
      Objects.requireNonNull(file, "file");
    }
  }

  /**
   * An attempt to download a piece failed, and another one follows.
   *
   * @param file the file
   * @param cause what kind of failure it was
   * @param detail what actually happened
   * @param failuresWithoutProgress how many attempts in a row have failed without the download
   *     moving forward, this one included; 0 when this one moved it forward before failing
   * @param retryIn how long the installation waits before the next attempt
   */
  record AttemptFailed(
      DatasetFile file,
      DatasetFailureCause cause,
      String detail,
      int failuresWithoutProgress,
      Duration retryIn)
      implements DatasetInstallEvent {

    /** Rejects missing components. */
    public AttemptFailed {
      Objects.requireNonNull(file, "file");
      Objects.requireNonNull(cause, "cause");
      Objects.requireNonNull(detail, "detail");
      Objects.requireNonNull(retryIn, "retryIn");
    }
  }

  /**
   * A file is complete and matches its fingerprint.
   *
   * @param file the file
   */
  record FileCompleted(DatasetFile file) implements DatasetInstallEvent {

    /** Rejects a missing file. */
    public FileCompleted {
      Objects.requireNonNull(file, "file");
    }
  }

  /**
   * The installation ended; always the last event.
   *
   * @param outcome how it ended
   */
  record Finished(DatasetInstallOutcome outcome) implements DatasetInstallEvent {

    /** Rejects a missing outcome. */
    public Finished {
      Objects.requireNonNull(outcome, "outcome");
    }
  }
}
