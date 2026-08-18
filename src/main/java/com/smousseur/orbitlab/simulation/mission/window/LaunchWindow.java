package com.smousseur.orbitlab.simulation.mission.window;

import java.time.Duration;
import org.orekit.time.AbsoluteDate;

/**
 * An open slot: the interval over which the cost stays inside the search's budget, and the instant
 * inside it that costs least.
 *
 * <p><b>An interval and not a date</b>, because that is what the word means and what the wizard has
 * to draw: an instant cannot be aimed at by a countdown, and the width of the slot <em>is</em> the
 * operational margin. A caller that only wants a date reads {@link #best}.
 *
 * @param opening the first instant whose cost is within budget
 * @param best the cheapest candidate of the slot
 * @param closing the last instant whose cost is within budget
 */
public record LaunchWindow(AbsoluteDate opening, LaunchWindowCandidate best, AbsoluteDate closing) {

  /**
   * @return how long the slot stays open
   */
  public Duration duration() {
    return Duration.ofMillis(Math.round(closing.durationFrom(opening) * 1000.0));
  }

  /**
   * @return the cheapest date of the slot — the one a mission is scheduled on
   */
  public AbsoluteDate date() {
    return best.epoch();
  }
}
