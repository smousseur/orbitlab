package com.smousseur.orbitlab.app.dataset;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.BytesProcessed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.FileStarted;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Finished;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.PhaseStarted;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The readable state of an installation, driven by events on an injected clock. */
class DatasetProgressTest {

  private static final long SECOND = 1_000_000_000L;
  private static final String SHA = "0".repeat(64);
  private static final DatasetFile FIRST =
      new DatasetFile("ephemeris/A.bin", 600, SHA, List.of(new DatasetPiece("A.bin", 600, SHA)));
  private static final DatasetFile SECOND_FILE =
      new DatasetFile("ephemeris/B.bin", 500, SHA, List.of(new DatasetPiece("B.bin", 500, SHA)));

  private final AtomicLong now = new AtomicLong(42 * SECOND);
  private final DatasetProgress progress = new DatasetProgress(now::get);

  private void at(double seconds) {
    now.set(42 * SECOND + (long) (seconds * SECOND));
  }

  @Test
  void startsWithNothingKnown() {
    DatasetProgress.Snapshot s = progress.snapshot();

    assertFalse(s.hasPhase());
    assertFalse(s.hasFile());
    assertFalse(s.hasRemaining());
    assertFalse(s.finished());
    assertEquals(0, s.phaseFraction());
  }

  @Test
  void followsAPhaseFileByFile() {
    progress.onEvent(new PhaseStarted(Phase.DOWNLOADING, 2, 1000));
    progress.onEvent(new FileStarted(FIRST, 1, 2, 100));

    DatasetProgress.Snapshot s = progress.snapshot();
    assertEquals(Phase.DOWNLOADING, s.phase());
    assertEquals(FIRST, s.file());
    assertEquals(1, s.fileIndex());
    assertEquals(2, s.fileCount());
    assertEquals(100, s.fileBytes(), "the kept bytes of a resumed file");
    assertEquals(0, s.phaseBytes());
    assertEquals(1000, s.phaseTotal());
    assertFalse(s.hasRemaining(), "no rate measured yet");

    at(1);
    progress.onEvent(new BytesProcessed(FIRST, 600, 500));
    progress.onEvent(new FileStarted(SECOND_FILE, 2, 2, 0));

    s = progress.snapshot();
    assertEquals(SECOND_FILE, s.file());
    assertEquals(2, s.fileIndex());
    assertEquals(0, s.fileBytes());
    assertEquals(500, s.phaseBytes());
    assertEquals(0.5, s.phaseFraction(), 1e-12);
  }

  @Test
  void measuresTheRateAndTheTimeLeft() {
    progress.onEvent(new PhaseStarted(Phase.DOWNLOADING, 1, 1000));
    progress.onEvent(new FileStarted(FIRST, 1, 1, 0));

    at(1);
    progress.onEvent(new BytesProcessed(FIRST, 200, 200));
    DatasetProgress.Snapshot s = progress.snapshot();
    assertEquals(200, s.bytesPerSecond(), 1e-9);
    assertEquals(Duration.ofSeconds(4), s.remaining());

    at(2);
    progress.onEvent(new BytesProcessed(FIRST, 600, 600));
    s = progress.snapshot();
    assertEquals(300, s.bytesPerSecond(), 1e-9);
    assertEquals(400 / 300.0, s.remaining().toNanos() / 1e9, 1e-6);
  }

  @Test
  void measuresTheRateOverTheLastFiveSecondsOnly() {
    progress.onEvent(new PhaseStarted(Phase.DOWNLOADING, 1, 1000));
    at(1);
    progress.onEvent(new BytesProcessed(FIRST, 200, 200));
    at(2);
    progress.onEvent(new BytesProcessed(FIRST, 600, 600));
    at(7);
    progress.onEvent(new BytesProcessed(FIRST, 900, 900));

    DatasetProgress.Snapshot s = progress.snapshot();
    assertEquals((900 - 600) / 5.0, s.bytesPerSecond(), 1e-9, "since the sample at 2 s");
    assertEquals(100 / 60.0, s.remaining().toNanos() / 1e9, 1e-6);
  }

  @Test
  void keepsTheLastFailureOfTheCurrentFileAndForgetsItWithTheNextFile() {
    progress.onEvent(new PhaseStarted(Phase.DOWNLOADING, 2, 1000));
    progress.onEvent(new FileStarted(FIRST, 1, 2, 0));
    AttemptFailed failure =
        new AttemptFailed(FIRST, DatasetFailureCause.NETWORK, "HTTP 503", 1, Duration.ofSeconds(2));

    at(1);
    progress.onEvent(failure);
    assertTrue(progress.snapshot().hasLastFailure());
    assertEquals(failure, progress.snapshot().lastFailure());

    progress.onEvent(new FileStarted(SECOND_FILE, 2, 2, 0));
    assertFalse(progress.snapshot().hasLastFailure());
  }

  @Test
  void aNewPhaseStartsFromZero() {
    progress.onEvent(new PhaseStarted(Phase.VERIFYING, 1, 600));
    progress.onEvent(new FileStarted(FIRST, 1, 1, 0));
    at(1);
    progress.onEvent(new BytesProcessed(FIRST, 600, 600));

    at(2);
    progress.onEvent(new PhaseStarted(Phase.DOWNLOADING, 1, 500));

    DatasetProgress.Snapshot s = progress.snapshot();
    assertEquals(Phase.DOWNLOADING, s.phase());
    assertFalse(s.hasFile());
    assertEquals(0, s.phaseBytes());
    assertEquals(500, s.phaseTotal());
    assertEquals(0, s.bytesPerSecond());
    assertFalse(s.hasRemaining());
  }

  @Test
  void endsWithTheOutcome() {
    progress.onEvent(new PhaseStarted(Phase.DOWNLOADING, 1, 600));
    DatasetInstallOutcome outcome = new DatasetInstallOutcome.Cancelled();

    progress.onEvent(new Finished(outcome));

    assertTrue(progress.snapshot().finished());
    assertEquals(outcome, progress.snapshot().outcome());
    assertEquals(Phase.DOWNLOADING, progress.snapshot().phase());
  }
}
