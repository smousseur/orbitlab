package com.smousseur.orbitlab.app.converters;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

import java.time.LocalDateTime;

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
}
