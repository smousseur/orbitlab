package com.smousseur.orbitlab.ui.timeline.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * The linear map between a mission's time window and the pixels of the timeline track, and the
 * single owner of that projection (spec {@code docs/navigation/02-timeline-mission.md} §5).
 *
 * <p><b>Linear, with no deformation.</b> Propulsive phases are ~2% of a GEO's duration, so an
 * honest axis compresses the whole ascent into a handful of pixels. That cost is paid by the
 * markers, which are declustered instead ({@code PhaseMarkerCluster}); it is not paid by bending
 * the axis, because a track whose distances are not durations lies about the only thing it exists
 * to say — and it would lie exactly where {@code NAV-3} will later put a scrub whose cursor
 * position must be a date.
 *
 * <p>Free of any JME dependency on purpose: dates, doubles and pixel coordinates only, so the
 * projection can be tested without a render loop.
 */
public final class TimeAxis {

  private final AbsoluteDate start;
  private final AbsoluteDate end;
  private final double durationSeconds;
  private final float x0;
  private final float width;

  /**
   * Builds the axis of a mission window.
   *
   * @param start the window's first sample date
   * @param end the window's last sample date, strictly after {@code start}
   * @param x0 the x coordinate of the track's left edge, in the widget's local space
   * @param width the track width in pixels, strictly positive
   * @throws IllegalArgumentException if the window or the width is not strictly positive
   */
  public TimeAxis(AbsoluteDate start, AbsoluteDate end, float x0, float width) {
    this.start = Objects.requireNonNull(start, "start");
    this.end = Objects.requireNonNull(end, "end");
    this.durationSeconds = end.durationFrom(start);
    if (!(durationSeconds > 0.0)) {
      throw new IllegalArgumentException("window duration must be > 0, got " + durationSeconds);
    }
    if (!(width > 0f)) {
      throw new IllegalArgumentException("track width must be > 0, got " + width);
    }
    this.x0 = x0;
    this.width = width;
  }

  /** The window's first date. */
  public AbsoluteDate start() {
    return start;
  }

  /** The window's last date. */
  public AbsoluteDate end() {
    return end;
  }

  /** The window's length in seconds, always strictly positive. */
  public double durationSeconds() {
    return durationSeconds;
  }

  /** The track's left edge, in the widget's local space. */
  public float x0() {
    return x0;
  }

  /** The track's width in pixels. */
  public float width() {
    return width;
  }

  /**
   * Projects a date onto the track.
   *
   * <p>The clamp is not a defensive precaution: it is the specified behaviour of the {@code now}
   * indicator, which pins itself to whichever bound it has passed (§5.3).
   *
   * @param date the date to project
   * @return an x within {@code [x0, x0 + width]}
   */
  public float timeToX(AbsoluteDate date) {
    return offsetToX(date.durationFrom(start));
  }

  /**
   * Projects an offset from {@link #start()} onto the track.
   *
   * @param secondsFromStart seconds elapsed since the window's start, any sign
   * @return an x within {@code [x0, x0 + width]}
   */
  public float offsetToX(double secondsFromStart) {
    return x0 + width * (float) clamp01(secondsFromStart / durationSeconds);
  }

  /**
   * Reads a date off the track.
   *
   * @param x an x in the widget's local space, clamped into the track
   * @return the corresponding date, within {@code [start, end]}
   */
  public AbsoluteDate xToTime(float x) {
    return start.shiftedBy(durationSeconds * clamp01((x - x0) / (double) width));
  }

  private static double clamp01(double v) {
    if (v < 0.0 || Double.isNaN(v)) {
      return 0.0;
    }
    return Math.min(v, 1.0);
  }

  /**
   * One major graduation.
   *
   * @param offsetSeconds seconds elapsed since the window's start
   * @param x the graduation's x in the widget's local space
   * @param label the {@code T+…} caption
   */
  public record Tick(double offsetSeconds, float x, String label) {}

  /**
   * Upper bound on the number of major graduations. The design target is 6 to 10; only the two gaps
   * in {@link #STEPS} (a window just over 10 min, a window of a few weeks) land under 6, and no
   * step in the table can be chosen without honouring this bound.
   */
  public static final int MAX_MAJOR_TICKS = 10;

  /**
   * The round step values a graduation may take, ascending. Deliberately not a computed "nice
   * number" ladder: a time axis reads in units a human names, and 3 h has to be available where a
   * decimal ladder would offer 2 h or 5 h.
   */
  private static final double[] STEPS = {
    1, 5, 15, 30, 60, 300, 900, 1800, 3600, 10800, 21600, 43200, 86400, 172800, 604800, 1209600,
    2592000
  };

  /**
   * The graduation step for this window: the smallest table value whose graduation count fits in
   * {@link #MAX_MAJOR_TICKS}. Windows longer than the table's reach (beyond ~10 months) keep
   * extending in whole multiples of the table's largest step ({@code STEPS[STEPS.length - 1]}, 30
   * days) — a round number of months rather than a single fixed step that would silently blow
   * through the tick budget on a multi-year mission.
   *
   * <p>The fixed count of 21 graduations that {@code ScrubberTrack} draws is deliberately not
   * reused — a count that does not depend on the window is exactly what made the existing scrubber
   * decorative.
   *
   * @return the step in seconds
   */
  public double tickStepSeconds() {
    for (double step : STEPS) {
      if (tickCount(step) <= MAX_MAJOR_TICKS) {
        return step;
      }
    }
    double base = STEPS[STEPS.length - 1];
    double step = base;
    while (tickCount(step) > MAX_MAJOR_TICKS) {
      step += base;
    }
    return step;
  }

  /**
   * The major graduations of this window, from {@code T+0} upward.
   *
   * @return the graduations, in chronological order; never empty
   */
  public List<Tick> majorTicks() {
    double step = tickStepSeconds();
    // tickStepSeconds() only ever returns a step whose count already fits MAX_MAJOR_TICKS, so
    // narrowing to int here is safe — the unsafe narrowing this class used to do was inside
    // tickCount() itself, see the note there.
    int count = (int) tickCount(step);
    List<Tick> ticks = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      double offset = i * step;
      ticks.add(new Tick(offset, offsetToX(offset), formatTickLabel(offset)));
    }
    return List.copyOf(ticks);
  }

  /**
   * The number of graduations a step produces over this window, computed in {@code long}.
   *
   * <p>A multi-decade mission window at the smallest table step (1 s) overflows {@code int}: the
   * true count is in the billions, so a naive {@code (int)} cast would silently saturate and then
   * wrap negative on the {@code + 1}. {@link #tickStepSeconds()} tries that 1 s step first, so an
   * overflowed count would have looked like a suspiciously good — and wrong — fit, handing {@link
   * #majorTicks()} a negative size and an {@code IllegalArgumentException} from {@code ArrayList}.
   * Staying in {@code long} until the step is already known to be small removes the overflow
   * instead of hiding it.
   */
  private long tickCount(double step) {
    return (long) Math.floor(durationSeconds / step) + 1L;
  }

  /**
   * Formats a graduation caption: relative to the window's start, at most two units, truncated
   * downward. The absolute UTC date deliberately appears only in the tooltip — the capsule's own
   * clock stays the application's absolute reference (§7.2).
   *
   * @param secondsFromStart the offset to format, in seconds
   * @return {@code "T+0"}, {@code "T+45 s"}, {@code "T+12 min"}, {@code "T+2 d 12 h"}…
   */
  public static String formatTickLabel(double secondsFromStart) {
    double s = Math.abs(secondsFromStart);
    if (s < 1.0) {
      return "T+0";
    }
    return "T+" + twoUnits(s, false);
  }

  /**
   * Formats the gap shown beside a pinned {@code now} indicator: same truncation rule as a
   * graduation, but the second unit is zero-padded and a gap under ten seconds keeps one decimal,
   * because that is the range in which a pinned indicator is about to unpin (§5.3).
   *
   * @param seconds the gap, sign ignored
   * @return {@code "4.2 s"}, {@code "18 min 20 s"}, {@code "3 d 04 h"}…
   */
  public static String formatGap(double seconds) {
    double s = Math.abs(seconds);
    if (s < 10.0) {
      return String.format(Locale.ROOT, "%.1f s", s);
    }
    return twoUnits(s, true);
  }

  /**
   * Shared body of both formatters: the largest unit that fits, then the remainder in the next one
   * down, both truncated toward zero.
   *
   * @param s a non-negative number of seconds
   * @param padSecondUnit whether the second unit is zero-padded to two digits
   * @return the formatted body, without any {@code T+} prefix
   */
  private static String twoUnits(double s, boolean padSecondUnit) {
    String fmt = padSecondUnit ? "%d %s %02d %s" : "%d %s %d %s";
    if (s < 60.0) {
      return (long) s + " s";
    }
    if (s < 3600.0) {
      long min = (long) (s / 60.0);
      long sec = (long) (s - min * 60.0);
      return sec == 0 && !padSecondUnit
          ? min + " min"
          : String.format(Locale.ROOT, fmt, min, "min", sec, "s");
    }
    if (s < 86400.0) {
      long h = (long) (s / 3600.0);
      long min = (long) ((s - h * 3600.0) / 60.0);
      return min == 0 && !padSecondUnit
          ? h + " h"
          : String.format(Locale.ROOT, fmt, h, "h", min, "min");
    }
    long d = (long) (s / 86400.0);
    long h = (long) ((s - d * 86400.0) / 3600.0);
    return h == 0 && !padSecondUnit ? d + " d" : String.format(Locale.ROOT, fmt, d, "d", h, "h");
  }
}
