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
   * The time resolution this problem's optimum and edges are worth refining to, which only the
   * problem can know either.
   *
   * <p><b>The same argument as {@link #coarseStep()}, on the other axis, and it is not the same
   * number.</b> The sweep step must resolve the <em>minimum</em>; this must resolve the
   * <em>window</em>, and the two scales can be four orders of magnitude apart: an Earth launch has
   * one alignment per sidereal day, so it is bracketed by an hourly sweep, while the slot around it
   * is minutes wide and the instant is decided to the second. A default of a tenth of the sweep
   * step would ask for six minutes there — coarser than the window itself, which is the silent kind
   * of wrong.
   *
   * <p>The default is that tenth, because it is right whenever the merit function has one scale
   * only, which is the common case.
   *
   * @return the refinement resolution
   */
  default Duration refinementPrecision() {
    return coarseStep().dividedBy(10L);
  }

  /**
   * The interval after which the same opportunity comes back, which only the problem can know.
   *
   * <p><b>The third scale, and the one a consumer sizes a horizon on.</b> {@link #coarseStep()}
   * says how finely to sample and {@link #refinementPrecision()} how finely to resolve; this says
   * how far one has to look to see anything at all. A caller picking its own horizon — a wizard
   * timeline showing "three days", say — would draw an empty axis on a monthly criterion, which is
   * the same silent failure {@link #coarseStep()} guards against on the other axis.
   *
   * <p>An order of magnitude, not a promise of periodicity: it sizes searches, it does not assert
   * that the windows are evenly spaced.
   *
   * @return the interval between two consecutive opportunities
   */
  Duration recurrence();

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
