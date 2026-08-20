package com.smousseur.orbitlab.simulation.mission.window;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.time.Duration;
import org.orekit.time.AbsoluteDate;

/**
 * The search criteria: where to look, how finely, and what counts as acceptable.
 *
 * <p><b>{@code precision} is a duration and not an iteration count</b>, because it is the only form
 * the caller can reason about: "to the minute" is a decision, "twelve golden-section steps" is an
 * implementation detail. It is also what bounds the cost — the refinement of one window takes
 * {@code log(step/precision)/log(1.618)} evaluations, so asking for a second instead of a minute
 * costs nine more evaluations, not sixty times more.
 *
 * <p><b>{@code maxDeltaV} is what makes a window a window.</b> Without an acceptance threshold a
 * merit function has minima but no edges, and the answer degenerates to a list of instants.
 *
 * <p><b>{@code margin} is the same threshold said relatively, and it exists because an absolute one
 * is blind on a flat criterion.</b> The translunar cost was measured swinging 14 m/s over a month
 * on a 3 183 m/s floor: any budget a mission would actually write down sits above the whole month,
 * so every epoch is "acceptable" and the slot degenerates to the searched range. A margin says
 * <em>how much more than the best</em> an epoch may cost, which is a decision that survives being
 * ported to a criterion of a different amplitude. Both bite at once — the effective threshold is
 * the lower of the two — so a caller can cap a search by what the tanks hold, by what it is willing
 * to waste, or by both.
 *
 * @param start the first date considered
 * @param span how far past {@code start} the search runs
 * @param step the coarse sweep step; must come from {@link LaunchWindowProblem#coarseStep()} unless
 *     the caller knows better
 * @param precision the time resolution the optimum and the edges are refined to
 * @param maxDeltaV the cost above which an epoch is not offered (m/s)
 * @param margin how much dearer than the cheapest epoch of the search an epoch may be and still be
 *     offered (m/s); {@link Double#POSITIVE_INFINITY} to be bound by {@code maxDeltaV} alone
 * @param maxWindows how many slots to return, cheapest first
 */
public record LaunchWindowSearch(
    AbsoluteDate start,
    Duration span,
    Duration step,
    Duration precision,
    double maxDeltaV,
    double margin,
    int maxWindows) {

  public LaunchWindowSearch {
    if (span.isNegative() || span.isZero()) {
      throw new OrbitlabException("the search span must be positive, got " + span);
    }
    if (step.isNegative() || step.isZero() || step.compareTo(span) > 0) {
      throw new OrbitlabException(
          "the sweep step must be positive and fit in the span, got " + step);
    }
    if (precision.isNegative() || precision.isZero() || precision.compareTo(step) >= 0) {
      // A precision coarser than the step would refine nothing; equal to it, the refinement is a
      // no-op that still costs its evaluations. Both are configuration mistakes, not edge cases.
      throw new OrbitlabException(
          "the precision must be positive and finer than the sweep step, got " + precision);
    }
    if (!(maxDeltaV > 0.0)) {
      throw new OrbitlabException("the delta-v budget must be positive, got " + maxDeltaV);
    }
    if (!(margin > 0.0)) {
      throw new OrbitlabException("the delta-v margin must be positive, got " + margin);
    }
    if (maxWindows < 1) {
      throw new OrbitlabException("at least one window must be asked for, got " + maxWindows);
    }
  }

  /**
   * A search bound by an absolute budget alone.
   *
   * @param start the first date considered
   * @param span how far past {@code start} the search runs
   * @param step the coarse sweep step
   * @param precision the time resolution the optimum and the edges are refined to
   * @param maxDeltaV the cost above which an epoch is not offered (m/s)
   * @param maxWindows how many slots to return, cheapest first
   */
  public LaunchWindowSearch(
      AbsoluteDate start,
      Duration span,
      Duration step,
      Duration precision,
      double maxDeltaV,
      int maxWindows) {
    this(start, span, step, precision, maxDeltaV, Double.POSITIVE_INFINITY, maxWindows);
  }

  /**
   * The usual search: both of the problem's own scales, and one window.
   *
   * @param start the first date considered
   * @param span how far past {@code start} to look
   * @param problem the problem whose sampling and refinement scales are adopted
   * @param maxDeltaV the acceptance budget (m/s)
   * @return the search
   */
  public static LaunchWindowSearch over(
      AbsoluteDate start, Duration span, LaunchWindowProblem problem, double maxDeltaV) {
    return new LaunchWindowSearch(
        start, span, problem.coarseStep(), problem.refinementPrecision(), maxDeltaV, 1);
  }

  /**
   * A search sized to show a given number of opportunities, on the problem's own three scales.
   *
   * <p><b>A count and not a duration.</b> The caller that wants "the next three" — the wizard's
   * timeline — must not be the one deciding what three is worth: three opportunities is three days
   * on an Earth plane alignment and three months on a translunar injection, and only {@link
   * LaunchWindowProblem#recurrence()} knows which. A horizon written by the caller draws an empty
   * axis the day the problem changes.
   *
   * <p>The span is {@code count} recurrences plus a twelfth of one. On an exactly periodic
   * criterion the recurrences alone would do; the extra twelfth covers the slot whose opening falls
   * inside the range while its optimum falls just past it.
   *
   * @param start the first date considered
   * @param problem the problem whose three scales are adopted
   * @param count how many opportunities to look for
   * @param maxDeltaV the absolute acceptance budget (m/s)
   * @param margin how much dearer than the cheapest epoch an offered one may be (m/s)
   * @return the search
   */
  public static LaunchWindowSearch forOpportunities(
      AbsoluteDate start, LaunchWindowProblem problem, int count, double maxDeltaV, double margin) {
    Duration recurrence = problem.recurrence();
    Duration span = recurrence.multipliedBy(count).plus(recurrence.dividedBy(12L));
    return new LaunchWindowSearch(
        start, span, problem.coarseStep(), problem.refinementPrecision(), maxDeltaV, margin, count);
  }

  /**
   * @return a copy of this search returning up to {@code count} windows
   */
  public LaunchWindowSearch withMaxWindows(int count) {
    return new LaunchWindowSearch(start, span, step, precision, maxDeltaV, margin, count);
  }

  /**
   * @param margin how much dearer than the cheapest epoch an offered one may be (m/s)
   * @return a copy of this search capped relatively as well as absolutely
   */
  public LaunchWindowSearch withMargin(double margin) {
    return new LaunchWindowSearch(start, span, step, precision, maxDeltaV, margin, maxWindows);
  }

  /**
   * @return the search span in seconds
   */
  public double spanSeconds() {
    return span.toNanos() / 1.0e9;
  }

  /**
   * @return the sweep step in seconds
   */
  public double stepSeconds() {
    return step.toNanos() / 1.0e9;
  }

  /**
   * @return the refinement resolution in seconds
   */
  public double precisionSeconds() {
    return precision.toNanos() / 1.0e9;
  }
}
