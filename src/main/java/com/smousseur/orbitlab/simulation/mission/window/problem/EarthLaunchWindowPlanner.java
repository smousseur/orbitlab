package com.smousseur.orbitlab.simulation.mission.window.problem;

import com.smousseur.orbitlab.simulation.OrekitService;
import com.smousseur.orbitlab.simulation.mission.operation.LaunchPlane;
import com.smousseur.orbitlab.simulation.mission.operation.MissionSpec;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSearch;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindowSolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

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

  /**
   * The request {@link #warmUp()} solves: Kourou, 51.6°, 400 km circular — the wizard's default
   * site and a plane that site can reach. The numbers matter only in that they must pose a solvable
   * problem; what is being warmed is code paths and Orekit caches, not an answer anyone reads.
   */
  private static final EarthLaunchWindowRequest WARM_UP_REQUEST =
      new EarthLaunchWindowRequest(
          5.236,
          -52.769,
          14.0,
          LaunchPlane.ofDegrees(51.6),
          0.0,
          Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 400_000.0);

  private EarthLaunchWindowPlanner() {}

  /**
   * Solves one throwaway window, so that the first one a user asks for is not the first one this
   * JVM has ever computed.
   *
   * <p><b>The second tier of a measured freeze.</b> {@link OrekitService#warmUpFrames()} pays the
   * large one — 8 483 ms for the first ITRF. Once ITRF is warm, a first full {@link
   * #nextOpportunities} still costs 549 ms — 641 ms re-measured through this method — where
   * subsequent ones cost 4–9 ms. That half second is JIT and the caches a first solve fills, not
   * data loading, and the only way to absorb it is to solve once for nobody. The criterion itself
   * was never the cost: 250 evaluations measure 28 ms all told. Measured end to end, a user's first
   * solve falls from <b>9 134 ms without this warm-up to 40 ms with it</b>.
   *
   * <p><b>Why the solve is warmed here and the frame there.</b> {@link OrekitService} owns frames,
   * propagators and gravity models, and this package already depends on it; a warm-up reaching the
   * other way would make {@code simulation} depend on {@code simulation.mission.window}. The frame
   * touch belongs there because the frame does, the solve belongs here because the solve does.
   *
   * <p><b>Synchronous, like the frame warm-up</b> — it starts no thread of its own, so a test that
   * calls it decides when it runs. The application calls it off the render thread; see {@code
   * OrbitLabApplication.startFrameWarmUp}.
   */
  public static void warmUp() {
    nextOpportunities(WARM_UP_REQUEST, AbsoluteDate.J2000_EPOCH, 1);
  }

  /**
   * The first opportunity at or after {@code earliest} for a mission that names a target node.
   *
   * <p><b>Closed form throughout</b> — some ninety evaluations of a vector angle, microseconds
   * each, a few milliseconds for the solve — which is why the caller may run it on the render
   * thread where the mission is created. That holds <em>once the frames are warm</em>: the first
   * ITRF of a JVM costs 8 483 ms and lands on whatever thread reaches it first, which is why {@link
   * #warmUp()} exists and why the application calls it off the render thread at startup.
   *
   * <p><b>This method keeps its own span rather than deriving one through {@link
   * LaunchWindowSearch#forOpportunities}, and deliberately.</b> It is the path every mission
   * created through the wizard is scheduled on, and its 26 h, 50 m/s and five candidates are a
   * measured, non-regressing triple. {@code nextOpportunities} derives its horizon because that one
   * must scale with the count; this one must not move.
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
        .solve(search).stream().min(Comparator.comparing(LaunchWindow::date));
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
        .solve(search).stream()
            .sorted(Comparator.comparing(LaunchWindow::date))
            .limit(count)
            .toList();
  }
}
