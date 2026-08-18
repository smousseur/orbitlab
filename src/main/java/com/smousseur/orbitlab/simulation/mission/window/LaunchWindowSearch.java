package com.smousseur.orbitlab.simulation.mission.window;

import com.smousseur.orbitlab.core.OrbitlabException;
import org.orekit.time.AbsoluteDate;

import java.time.Duration;

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
 * @param start the first date considered
 * @param span how far past {@code start} the search runs
 * @param step the coarse sweep step; must come from {@link LaunchWindowProblem#coarseStep()} unless
 *     the caller knows better
 * @param precision the time resolution the optimum and the edges are refined to
 * @param maxDeltaV the cost above which an epoch is not offered (m/s)
 * @param maxWindows how many slots to return, cheapest first
 */
public record LaunchWindowSearch(
    AbsoluteDate start,
    Duration span,
    Duration step,
    Duration precision,
    double maxDeltaV,
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
    if (maxWindows < 1) {
      throw new OrbitlabException("at least one window must be asked for, got " + maxWindows);
    }
  }

  /**
   * The usual search: the problem's own sweep step, a precision a tenth of it, and one window.
   *
   * @param start the first date considered
   * @param span how far past {@code start} to look
   * @param problem the problem whose sampling scale is adopted
   * @param maxDeltaV the acceptance budget (m/s)
   * @return the search
   */
  public static LaunchWindowSearch over(
      AbsoluteDate start, Duration span, LaunchWindowProblem problem, double maxDeltaV) {
    Duration step = problem.coarseStep();
    return new LaunchWindowSearch(start, span, step, step.dividedBy(10L), maxDeltaV, 1);
  }

  /**
   * @return a copy of this search returning up to {@code count} windows
   */
  public LaunchWindowSearch withMaxWindows(int count) {
    return new LaunchWindowSearch(start, span, step, precision, maxDeltaV, count);
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
