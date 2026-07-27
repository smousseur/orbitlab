package com.smousseur.orbitlab.app.converters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
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
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  // Parsing is strict on purpose: the default SMART resolver silently folds 2030-02-31 back to the
  // 28th, and a seek is not the place to guess what the user meant. STRICT requires era-less
  // "uuuu" rather than "yyyy".
  private static final DateTimeFormatter PARSE_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter PARSE_DATE_ONLY_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
  private static final int DATE_ONLY_LENGTH = "yyyy-MM-dd".length();

  private TimeConverter() {}

  /** Converts an Orekit {@link AbsoluteDate} to a {@link LocalDateTime} for display (UTC). */
  public static LocalDateTime toUtcLocalDateTime(AbsoluteDate date) {
    Objects.requireNonNull(date, "date");
    Date javaDate = date.toDate(UTC); // Represents an instant
    Instant instant = javaDate.toInstant();
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /**
   * Converts a {@link LocalDateTime} interpreted as UTC to an Orekit {@link AbsoluteDate}.
   *
   * <p>Built from calendar components rather than from a {@link Date}: Orekit's {@code
   * AbsoluteDate(Date, TimeScale)} splits the epoch milliseconds with {@code /} and {@code %}, which
   * both truncate towards zero, so any instant before 1970 yields a negative seconds-in-day and
   * throws.
   */
  public static AbsoluteDate fromUtcLocalDateTime(LocalDateTime utcDateTime) {
    Objects.requireNonNull(utcDateTime, "utcDateTime");
    return new AbsoluteDate(
        utcDateTime.getYear(),
        utcDateTime.getMonthValue(),
        utcDateTime.getDayOfMonth(),
        utcDateTime.getHour(),
        utcDateTime.getMinute(),
        utcDateTime.getSecond() + utcDateTime.getNano() / 1_000_000_000.0,
        UTC);
  }

  /**
   * Convenience method for HUD/debug display, formatted like: 2026-01-08T12:34:56Z
   *
   * <p>Uses seconds precision (LocalDateTime's {@code toString()} includes nanos only if present).
   */
  public static String toUtcIsoString(AbsoluteDate date) {
    return toUtcLocalDateTime(date) + "Z";
  }

  /**
   * Formats an Orekit {@link AbsoluteDate} using the pattern {@code yyyy-MM-dd HH:mm:ss} in UTC.
   *
   * @param date the date to format
   * @return the formatted date string
   */
  public static String formatDate(AbsoluteDate date) {
    Objects.requireNonNull(date, "date");
    Instant instant = date.toInstant();
    return instant.atZone(ZoneOffset.UTC).format(DATA_FORMATTER);
  }

  /**
   * Parses a user-entered UTC date, accepting the two formats the application itself produces plus a
   * date-only shorthand:
   *
   * <ul>
   *   <li>{@code yyyy-MM-dd HH:mm:ss} — what {@link #formatDate(AbsoluteDate)} displays
   *   <li>{@code yyyy-MM-ddTHH:mm:ssZ} — what {@code OrekitTime.formatDate} writes, so a value
   *       copied from the mission wizard round-trips
   *   <li>{@code yyyy-MM-dd} — resolved to {@code 00:00:00}
   * </ul>
   *
   * <p>Invalid input yields an empty optional rather than an exception: callers are interactive and
   * treat a rejected entry as a UI state, not as an error.
   *
   * @param text the raw user input (surrounding whitespace is ignored, {@code null} is accepted)
   * @return the parsed date interpreted as UTC, or empty if the text is not a supported date
   */
  public static Optional<AbsoluteDate> parseUtcDate(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String normalized = text.trim();
    if (normalized.endsWith("Z") || normalized.endsWith("z")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    normalized = normalized.replace('T', ' ').replace('t', ' ').trim();

    try {
      LocalDateTime parsed =
          normalized.length() == DATE_ONLY_LENGTH
              ? LocalDate.parse(normalized, PARSE_DATE_ONLY_FORMATTER).atStartOfDay()
              : LocalDateTime.parse(normalized, PARSE_FORMATTER);
      return Optional.of(fromUtcLocalDateTime(parsed));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
