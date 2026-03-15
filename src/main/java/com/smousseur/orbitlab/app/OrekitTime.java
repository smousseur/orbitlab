package com.smousseur.orbitlab.app;

import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import java.time.Instant;
import java.util.Date;

/**
 * Utility class for creating Orekit {@link AbsoluteDate} instances from Java time types.
 *
 * <p>All dates produced by this class use the UTC time scale.
 */
public final class OrekitTime {

  private OrekitTime() {
  }

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
}
