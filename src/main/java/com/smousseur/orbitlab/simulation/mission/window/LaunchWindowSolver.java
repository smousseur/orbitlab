package com.smousseur.orbitlab.simulation.mission.window;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.orekit.time.AbsoluteDate;

/**
 * Finds the open slots of a {@link LaunchWindowProblem} over a time range (MIS-2).
 *
 * <p><b>Three passes, and the same three whatever the target is</b> — which is why this class is
 * concrete and has no strategy seam: a sweep over ℝ knows nothing about the Moon, and only the
 * merit function changes.
 *
 * <ol>
 *   <li><b>Coarse sweep</b> at the problem's own step, producing a grid of costs.
 *   <li><b>Bracketing</b> — every grid sample cheaper than both its neighbours brackets a local
 *       minimum in {@code [t(i-1), t(i+1)]}. Three points are the whole detector.
 *   <li><b>Golden-section refinement</b> inside each bracket down to the requested precision, then
 *       a <b>bisection on the acceptance threshold</b> outwards from the optimum for the two edges.
 * </ol>
 *
 * <p><b>Why golden section rather than the obvious alternatives.</b>
 *
 * <ul>
 *   <li><em>A fine brute-force sweep</em> costs {@code span/precision} evaluations — sixty days to
 *       the minute is 86 400 of them. Free for a closed-form criterion, unaffordable the day a
 *       problem propagates, and this class must not be re-chosen when that happens.
 *   <li><em>A derivative method</em> (Newton, gradient) needs a derivative the problem cannot
 *       supply analytically; approximating it by finite differences is two evaluations per step for
 *       a method that diverges where the merit function is flat — and it <em>is</em> flat around
 *       the optimum, that being the definition of one.
 *   <li><em>A global optimizer</em> (CMA-ES, already in the repository) is non-deterministic and
 *       returns one point, when the question is a list of intervals over a one-dimensional range.
 *   <li><em>Golden section</em> is derivative-free, contracts by a fixed 0.618 per <b>single</b>
 *       evaluation, and cannot diverge inside a bracket. Roughly twenty-four evaluations take an
 *       eight-hour bracket to the second.
 * </ul>
 *
 * <p>Everything is done on offsets in seconds from the search start rather than on {@link
 * AbsoluteDate}, so the caching and the interval arithmetic stay on one type.
 */
public class LaunchWindowSolver {
  private static final Logger logger = LogManager.getLogger(LaunchWindowSolver.class);

  /** {@code (√5 − 1)/2}: the contraction ratio that lets one of the two probes be reused. */
  private static final double GOLDEN_RATIO = 0.6180339887498949;

  /** Hard cap on bisection steps, so a pathological merit function cannot spin. */
  private static final int MAX_EDGE_STEPS = 40;

  private final LaunchWindowProblem problem;
  private final Map<Long, LaunchWindowCandidate> evaluations = new HashMap<>();
  private LaunchWindowSearch search;

  /**
   * @param problem the target whose windows are searched
   */
  public LaunchWindowSolver(LaunchWindowProblem problem) {
    this.problem = problem;
  }

  /**
   * Lists the open slots of this solver's problem, cheapest first.
   *
   * @param search the search criteria
   * @return the windows found, at most {@link LaunchWindowSearch#maxWindows()}, possibly empty
   */
  public List<LaunchWindow> solve(LaunchWindowSearch search) {
    this.search = search;
    this.evaluations.clear();

    double span = search.spanSeconds();
    double step = search.stepSeconds();
    int samples = (int) Math.floor(span / step) + 1;

    double[] grid = new double[samples];
    for (int i = 0; i < samples; i++) {
      grid[i] = costAt(i * step);
    }

    List<LaunchWindow> windows = new ArrayList<>();
    for (int i = 0; i < samples; i++) {
      if (!bracketsMinimum(grid, i)) {
        continue;
      }
      double low = Math.max(0.0, (i - 1) * step);
      double high = Math.min(span, (i + 1) * step);
      LaunchWindowCandidate best = problem.confirm(evaluate(refine(low, high)));
      if (!best.feasible() || best.deltaV() > search.maxDeltaV()) {
        logger.debug(
            "[{}] the candidate near {} is not offered: {}",
            problem.name(),
            best.epoch(),
            best.feasible() ? best.deltaV() + " m/s over budget" : best.refusal());
        continue;
      }
      double centre = best.epoch().durationFrom(search.start());
      windows.add(
          new LaunchWindow(
              search.start().shiftedBy(edge(centre, -step, 0.0)),
              best,
              search.start().shiftedBy(edge(centre, step, span))));
    }

    windows.sort(Comparator.comparingDouble(w -> w.best().deltaV()));
    List<LaunchWindow> kept = windows.subList(0, Math.min(search.maxWindows(), windows.size()));
    logger.info(
        "[{}] {} window(s) over {} sampled at {}, {} evaluation(s)",
        problem.name(),
        kept.size(),
        search.span(),
        search.step(),
        evaluations.size());
    return List.copyOf(kept);
  }

  /**
   * Whether sample {@code i} is cheaper than both its neighbours, an out-of-range neighbour being
   * read as infinitely expensive so the two ends of the sweep can hold a window.
   */
  private boolean bracketsMinimum(double[] grid, int i) {
    if (!Double.isFinite(grid[i])) {
      return false;
    }
    double before = i > 0 ? grid[i - 1] : Double.POSITIVE_INFINITY;
    double after = i < grid.length - 1 ? grid[i + 1] : Double.POSITIVE_INFINITY;
    return grid[i] <= before && grid[i] <= after;
  }

  /**
   * Golden-section search on {@code [low, high]}, contracting to the requested precision.
   *
   * <p>Unimodality inside the bracket is assumed, and it is the sweep step that buys it: a bracket
   * two steps wide holds one feature when the step obeys {@link LaunchWindowProblem#coarseStep()}.
   * That is the single reason the step belongs to the problem and not to the search.
   *
   * @return the offset of the cheapest point found (s from the search start)
   */
  private double refine(double low, double high) {
    double a = low;
    double b = high;
    double c = b - GOLDEN_RATIO * (b - a);
    double d = a + GOLDEN_RATIO * (b - a);
    double costC = costAt(c);
    double costD = costAt(d);

    while (b - a > search.precisionSeconds()) {
      if (costC <= costD) {
        b = d;
        d = c;
        costD = costC;
        c = b - GOLDEN_RATIO * (b - a);
        costC = costAt(c);
      } else {
        a = c;
        c = d;
        costC = costD;
        d = a + GOLDEN_RATIO * (b - a);
        costD = costAt(d);
      }
    }
    return costC <= costD ? c : d;
  }

  /**
   * Walks outwards from the optimum in steps of {@code stride} until the cost leaves the budget,
   * then bisects the last acceptable and the first unacceptable point down to the precision.
   *
   * @param centre the optimum's offset (s)
   * @param stride the sweep step, negative to walk backwards (s)
   * @param bound the offset the walk stops at — 0 backwards, the span forwards (s)
   * @return the offset of the edge (s)
   */
  private double edge(double centre, double stride, double bound) {
    double inside = centre;
    double outside = centre + stride;
    while (Math.signum(stride) * (bound - outside) > 0.0 && withinBudget(outside)) {
      inside = outside;
      outside += stride;
    }
    if (!withinBudget(outside)) {
      for (int i = 0;
          i < MAX_EDGE_STEPS && Math.abs(outside - inside) > search.precisionSeconds();
          i++) {
        double middle = 0.5 * (inside + outside);
        if (withinBudget(middle)) {
          inside = middle;
        } else {
          outside = middle;
        }
      }
      return inside;
    }
    // The slot is still open at the end of the searched range: report the bound rather than
    // pretending the window closes there, and let the caller widen the span if it cares.
    return bound;
  }

  private boolean withinBudget(double offset) {
    return costAt(offset) <= search.maxDeltaV();
  }

  private double costAt(double offset) {
    return evaluate(offset).deltaV();
  }

  /**
   * One evaluation, memoised at the search's precision. Golden section and the two bisections
   * revisit the same instants; without the cache a window costs about a third more evaluations,
   * which matters as soon as an evaluation propagates.
   */
  private LaunchWindowCandidate evaluate(double offset) {
    long key = Math.round(offset / search.precisionSeconds());
    return evaluations.computeIfAbsent(
        key, k -> problem.evaluate(search.start().shiftedBy(k * search.precisionSeconds())));
  }
}
