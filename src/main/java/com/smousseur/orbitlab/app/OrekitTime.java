package com.smousseur.orbitlab.app;

import java.time.Instant;
import java.util.Date;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateTimeComponents;
import org.orekit.time.TimeScalesFactory;

/**
 * Utility class for creating Orekit {@link AbsoluteDate} instances from Java time types.
 *
 * <p>All dates produced by this class use the UTC time scale.
 */
public final class OrekitTime {
  private OrekitTime() {}

  /**
   * Returns the current UTC time as an Orekit {@link AbsoluteDate}.
   *
   * @return the current instant in UTC
   */
  public static AbsoluteDate utcNow() {
    return fromInstantUtc(Instant.now());
  }

  /**
   * Converts a Java {@link Instant} to an Orekit {@link AbsoluteDate} in the UTC time scale.
   *
   * @param instant the Java instant to convert
   * @return the corresponding Orekit absolute date in UTC
   */
  public static AbsoluteDate fromInstantUtc(Instant instant) {
    return new AbsoluteDate(Date.from(instant), TimeScalesFactory.getUTC());
  }

  /**
   * Utc now string string.
   *
   * @return the string
   */
  public static String utcNowString() {
    return formatDate(utcNow());
  }

  /**
   * Format date string.
   *
   * @param date the date
   * @return the string
   */
  public static String formatDate(AbsoluteDate date) {
    DateTimeComponents components = date.getComponents(TimeScalesFactory.getUTC());
    return String.format(
        "%04d-%02d-%02dT%02d:%02d:%02dZ",
        components.getDate().getYear(),
        components.getDate().getMonth(),
        components.getDate().getDay(),
        components.getTime().getHour(),
        components.getTime().getMinute(),
        (int) components.getTime().getSecond());
  }
}
