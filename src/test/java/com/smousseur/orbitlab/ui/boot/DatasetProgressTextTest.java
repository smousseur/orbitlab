package com.smousseur.orbitlab.ui.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.app.dataset.DatasetFailureCause;
import com.smousseur.orbitlab.app.dataset.DatasetFile;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import com.smousseur.orbitlab.app.dataset.DatasetInstallOutcome;
import com.smousseur.orbitlab.app.dataset.DatasetProgress.Snapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the strings the start-up screen shows. */
class DatasetProgressTextTest {

  private static final DatasetFile MOON =
      new DatasetFile("ephemeris/MOON.bin", 2_000_000_000L, "0".repeat(64), List.of());

  private static final DatasetFile EARTH_ORBIT =
      new DatasetFile("orbits/EARTH-orbit.bin", 1_000_000L, "1".repeat(64), List.of());

  private static final Snapshot BEFORE_ANY_PHASE =
      new Snapshot(null, null, 0, 0, 0, 0, 0, 0, null, null, null);

  /** A download of 7.57 GB, 2.80 GB of it done, in MOON.bin, rank 11 of 21. */
  private static Snapshot downloading(double bytesPerSecond, Duration remaining) {
    return new Snapshot(
        Phase.DOWNLOADING,
        MOON,
        11,
        21,
        701_152_537L,
        2_800_000_000L,
        7_567_283_506L,
        bytesPerSecond,
        remaining,
        null,
        null);
  }

  private static Snapshot phase(Phase phase, DatasetFile file, long done, long total) {
    return new Snapshot(phase, file, 1, 21, done, done, total, 0, null, null, null);
  }

  private static AttemptFailed attempt(DatasetFailureCause cause, Duration retryIn) {
    return new AttemptFailed(MOON, cause, "cut", 1, retryIn);
  }

  @Test
  void titleFollowsThePhase() {
    assertEquals("Preparing data", DatasetProgressText.title(BEFORE_ANY_PHASE));
    assertEquals(
        "Checking existing data",
        DatasetProgressText.title(phase(Phase.VERIFYING, MOON, 0, 1_000)));
    assertEquals(
        "Downloading ephemeris data",
        DatasetProgressText.title(phase(Phase.DOWNLOADING, MOON, 0, 1_000)));
  }

  @Test
  void fileLineNamesTheFileWithoutItsFolderAndItsRank() {
    assertEquals("MOON.bin (file 11 of 21)", DatasetProgressText.fileLine(downloading(0, null)));
    assertEquals("", DatasetProgressText.fileLine(BEFORE_ANY_PHASE));
    assertEquals(
        "",
        DatasetProgressText.fileLine(
            new Snapshot(Phase.VERIFYING, null, 0, 10, 0, 0, 1_000, 0, null, null, null)));
  }

  @Test
  void percentIsRoundedDownAndOnlyReaches100AtTheEnd() {
    assertEquals("37 %", DatasetProgressText.percent(downloading(0, null)));
    assertEquals("29 %", DatasetProgressText.percent(phase(Phase.VERIFYING, MOON, 29, 100)));
    assertEquals(
        "99 %", DatasetProgressText.percent(phase(Phase.DOWNLOADING, MOON, 999_999, 1_000_000)));
    assertEquals(
        "100 %", DatasetProgressText.percent(phase(Phase.DOWNLOADING, MOON, 1_000_000, 1_000_000)));
    assertEquals("0 %", DatasetProgressText.percent(phase(Phase.DOWNLOADING, MOON, 0, 1_000)));
    assertEquals("", DatasetProgressText.percent(BEFORE_ANY_PHASE));
  }

  @Test
  void amountsAreDecimalInTheUnitOfTheTotal() {
    assertEquals("2.80 / 7.57 GB", DatasetProgressText.amountLine(downloading(0, null)));
    assertEquals(
        "0.85 / 2.29 GB",
        DatasetProgressText.amountLine(
            phase(Phase.DOWNLOADING, MOON, 850_000_000L, 2_289_103_633L)));
    assertEquals(
        "0.00 / 1.00 GB",
        DatasetProgressText.amountLine(phase(Phase.DOWNLOADING, MOON, 0, 1_000_000_000L)));
    assertEquals(
        "120 / 450 MB",
        DatasetProgressText.amountLine(
            phase(Phase.DOWNLOADING, EARTH_ORBIT, 120_400_000L, 449_600_000L)));
    assertEquals("", DatasetProgressText.amountLine(BEFORE_ANY_PHASE));
  }

  @Test
  void rateHasOneDecimalUnder100MegabytesPerSecond() {
    assertEquals("4.6 MB/s", DatasetProgressText.rate(4_560_000));
    assertEquals("0.0 MB/s", DatasetProgressText.rate(0));
    assertEquals("99.9 MB/s", DatasetProgressText.rate(99_940_000));
    assertEquals("100 MB/s", DatasetProgressText.rate(99_960_000));
    assertEquals("653 MB/s", DatasetProgressText.rate(653_400_000));
  }

  @Test
  void timeLeftRoundsTheMinutesUp() {
    assertEquals("less than a minute left", DatasetProgressText.timeLeft(Duration.ZERO));
    assertEquals(
        "less than a minute left", DatasetProgressText.timeLeft(Duration.ofMillis(59_999)));
    assertEquals("about 1 min left", DatasetProgressText.timeLeft(Duration.ofSeconds(60)));
    assertEquals("about 2 min left", DatasetProgressText.timeLeft(Duration.ofMillis(60_500)));
    assertEquals(
        "about 17 min left", DatasetProgressText.timeLeft(Duration.ofMinutes(16).plusSeconds(1)));
    assertEquals(
        "about 1 h 00 min left",
        DatasetProgressText.timeLeft(Duration.ofMinutes(59).plusSeconds(30)));
    assertEquals("about 1 h 05 min left", DatasetProgressText.timeLeft(Duration.ofMinutes(65)));
    assertEquals(
        "about 27 h 22 min left",
        DatasetProgressText.timeLeft(Duration.ofHours(27).plusMinutes(22)));
  }

  @Test
  void speedLineWaitsForAMeasuredRate() {
    assertEquals("measuring speed...", DatasetProgressText.speedLine(downloading(0, null)));
    assertEquals(
        "4.6 MB/s - about 17 min left",
        DatasetProgressText.speedLine(
            downloading(4_600_000, Duration.ofMinutes(16).plusSeconds(40))));
    assertEquals("", DatasetProgressText.speedLine(BEFORE_ANY_PHASE));
  }

  @Test
  void retryLineCountsDownToTheNextAttempt() {
    AttemptFailed network = attempt(DatasetFailureCause.NETWORK, Duration.ofSeconds(4));

    assertEquals(
        "Connection lost, retrying in 4 s", DatasetProgressText.retryLine(network, Duration.ZERO));
    assertEquals(
        "Connection lost, retrying in 4 s",
        DatasetProgressText.retryLine(network, Duration.ofMillis(500)));
    assertEquals(
        "Connection lost, retrying in 1 s",
        DatasetProgressText.retryLine(network, Duration.ofMillis(3_200)));
    assertEquals(
        "Connection lost, retrying...",
        DatasetProgressText.retryLine(network, Duration.ofSeconds(4)));
    assertEquals(
        "Connection lost, retrying...",
        DatasetProgressText.retryLine(network, Duration.ofSeconds(50)));
    assertEquals(
        "Bad data received, retrying in 2 s",
        DatasetProgressText.retryLine(
            attempt(DatasetFailureCause.CORRUPT, Duration.ofSeconds(2)), Duration.ZERO));
  }

  @Test
  void aCheckFailsOnlyOnTheFileBeingRead() {
    Snapshot verifyingMoon = phase(Phase.VERIFYING, MOON, 10, 1_000);
    DatasetInstallOutcome.Failed unreadable =
        new DatasetInstallOutcome.Failed(DatasetFailureCause.LOCAL_IO, MOON, "cannot read");
    DatasetInstallOutcome.Failed noRoom =
        new DatasetInstallOutcome.Failed(DatasetFailureCause.DISK_FULL, null, "no room");

    assertEquals("Data check failed", DatasetProgressText.failureTitle(verifyingMoon, unreadable));
    assertEquals("Download failed", DatasetProgressText.failureTitle(verifyingMoon, noRoom));
    assertEquals(
        "Download failed",
        DatasetProgressText.failureTitle(phase(Phase.DOWNLOADING, MOON, 10, 1_000), unreadable));
    assertEquals("Download failed", DatasetProgressText.failureTitle(BEFORE_ANY_PHASE, noRoom));
  }

  @Test
  void everyCauseHasItsMessageOnTwoLinesAtMost() {
    assertEquals(
        "Couldn't reach GitHub. Check your connection, then retry.",
        DatasetProgressText.failureMessage(DatasetFailureCause.NETWORK));
    assertEquals(
        "Not enough disk space in the data folder. Free up space, then retry.",
        DatasetProgressText.failureMessage(DatasetFailureCause.DISK_FULL));
    assertEquals(
        "The downloaded data doesn't match its fingerprint. Retry, and report it if it happens"
            + " again.",
        DatasetProgressText.failureMessage(DatasetFailureCause.CORRUPT));
    assertEquals(
        "A data file is missing from the release. Retrying won't help; please report it.",
        DatasetProgressText.failureMessage(DatasetFailureCause.NOT_FOUND));
    assertEquals(
        "Couldn't write to the data folder. Check its permissions, then retry.",
        DatasetProgressText.failureMessage(DatasetFailureCause.LOCAL_IO));

    for (DatasetFailureCause cause : DatasetFailureCause.values()) {
      List<String> lines =
          DatasetProgressText.wrap(DatasetProgressText.failureMessage(cause), 61, 2);
      assertTrue(lines.size() <= 2, cause + ": " + lines);
      assertFalse(lines.getLast().endsWith("..."), cause + " was cut: " + lines);
      assertEquals(
          DatasetProgressText.failureMessage(cause), String.join(" ", lines), cause.name());
    }
  }

  @Test
  void detailNamesTheFileAndKeepsOnlyPrintableAscii() {
    assertEquals(
        "ephemeris/MOON.bin: HTTP 404 from https://example.org/MOON.bin.p03",
        DatasetProgressText.failureDetail(
            new DatasetInstallOutcome.Failed(
                DatasetFailureCause.NOT_FOUND,
                MOON,
                "HTTP 404 from https://example.org/MOON.bin.p03")));
    assertEquals(
        "cannot write C:\\Users\\Zo?\\.orbitlab: Acc?s refus? ? done",
        DatasetProgressText.failureDetail(
            new DatasetInstallOutcome.Failed(
                DatasetFailureCause.LOCAL_IO,
                null,
                "cannot write C:\\Users\\Zoé\\.orbitlab: Accès refusé \uD83D\uDE80\ndone")));
  }

  @Test
  void wrapCutsAtASpaceThenAnywhereThenEndsWithAnEllipsis() {
    assertEquals(List.of("short"), DatasetProgressText.wrap("  short ", 10, 2));
    assertEquals(List.of(), DatasetProgressText.wrap("", 10, 2));
    assertEquals(List.of("one two", "three"), DatasetProgressText.wrap("one two three", 10, 2));
    assertEquals(
        List.of("C:/a/very/", "long/path"), DatasetProgressText.wrap("C:/a/very/long/path", 10, 2));
    assertEquals(
        List.of("abcdefghij", "klmnopq..."),
        DatasetProgressText.wrap("abcdefghijklmnopqrstuvwxyz", 10, 2));
    assertEquals(
        List.of("one two", "three f..."),
        DatasetProgressText.wrap("one two three four five six", 10, 2));
    assertThrows(IllegalArgumentException.class, () -> DatasetProgressText.wrap("x", 3, 2));
    assertThrows(IllegalArgumentException.class, () -> DatasetProgressText.wrap("x", 10, 0));
  }

  @Test
  void aLongDetailFitsTwoLinesOfTheCard() {
    String detail =
        DatasetProgressText.failureDetail(
            new DatasetInstallOutcome.Failed(
                DatasetFailureCause.NETWORK,
                MOON,
                "download from https://github.com/smousseur/orbitlab/releases/download/dataset-v1/"
                    + "MOON.bin.p03 failed after 12,345,678 bytes: java.io.IOException: closed;"
                    + " gave up after 3 failures in a row without progress"));

    List<String> lines = DatasetProgressText.wrap(detail, 70, 2);

    assertEquals(2, lines.size());
    assertTrue(lines.stream().allMatch(line -> line.length() <= 70), lines.toString());
    assertTrue(lines.getLast().endsWith("..."), lines.toString());
  }

  @Test
  void everyTextIsPrintableAscii() {
    List<String> texts = new ArrayList<>();
    List<Snapshot> snapshots =
        List.of(
            BEFORE_ANY_PHASE,
            phase(Phase.VERIFYING, MOON, 1_234_567_890L, 4_577_027_336L),
            phase(Phase.DOWNLOADING, EARTH_ORBIT, 12_345_678L, 345_678_901L),
            downloading(4_600_000, Duration.ofMinutes(65)),
            downloading(4_600_000, Duration.ofSeconds(20)));
    for (Snapshot snapshot : snapshots) {
      texts.add(DatasetProgressText.title(snapshot));
      texts.add(DatasetProgressText.fileLine(snapshot));
      texts.add(DatasetProgressText.percent(snapshot));
      texts.add(DatasetProgressText.amountLine(snapshot));
      texts.add(DatasetProgressText.speedLine(snapshot));
    }
    for (DatasetFailureCause cause : DatasetFailureCause.values()) {
      DatasetInstallOutcome.Failed failed =
          new DatasetInstallOutcome.Failed(cause, MOON, "d\u00e9tail \u2014 \u00e9chec");
      texts.add(
          DatasetProgressText.retryLine(attempt(cause, Duration.ofSeconds(4)), Duration.ZERO));
      texts.add(
          DatasetProgressText.retryLine(attempt(cause, Duration.ofSeconds(4)), Duration.ofDays(1)));
      texts.add(DatasetProgressText.failureTitle(snapshots.get(1), failed));
      texts.add(DatasetProgressText.failureMessage(cause));
      texts.add(DatasetProgressText.failureDetail(failed));
    }

    for (String text : texts) {
      assertTrue(text.chars().allMatch(c -> c >= 32 && c <= 126), "not printable ASCII: " + text);
    }
  }
}
