package com.smousseur.orbitlab.ui;

import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.source.EphemerisSource;
import com.smousseur.orbitlab.simulation.source.EphemerisSourceRegistry;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * Ephemeris coverage, as the widgets that accept a typed date need it: can this date be rendered,
 * and how is the admissible range worded.
 *
 * <p>Shared by the timeline date field and the wizard launch date so that both refuse the same
 * entries with the same wording. When no source is published the window is unbounded — consistent
 * with the Keplerian fallback of {@link EphemerisSource#sampleIcrfSafe}.
 */
public final class EphemerisWindow {

  private EphemerisWindow() {}

  /**
   * Tells whether the published ephemeris source can sample the given date.
   *
   * @param date the candidate date
   * @return {@code true} when the date is inside coverage, or when nothing bounds it
   */
  public static boolean covers(AbsoluteDate date) {
    Optional<EphemerisSource> source = EphemerisSourceRegistry.get();
    AbsoluteDate start = source.flatMap(EphemerisSource::coverageStart).orElse(null);
    AbsoluteDate endExclusive = source.flatMap(EphemerisSource::coverageEndExclusive).orElse(null);
    return (start == null || date.compareTo(start) >= 0)
        && (endExclusive == null || date.compareTo(endExclusive) < 0);
  }

  /**
   * Returns the admissible range for display, in UTC — the scale dates are typed in, while the
   * bounds are held in TAI (≈ 37 s apart).
   *
   * @return the range, or empty when nothing bounds the window
   */
  public static Optional<String> rangeLabel() {
    Optional<EphemerisSource> source = EphemerisSourceRegistry.get();
    Optional<AbsoluteDate> start = source.flatMap(EphemerisSource::coverageStart);
    Optional<AbsoluteDate> endExclusive = source.flatMap(EphemerisSource::coverageEndExclusive);
    if (start.isEmpty() && endExclusive.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        start.map(TimeConverter::formatDate).orElse("...")
            + " -> "
            + endExclusive.map(TimeConverter::formatDate).orElse("..."));
  }
}
