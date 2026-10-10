package com.smousseur.orbitlab.ui.boot;

import com.smousseur.orbitlab.app.dataset.DatasetFailureCause;
import com.smousseur.orbitlab.app.dataset.DatasetFile;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.AttemptFailed;
import com.smousseur.orbitlab.app.dataset.DatasetInstallEvent.Phase;
import com.smousseur.orbitlab.app.dataset.DatasetInstallOutcome;
import com.smousseur.orbitlab.app.dataset.DatasetProgress.Snapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every string the start-up screen shows while the dataset is checked or downloaded, and when that
 * fails.
 *
 * <p>The screen needs a JME context and cannot be unit-tested; the decisions that can be wrong —
 * units, rounding, what an unknown rate reads as, how a failure's detail is cut — live here and are
 * covered.
 *
 * <p><b>ASCII only</b>, like every other HUD string: the fonts the screen uses carry glyphs 32-127,
 * and a missing glyph is dropped without any error. The units are decimal, as a download manager
 * shows them.
 */
public final class DatasetProgressText {

  /** The title before the installation reports its first phase. */
  public static final String PREPARING = "Preparing data";

  /** The time left while no rate is measured yet. */
  public static final String MEASURING_SPEED = "measuring speed...";

  private static final double BYTES_PER_MB = 1e6;
  private static final double BYTES_PER_GB = 1e9;
  private static final long WHOLE_RATE_MB = 100;
  private static final long NANOS_PER_MINUTE = Duration.ofMinutes(1).toNanos();
  private static final String ELLIPSIS = "...";

  private DatasetProgressText() {}

  /**
   * The card's title for the phase under way.
   *
   * @param snapshot the installation's state
   * @return the title, never null
   */
  public static String title(Snapshot snapshot) {
    if (!snapshot.hasPhase()) {
      return PREPARING;
    }
    return switch (snapshot.phase()) {
      case VERIFYING -> "Checking existing data";
      case DOWNLOADING -> "Downloading ephemeris data";
    };
  }

  /**
   * The current file and its rank, e.g. {@code "MOON.bin (file 11 of 21)"}.
   *
   * @param snapshot the installation's state
   * @return the line, empty before the phase's first file
   */
  public static String fileLine(Snapshot snapshot) {
    if (!snapshot.hasFile()) {
      return "";
    }
    return String.format(
        Locale.ROOT,
        "%s (file %d of %d)",
        fileName(snapshot.file()),
        snapshot.fileIndex(),
        snapshot.fileCount());
  }

  /**
   * The share of the phase done, rounded down so that 100 % only shows once the phase is over.
   *
   * @param snapshot the installation's state
   * @return e.g. {@code "37 %"}, empty before the first phase
   */
  public static String percent(Snapshot snapshot) {
    if (!snapshot.hasPhase()) {
      return "";
    }
    long total = snapshot.phaseTotal();
    long percent = total <= 0 ? 0 : Math.min(100, snapshot.phaseBytes() * 100 / total);
    return percent + " %";
  }

  /**
   * The bytes done and to do in the phase, in the unit of the total, e.g. {@code "2.80 / 7.57 GB"}
   * or {@code "120 / 450 MB"}.
   *
   * @param snapshot the installation's state
   * @return the line, empty before the first phase
   */
  public static String amountLine(Snapshot snapshot) {
    if (!snapshot.hasPhase()) {
      return "";
    }
    long done = snapshot.phaseBytes();
    long total = snapshot.phaseTotal();
    if (total >= BYTES_PER_GB) {
      return String.format(
          Locale.ROOT, "%.2f / %.2f GB", done / BYTES_PER_GB, total / BYTES_PER_GB);
    }
    return String.format(
        Locale.ROOT,
        "%d / %d MB",
        Math.round(done / BYTES_PER_MB),
        Math.round(total / BYTES_PER_MB));
  }

  /**
   * The rate and the time left, e.g. {@code "4.6 MB/s - about 17 min left"}.
   *
   * @param snapshot the installation's state
   * @return the line; {@link #MEASURING_SPEED} until a rate is measured, empty before the first
   *     phase
   */
  public static String speedLine(Snapshot snapshot) {
    if (!snapshot.hasPhase()) {
      return "";
    }
    if (!snapshot.hasRemaining()) {
      return MEASURING_SPEED;
    }
    return rate(snapshot.bytesPerSecond()) + " - " + timeLeft(snapshot.remaining());
  }

  /**
   * A rate in megabytes per second, with one decimal below 100 MB/s, e.g. {@code "4.6 MB/s"}.
   *
   * @param bytesPerSecond the rate
   * @return the rate with its unit
   */
  public static String rate(double bytesPerSecond) {
    long tenths = Math.round(bytesPerSecond / BYTES_PER_MB * 10);
    if (tenths >= WHOLE_RATE_MB * 10) {
      return Math.round(tenths / 10.0) + " MB/s";
    }
    return tenths / 10 + "." + tenths % 10 + " MB/s";
  }

  /**
   * A time left, the minutes rounded up: {@code "less than a minute left"}, {@code "about 17 min
   * left"}, {@code "about 1 h 05 min left"}.
   *
   * @param remaining the time left
   * @return the phrase
   */
  public static String timeLeft(Duration remaining) {
    if (remaining.compareTo(Duration.ofMinutes(1)) < 0) {
      return "less than a minute left";
    }
    long minutes = (remaining.toNanos() + NANOS_PER_MINUTE - 1) / NANOS_PER_MINUTE;
    if (minutes < 60) {
      return "about " + minutes + " min left";
    }
    return String.format(Locale.ROOT, "about %d h %02d min left", minutes / 60, minutes % 60);
  }

  /**
   * What replaces the speed line after a failed attempt, counting down to the next one, e.g. {@code
   * "Connection lost, retrying in 4 s"}, then {@code "Connection lost, retrying..."} once the wait
   * is over and the next attempt is under way.
   *
   * @param failure the failed attempt
   * @param sinceFailure how long ago it was reported
   * @return the line
   */
  public static String retryLine(AttemptFailed failure, Duration sinceFailure) {
    String what =
        switch (failure.cause()) {
          case NETWORK -> "Connection lost";
          case CORRUPT -> "Bad data received";
          case DISK_FULL, NOT_FOUND, LOCAL_IO -> "Download error";
        };
    long left = failure.retryIn().minus(sinceFailure).toMillis();
    if (left <= 0) {
      return what + ", retrying...";
    }
    return what + ", retrying in " + (left + 999) / 1000 + " s";
  }

  /**
   * The error card's title: a check failed when the file being read through its fingerprint could
   * not be, a download failed in every other case — including a lack of space found once the check
   * is over.
   *
   * @param snapshot the installation's state when it ended
   * @param failed how it ended
   * @return the title
   */
  public static String failureTitle(Snapshot snapshot, DatasetInstallOutcome.Failed failed) {
    boolean checking =
        snapshot.phase() == Phase.VERIFYING
            && failed.hasFile()
            && failed.file().equals(snapshot.file());
    return checking ? "Data check failed" : "Download failed";
  }

  /**
   * What the user is told for each cause of failure.
   *
   * @param cause the cause
   * @return the message, on one line; the screen wraps it
   */
  public static String failureMessage(DatasetFailureCause cause) {
    return switch (cause) {
      case NETWORK -> "Couldn't reach GitHub. Check your connection, then retry.";
      case DISK_FULL -> "Not enough disk space in the data folder. Free up space, then retry.";
      case CORRUPT ->
          "The downloaded data doesn't match its fingerprint. Retry, and report it if it happens"
              + " again.";
      case NOT_FOUND ->
          "A data file is missing from the release. Retrying won't help; please report it.";
      case LOCAL_IO -> "Couldn't write to the data folder. Check its permissions, then retry.";
    };
  }

  /**
   * The technical detail of a failure: the file, then what happened, with every character outside
   * printable ASCII replaced by {@code ?}.
   *
   * @param failed the failure
   * @return the detail, on one line; see {@link #wrap} for how it is cut
   */
  public static String failureDetail(DatasetInstallOutcome.Failed failed) {
    String text =
        failed.hasFile() ? failed.file().path() + ": " + failed.detail() : failed.detail();
    return ascii(text);
  }

  /**
   * Replaces line breaks and tabs by spaces and every other character outside printable ASCII by
   * {@code ?}.
   *
   * @param text any text
   * @return the text, printable ASCII only
   */
  public static String ascii(String text) {
    StringBuilder out = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            c -> {
              if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
              } else if (c < 32 || c > 126) {
                out.append('?');
              } else {
                out.append((char) c);
              }
            });
    return out.toString();
  }

  /**
   * Cuts a text into lines of at most {@code columns} characters, at a space when there is one in
   * the second half of the line, anywhere otherwise, since a path or an address has none. A text
   * that needs more than {@code maxLines} lines ends its last one with {@code ...}.
   *
   * @param text the text, on one line
   * @param columns the most characters a line holds, more than 3
   * @param maxLines the most lines, at least 1
   * @return the lines, none of them empty unless the text is
   */
  public static List<String> wrap(String text, int columns, int maxLines) {
    if (columns <= ELLIPSIS.length() || maxLines < 1) {
      throw new IllegalArgumentException("columns " + columns + ", maxLines " + maxLines);
    }
    List<String> lines = new ArrayList<>(maxLines);
    String rest = text.strip();
    while (!rest.isEmpty()) {
      if (rest.length() <= columns) {
        lines.add(rest);
        break;
      }
      if (lines.size() == maxLines - 1) {
        lines.add(rest.substring(0, columns - ELLIPSIS.length()).stripTrailing() + ELLIPSIS);
        break;
      }
      int cut = rest.lastIndexOf(' ', columns);
      if (cut < columns / 2) {
        cut = columns;
      }
      lines.add(rest.substring(0, cut).stripTrailing());
      rest = rest.substring(cut).stripLeading();
    }
    return lines;
  }

  private static String fileName(DatasetFile file) {
    String path = file.path();
    return ascii(path.substring(path.lastIndexOf('/') + 1));
  }
}
