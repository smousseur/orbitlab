package com.smousseur.orbitlab.states.mission;

import com.smousseur.orbitlab.engine.Easing;
import com.smousseur.orbitlab.engine.RetrogradeFlipConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.orekit.time.AbsoluteDate;

/**
 * When the primary is drawn turned around for a retrograde burn, and by how much: an angle from 0
 * (nose along its flight direction) to π (tail first), as a pure function of the date, so the turn
 * replays backward when the clock is rewound and lands on the same angle whatever order the dates
 * are drawn in.
 *
 * <p>A burn is retrograde when its energy-weighted cosine between thrust and velocity is below the
 * configured threshold, whatever stage flies it; every other burn, a plane correction across the
 * velocity included, is drawn nose first. Around each retrograde burn the primary turns over during
 * the {@code flipSeconds} before it, flies it turned, and turns back during the {@code flipSeconds}
 * after it. It never turns while anything else happens that the turn would spoil, which bound each
 * half: another burn, whose plume must leave the right way; any <em>barrier</em> date the caller
 * names — a change of silhouette, whose pivot would jump mid-turn, the first heated sample of a
 * re-entry, whose plasma is placed on the nose — and either end of the ephemeris. A half that has
 * no room is squeezed into what there is; two retrograde burns whose turns would overlap, with
 * nothing between them, are merged, the primary staying turned in between.
 */
final class RetrogradeFlips {

  /** No turn at all: the schedule of a trajectory without a retrograde burn, or not yet built. */
  static final RetrogradeFlips NONE = new RetrogradeFlips(null, List.of(), Easing.LINEAR);

  /**
   * One turn, in seconds from the schedule's epoch: turning over from {@code turnStart} to {@code
   * turnedFrom}, turned until {@code turnedUntil}, turning back until {@code turnEnd}.
   */
  record Window(double turnStart, double turnedFrom, double turnedUntil, double turnEnd) {}

  private final AbsoluteDate epoch;
  private final List<Window> windows;
  private final Easing easing;

  private RetrogradeFlips(AbsoluteDate epoch, List<Window> windows, Easing easing) {
    this.epoch = epoch;
    this.windows = windows;
    this.easing = easing;
  }

  /**
   * Builds the schedule of a trajectory.
   *
   * @param burns the trajectory's burns, in flight order
   * @param barriers the dates no turn may straddle besides the burns, in any order
   * @param start the first date of the trajectory
   * @param end the last date of the trajectory
   * @param config the half turn's duration and curve, and the retrograde threshold
   * @return the schedule
   */
  static RetrogradeFlips of(
      List<BurnTimeline.Burn> burns,
      Collection<AbsoluteDate> barriers,
      AbsoluteDate start,
      AbsoluteDate end,
      RetrogradeFlipConfig config) {
    Objects.requireNonNull(burns, "burns");
    Objects.requireNonNull(barriers, "barriers");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(config, "config");
    double last = end.durationFrom(start);
    double[] fences = new double[barriers.size()];
    int f = 0;
    for (AbsoluteDate barrier : barriers) {
      fences[f++] = barrier.durationFrom(start);
    }
    double x = config.flipSeconds();
    List<Window> windows = new ArrayList<>();
    int previousRetrograde = -2;
    for (int i = 0; i < burns.size(); i++) {
      BurnTimeline.Burn burn = burns.get(i);
      if (!(burn.cosTheta() < config.retrogradeCosine())) {
        continue;
      }
      double from = burn.start().durationFrom(start);
      double until = burn.end().durationFrom(start);
      double before = i > 0 ? burns.get(i - 1).end().durationFrom(start) : 0.0;
      double after = i + 1 < burns.size() ? burns.get(i + 1).start().durationFrom(start) : last;
      for (double fence : fences) {
        if (fence <= from) {
          before = Math.max(before, fence);
        }
        if (fence >= until) {
          after = Math.min(after, fence);
        }
      }
      Window window =
          new Window(
              Math.max(from - x, Math.max(before, 0.0)),
              from,
              until,
              Math.min(until + x, Math.min(after, last)));
      if (previousRetrograde == i - 1 && mergeable(windows.getLast(), window, fences)) {
        Window previous = windows.removeLast();
        window = new Window(previous.turnStart(), previous.turnedFrom(), until, window.turnEnd());
      }
      windows.add(window);
      previousRetrograde = i;
    }
    return new RetrogradeFlips(start, List.copyOf(windows), config.easing());
  }

  /**
   * Whether the turn of a retrograde burn merges with the turn of the retrograde burn right before
   * it, no other burn between them: no barrier lies between the two, and turning back from the
   * first would still be under way when turning over for the second begins.
   */
  private static boolean mergeable(Window previous, Window next, double[] fences) {
    for (double fence : fences) {
      if (fence > previous.turnedUntil() && fence < next.turnedFrom()) {
        return false;
      }
    }
    return previous.turnEnd() >= next.turnStart();
  }

  /**
   * The turn at a date.
   *
   * @param date the date to look up
   * @return the angle the primary is turned by, from 0 (nose first) to π (tail first)
   */
  double angleAt(AbsoluteDate date) {
    if (windows.isEmpty()) {
      return 0.0;
    }
    double t = date.durationFrom(epoch);
    Window window = windowAt(t);
    if (window == null || t >= window.turnEnd()) {
      return 0.0;
    }
    if (t < window.turnedFrom()) {
      double progress = (t - window.turnStart()) / (window.turnedFrom() - window.turnStart());
      return Math.PI * easing.apply((float) progress);
    }
    if (t < window.turnedUntil()) {
      return Math.PI;
    }
    double progress = (t - window.turnedUntil()) / (window.turnEnd() - window.turnedUntil());
    return Math.PI * (1.0 - easing.apply((float) progress));
  }

  /** The schedule's turns, in date order, for the measurement probes. */
  List<Window> windows() {
    return windows;
  }

  /** The last window that starts at or before {@code t}, or {@code null} when none does. */
  private Window windowAt(double t) {
    int low = 0;
    int high = windows.size() - 1;
    Window found = null;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      Window window = windows.get(mid);
      if (window.turnStart() <= t) {
        found = window;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return found;
  }
}
