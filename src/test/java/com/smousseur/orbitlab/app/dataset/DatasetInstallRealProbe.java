package com.smousseur.orbitlab.app.dataset;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.BytesProcessed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.FileStarted;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.PhaseStarted;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The installation against the published release, with the application's own configuration, into a
 * temporary directory deleted at the end: it is cancelled once the Moon's partial download passes
 * 700 MB, installed again, which adopts the files already complete and downloads only what the Moon
 * misses, then the marker is deleted and the whole dataset is adopted without any download. Each
 * step's duration is logged. About 25 to 30 minutes on an ordinary connection, and 7.6 GB of disk.
 * Run with {@code -Dorbitlab.probe=true --tests '*DatasetInstallRealProbe*'}.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class DatasetInstallRealProbe {

  private static final Logger LOGGER = LogManager.getLogger(DatasetInstallRealProbe.class);

  private static final long CANCEL_AFTER_BYTES = 700_000_000L;

  @TempDir Path root;

  /** What one installation reported, kept small: the phases, the starts and each phase's end. */
  private static final class Recorder implements DatasetInstallListener {
    private final List<PhaseStarted> phases = new ArrayList<>();
    private final List<FileStarted> starts = new ArrayList<>();
    private final List<AttemptFailed> failures = new ArrayList<>();
    private final Map<Phase, BytesProcessed> lastBytes = new EnumMap<>(Phase.class);
    private Phase phase;

    @Override
    public void onEvent(DatasetInstallEvent event) {
      switch (event) {
        case PhaseStarted e -> {
          phases.add(e);
          phase = e.phase();
        }
        case FileStarted e -> starts.add(e);
        case BytesProcessed e -> lastBytes.put(phase, e);
        case AttemptFailed e -> {
          failures.add(e);
          LOGGER.warn("Attempt failed: {}", e);
        }
        default -> {
          // Completions and the end are read from the outcome.
        }
      }
    }

    FileStarted startOf(DatasetFile file) {
      return starts.stream().filter(s -> s.file().equals(file)).findFirst().orElseThrow();
    }
  }

  @Test
  void cancelsResumesAndAdoptsTheRealRelease() throws IOException {
    DatasetManifest manifest = DatasetManifestCodec.readEmbedded();
    DatasetChecker checker = new DatasetChecker(manifest, root);
    DatasetInstaller installer = new DatasetInstaller(checker, DatasetInstallConfig.defaults());
    int moonIndex = indexOf(manifest, "ephemeris/MOON.bin");
    DatasetFile moon = manifest.files().get(moonIndex);
    List<DatasetFile> beforeMoon = manifest.files().subList(0, moonIndex);
    List<DatasetFile> afterMoon = manifest.files().subList(moonIndex + 1, manifest.files().size());
    List<String> summary = new ArrayList<>();

    Recorder first = new Recorder();
    long started = System.nanoTime();
    DatasetInstallOutcome cancelled =
        installer.install(
            event -> {
              first.onEvent(event);
              if (event instanceof BytesProcessed p
                  && p.file().equals(moon)
                  && p.fileBytes() > CANCEL_AFTER_BYTES) {
                installer.cancel();
              }
            });
    summary.add(step("1. cancelled in the Moon", started, first));
    assertEquals(new DatasetInstallOutcome.Cancelled(), cancelled);
    long partBytes = Files.size(checker.partOf(moon));
    assertTrue(partBytes > CANCEL_AFTER_BYTES, "partial download kept: " + partBytes);
    assertFalse(Files.exists(checker.fileOf(moon)));
    assertFalse(Files.exists(checker.marker()));
    for (DatasetFile file : beforeMoon) {
      assertEquals(file.size(), Files.size(checker.fileOf(file)), file.path());
    }
    summary.add(String.format(Locale.ROOT, "   MOON.bin.part kept at %,d bytes", partBytes));

    Recorder second = new Recorder();
    started = System.nanoTime();
    DatasetInstallOutcome resumed = installer.install(second);
    summary.add(step("2. resumed", started, second));
    assertEquals(new DatasetInstallOutcome.Completed(), resumed);
    long toDownload = moon.size() - partBytes + sizeOf(afterMoon);
    assertEquals(
        List.of(
            new PhaseStarted(Phase.VERIFYING, beforeMoon.size(), sizeOf(beforeMoon)),
            new PhaseStarted(Phase.DOWNLOADING, 1 + afterMoon.size(), toDownload)),
        second.phases);
    assertEquals(partBytes, second.startOf(moon).presentBytes());
    assertEquals(toDownload, second.lastBytes.get(Phase.DOWNLOADING).phaseBytes());

    assertTrue(checker.check().ready());
    assertEquals(manifest, DatasetManifestCodec.read(Files.readString(checker.marker())));

    Files.delete(checker.marker());
    Recorder third = new Recorder();
    started = System.nanoTime();
    DatasetInstallOutcome adopted = installer.install(third);
    summary.add(step("4. adopted without marker", started, third));
    assertEquals(new DatasetInstallOutcome.Completed(), adopted);
    assertEquals(
        List.of(new PhaseStarted(Phase.VERIFYING, manifest.files().size(), manifest.totalSize())),
        third.phases);
    assertTrue(checker.check().ready());

    LOGGER.info("Dataset installation against {}:\n{}", manifest.tag(), String.join("\n", summary));
  }

  private static String step(String name, long startedNanos, Recorder recorder) {
    double seconds = (System.nanoTime() - startedNanos) / 1e9;
    StringBuilder line =
        new StringBuilder(String.format(Locale.ROOT, "%-28s %8.1f s", name, seconds));
    for (PhaseStarted phase : recorder.phases) {
      BytesProcessed last = recorder.lastBytes.get(phase.phase());
      long done = last == null ? 0 : last.phaseBytes();
      line.append(
          String.format(
              Locale.ROOT,
              "; %s %d files, %,d of %,d bytes",
              phase.phase(),
              phase.fileCount(),
              done,
              phase.bytes()));
    }
    line.append("; ").append(recorder.failures.size()).append(" failed attempts");
    return line.toString();
  }

  private static int indexOf(DatasetManifest manifest, String path) {
    for (int i = 0; i < manifest.files().size(); i++) {
      if (manifest.files().get(i).path().equals(path)) {
        return i;
      }
    }
    throw new AssertionError("no " + path + " in the manifest");
  }

  private static long sizeOf(List<DatasetFile> files) {
    return files.stream().mapToLong(DatasetFile::size).sum();
  }
}
