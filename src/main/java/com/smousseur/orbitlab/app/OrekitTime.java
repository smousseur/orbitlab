package com.smousseur.orbitlab.app;

import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import java.time.Instant;
import java.util.Date;

public final class OrekitTime {

  private OrekitTime() {
  }

  public static AbsoluteDate utcNow() {
    return fromInstantUtc(Instant.now());
  }

  public static AbsoluteDate fromInstantUtc(Instant instant) {
    return new AbsoluteDate(Date.from(instant), TimeScalesFactory.getUTC());
  }
}
