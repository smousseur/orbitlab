package com.smousseur.orbitlab.app.converters;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

/**
 * Time conversion utilities used at the application layer.
 *
 * <p>Convention: user-facing dates are displayed/entered in UTC.
 *
 * <p>Note: LocalDateTime has no timezone. Here it is always interpreted as UTC.
 */
public final class TimeConverter {

  private static final TimeScale UTC = TimeScalesFactory.getUTC();
  private static final DateTimeFormatter DATA_FORMATTER =
      DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss");

  private TimeConverter() {}

  /** Converts an Orekit {@link AbsoluteDate} to a {@link LocalDateTime} for display (UTC). */
  public static LocalDateTime toUtcLocalDateTime(AbsoluteDate date) {
    Objects.requireNonNull(date, "date");
    Date javaDate = date.toDate(UTC); // Represents an instant
    Instant instant = javaDate.toInstant();
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /** Converts a {@link LocalDateTime} interpreted as UTC to an Orekit {@link AbsoluteDate}. */
  public static AbsoluteDate fromUtcLocalDateTime(LocalDateTime utcDateTime) {
    Objects.requireNonNull(utcDateTime, "utcDateTime");
    Instant instant = utcDateTime.toInstant(ZoneOffset.UTC);
    return new AbsoluteDate(Date.from(instant), UTC);
  }

  /**
   * Convenience method for HUD/debug display, formatted like: 2026-01-08T12:34:56Z
   *
   * <p>Uses seconds precision (LocalDateTime's {@code toString()} includes nanos only if present).
   */
  public static String toUtcIsoString(AbsoluteDate date) {
    return toUtcLocalDateTime(date) + "Z";
  }

  public static String formatDate(AbsoluteDate date) {
    Objects.requireNonNull(date, "date");
    Instant instant = date.toInstant();
    return instant.atZone(ZoneOffset.UTC).format(DATA_FORMATTER);
  }
}
