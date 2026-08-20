package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * Turns a configured mission and an earliest date into the launch opportunity it should fly (MIS-2)
 * — the one place a {@link MissionSpec.EarthOrbit} meets {@link EarthLaunchWindowProblem}.
 *
 * <p><b>The date the user typed becomes a floor, not a choice.</b> A mission naming a target node
 * cannot leave whenever it likes: the pad meets that plane once per sidereal day and the rest of
 * the day costs kilometres per second. So the wizard's launch date is read as "no earlier than
 * this", and what is scheduled is the first opening at or after it.
 *
 * <p><b>The soonest window, not the cheapest.</b> {@link LaunchWindowSolver} orders by cost because
 * that is the general answer; here the alignments of consecutive days differ by less than a metre
 * per second — they are the same opportunity, repeated — so ordering by cost would push the launch
 * a day later for nothing. Operationally the next one wins.
 */
public final class EarthLaunchWindowPlanner {

  /**
   * How far past the requested date an opportunity is looked for. One sidereal day plus two hours:
   * the alignment recurs every 86 164 s, so this bracket holds one whatever the phase, and holding
   * more would only offer tomorrow's copy of the same window.
   */
  private static final Duration SEARCH_SPAN = Duration.ofHours(26);

  /**
   * How much dearer than the alignment an epoch may be and still count as inside the window (m/s).
   *
   * <p>Fifty, which buys ±116 s at the 0.438 m/s per second the cost climbs at from a 400 km orbit
   * at 51.6° — a slot of 3 min 52 s, measured. It is the width of the interval reported, not the
   * choice of the instant: the optimum is the optimum whatever the margin.
   */
  private static final double MARGIN = 50.0;

  /**
   * Slots to compute before picking the earliest. More than one because the requested date can fall
   * inside a window that is already closing, in which case the next day's is the real answer and
   * both have to be on the table.
   */
  private static final int CANDIDATES = 5;

  private EarthLaunchWindowPlanner() {}

  /**
   * The first opportunity at or after {@code earliest} for a mission that names a target node.
   *
   * <p><b>Closed form throughout</b> — some ninety evaluations of a vector angle, microseconds each
   * — which is why the caller may run it on the render thread where the mission is created.
   *
   * @param spec the mission being scheduled
   * @param earliest the date the user asked for, read as a floor
   * @return the window to fly, or empty when the mission waits for no plane
   */
  public static Optional<LaunchWindow> nextOpportunity(
      MissionSpec.EarthOrbit spec, AbsoluteDate earliest) {
    if (!spec.hasTargetRaan()) {
      return Optional.empty();
    }
    EarthLaunchWindowProblem problem = EarthLaunchWindowRequest.from(spec).toProblem();
    LaunchWindowSearch search =
        new LaunchWindowSearch(
            earliest,
            SEARCH_SPAN,
            problem.coarseStep(),
            problem.refinementPrecision(),
            // No absolute cap: what a plane residual may cost is not a figure a mission writes
            // down, and the floor it is measured against changes with every pad (see
            // EarthLaunchWindowProblem). The margin is the whole acceptance rule here.
            Double.POSITIVE_INFINITY,
            MARGIN,
            CANDIDATES);
    return new LaunchWindowSolver(problem)
        .solve(search)
        .stream()
        .min(Comparator.comparing(LaunchWindow::date));
  }

  /**
   * The next {@code count} opportunities at or after {@code earliest}, in chronological order.
   *
   * <p><b>Chronological and not by cost</b>, because that is what a timeline draws; {@link
   * LaunchWindowSolver} orders by cost, which on this criterion ranks copies of the same
   * opportunity a metre per second apart.
   *
   * <p>The horizon is not a parameter: {@link LaunchWindowSearch#forOpportunities} derives it from
   * the problem's recurrence, so a caller counts opportunities and never days.
   *
   * <p><b>The cut to {@code count} is made here, on the date, and not by the search.</b> The
   * factory deliberately asks the solver for one slot more than wanted, because the solver's own
   * truncation is by cost; sorting by date and cutting here is what makes "the next three" mean the
   * next three.
   *
   * @param request the mission's window inputs
   * @param earliest the date the user asked for, read as a floor
   * @param count how many opportunities to keep
   * @return the opportunities found, possibly fewer than asked for, never null
   */
  public static List<LaunchWindow> nextOpportunities(
      EarthLaunchWindowRequest request, AbsoluteDate earliest, int count) {
    EarthLaunchWindowProblem problem = request.toProblem();
    LaunchWindowSearch search =
        LaunchWindowSearch.forOpportunities(
            earliest, problem, count, Double.POSITIVE_INFINITY, MARGIN);
    return new LaunchWindowSolver(problem)
        .solve(search)
        .stream()
        .sorted(Comparator.comparing(LaunchWindow::date))
        .limit(count)
        .toList();
  }
}
