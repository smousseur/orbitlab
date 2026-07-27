package com.smousseur.orbitlab.app.converters;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeConverterTest {

  private static TimeScale UTC;

  @BeforeAll
  static void initOrekit() {
    OrekitService.get().initialize();
    UTC = TimeScalesFactory.getUTC();
  }

  @Test
  void toUtcLocalDateTime_convertsCorrectly() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 8, 12, 34, 56.0, UTC);

    LocalDateTime ldt = TimeConverter.toUtcLocalDateTime(date);

    assertEquals(LocalDateTime.of(2026, 1, 8, 12, 34, 56), ldt);
  }

  @Test
  void fromUtcLocalDateTime_convertsCorrectly() {
    LocalDateTime ldt = LocalDateTime.of(2026, 1, 8, 12, 34, 56);

    AbsoluteDate date = TimeConverter.fromUtcLocalDateTime(ldt);

    assertEquals(new AbsoluteDate(2026, 1, 8, 12, 34, 56.0, UTC), date);
  }

  @Test
  void roundTrip_keepsSecondsPrecision() {
    AbsoluteDate original = new AbsoluteDate(2030, 6, 1, 0, 0, 5.0, UTC);

    LocalDateTime ldt = TimeConverter.toUtcLocalDateTime(original);
    AbsoluteDate back = TimeConverter.fromUtcLocalDateTime(ldt);

    assertEquals(original, back);
  }

  @Test
  void toUtcIsoString_appendsZ() {
    AbsoluteDate date = new AbsoluteDate(2026, 1, 8, 12, 34, 56.0, UTC);

    assertEquals("2026-01-08T12:34:56Z", TimeConverter.toUtcIsoString(date));
  }

  @Test
  void parseUtcDate_acceptsDisplayedFormat() {
    AbsoluteDate expected = new AbsoluteDate(2030, 3, 14, 9, 26, 53.0, UTC);

    assertEquals(Optional.of(expected), TimeConverter.parseUtcDate("2030-03-14 09:26:53"));
  }

  @Test
  void parseUtcDate_acceptsIsoFormatWrittenByTheWizard() {
    AbsoluteDate expected = new AbsoluteDate(2030, 3, 14, 9, 26, 53.0, UTC);

    assertEquals(Optional.of(expected), TimeConverter.parseUtcDate("2030-03-14T09:26:53Z"));
  }

  @Test
  void parseUtcDate_acceptsDateOnlyAsMidnight() {
    AbsoluteDate expected = new AbsoluteDate(2030, 3, 14, 0, 0, 0.0, UTC);

    assertEquals(Optional.of(expected), TimeConverter.parseUtcDate("  2030-03-14  "));
  }

  @Test
  void parseUtcDate_rejectsUnparsableEntries() {
    assertEquals(Optional.empty(), TimeConverter.parseUtcDate("hier"));
    assertEquals(Optional.empty(), TimeConverter.parseUtcDate("2030-02-31 00:00:00"));
    assertEquals(Optional.empty(), TimeConverter.parseUtcDate("2030-03-14 09:26"));
    assertEquals(Optional.empty(), TimeConverter.parseUtcDate(""));
    assertEquals(Optional.empty(), TimeConverter.parseUtcDate(null));
  }

  @Test
  void parseUtcDate_roundTripsWithFormatDate() {
    AbsoluteDate original = new AbsoluteDate(2101, 1, 1, 0, 0, 0.0, UTC);

    assertEquals(
        Optional.of(original), TimeConverter.parseUtcDate(TimeConverter.formatDate(original)));
  }
}
