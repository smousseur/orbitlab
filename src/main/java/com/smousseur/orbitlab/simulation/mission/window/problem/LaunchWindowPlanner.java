package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowProblem;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.util.Comparator;
import java.util.List;
import org.orekit.time.AbsoluteDate;

/**
 * The next opportunities of any {@link LaunchWindowRequest}, in chronological order — what the
 * wizard's timeline draws, whatever it is aiming at (MIS-4 / L5 §4.2).
 *
 * <p><b>Generic because it always was.</b> The horizon is derived from {@link
 * LaunchWindowProblem#recurrence()} through {@link LaunchWindowSearch#forOpportunities}, and the
 * sampling and refinement scales come from the problem too; the only number this class writes down
 * is {@link #MARGIN}. What it does <b>not</b> touch is {@code
 * EarthLaunchWindowPlanner.nextOpportunity} — the singular, the path every mission is scheduled on,
 * whose 26 h, 50 m/s and five candidates are a measured triple that must not move.
 */
public final class LaunchWindowPlanner {

  /**
   * How much dearer than the cheapest epoch of the search an epoch may be and still be offered
   * (m/s). Fifty, measured on the Earth criterion: ±116 s at the 0.438 m/s per second the cost
   * climbs at from a 400 km orbit at 51.6°, a slot of 3 min 52. On the lunar criterion it carves a
   * slot out of a merit function swinging some 14 m/s over a month, which is the case a relative
   * threshold exists for — an absolute one is blind on a criterion that flat.
   */
  private static final double MARGIN = 50.0;

  private LaunchWindowPlanner() {}

  /**
   * The next {@code count} opportunities at or after {@code earliest}, in chronological order.
   *
   * <p><b>Chronological and not by cost</b>, because that is what a timeline draws; {@link
   * LaunchWindowSolver} orders by cost, which on these criteria ranks copies of the same
   * opportunity a metre per second apart.
   *
   * <p><b>The cut to {@code count} is made here, on the date, and not by the search.</b> The
   * factory deliberately asks the solver for one slot more than wanted, because the solver's own
   * truncation is by cost; sorting by date and cutting here is what makes "the next three" mean the
   * next three.
   *
   * @param request the window inputs
   * @param earliest the date the user asked for, read as a floor
   * @param count how many opportunities to keep
   * @return the opportunities found, possibly fewer than asked for, never null
   */
  public static List<LaunchWindow> nextOpportunities(
      LaunchWindowRequest request, AbsoluteDate earliest, int count) {
    LaunchWindowProblem problem = request.toProblem();
    LaunchWindowSearch search =
        LaunchWindowSearch.forOpportunities(
            earliest,
            problem,
            count,
            // No absolute cap: what a residual may cost is not a figure a mission writes down, and
            // the floor it is measured against changes with every pad. The margin is the whole
            // acceptance rule here.
            Double.POSITIVE_INFINITY,
            MARGIN);
    return new LaunchWindowSolver(problem)
        .solve(search).stream()
            .sorted(Comparator.comparing(LaunchWindow::date))
            .limit(count)
            .toList();
  }
}
