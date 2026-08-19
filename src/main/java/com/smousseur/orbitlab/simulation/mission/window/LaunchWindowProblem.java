package com.smousseur.orbitlab.simulation.mission.window;

import java.time.Duration;
import org.orekit.time.AbsoluteDate;

/**
 * What a launch window is being searched <em>for</em>: the abstraction MIS-2 turns on.
 *
 * <p>An implementation answers one question — <b>what would it cost to leave at this instant, and
 * can it be done at all?</b> — and declares the timescale on which that answer varies. Everything
 * else (scanning, bracketing, refining, cutting the window edges) belongs to {@link
 * LaunchWindowSolver} and is the same for every target.
 *
 * <p><b>The cost is always a Δv in m/s</b>, never a plane angle or a distance. Heterogeneous
 * criteria — plane alignment, target RAAN, Earth-Moon geometry — cannot be compared or thresholded
 * until they are in one unit, and the unit that carries meaning here is the one the propellant
 * budget is written in. A residual out-of-plane angle θ is worth {@code 2·v·sin(θ/2)}; state it
 * that way and the acceptance threshold becomes a budget rather than a tuning constant.
 *
 * <p><b>The initial state is not a parameter of the search</b>, and that is the reason it does not
 * appear anywhere in this package: it is a <em>function of the candidate date</em>. A translunar
 * parking orbit is built around where the Moon will be; an ascent state is the launch site rotated
 * to that epoch. An implementation derives it from the epoch it is handed.
 */
public interface LaunchWindowProblem {

  /**
   * @return a human-readable name, used in logs and in the wizard's window timeline
   */
  String name();

  /**
   * Evaluates one candidate epoch.
   *
   * <p><b>Must never throw</b> for an epoch that simply does not work: an unreachable geometry is a
   * {@link LaunchWindowCandidate#refused refusal}, which is data the solver walks past, whereas an
   * exception would abort a sweep because one of its two hundred samples was bad. Throwing stays
   * legitimate for a broken configuration — a negative mass, a missing ephemeris.
   *
   * <p>Called on the order of {@code span/step + 30·windows} times, so it is what sets the cost of
   * a search. Keep it closed-form; see {@link #confirm}.
   *
   * @param epoch the candidate launch (or injection) date
   * @return the cost of leaving at that instant, or a refusal
   */
  LaunchWindowCandidate evaluate(AbsoluteDate epoch);

  /**
   * The coarse sweep step this problem needs to be sampled at, which only the problem can know.
   *
   * <p><b>This is a sampling theorem, not a preference.</b> The step must be shorter than half the
   * narrowest feature of the merit function, or the sweep steps over the windows entirely: the
   * Earth-Moon geometry is monthly and tolerates hours, an ISS plane alignment recurs about twice a
   * day and is minutes wide. A search that imposed its own step would silently return "no window"
   * on the second case.
   *
   * @return the coarse sampling step
   */
  Duration coarseStep();

  /**
   * Confirms a refined candidate under the full model — the expensive second tier.
   *
   * <p>The default does nothing, which is the honest answer for a problem whose {@link #evaluate}
   * is already the truth. It exists for the translunar case, where the closed-form Lambert cost is
   * a good ranking and a poor verdict: what actually refuses an epoch there is the flown perilune
   * floor, and measuring it costs some thirty propagations of four days. Screening on the cheap
   * criterion and confirming only the handful of survivors is what keeps a sixty-day search under a
   * minute instead of over an hour.
   *
   * @param candidate the refined candidate to confirm
   * @return the confirmed candidate, possibly re-costed, or a refusal
   */
  default LaunchWindowCandidate confirm(LaunchWindowCandidate candidate) {
    return candidate;
  }
}
