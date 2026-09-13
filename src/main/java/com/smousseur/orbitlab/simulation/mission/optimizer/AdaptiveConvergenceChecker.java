package com.smousseur.orbitlab.simulation.mission.optimizer;

import org.hipparchus.optim.ConvergenceChecker;
import org.hipparchus.optim.PointValuePair;
import org.hipparchus.util.FastMath;

/**
 * Convergence checker for CMA-ES runs with adaptive early-kill logic.
 *
 * <ul>
 *   <li>Never converges before {@link #DEFAULT_MIN_ITERS_BEFORE_CONVERGE} iterations.
 *   <li>If {@code earlyKill} is enabled, aborts runs stuck above the bad-basin threshold after 300
 *       iterations.
 *   <li>Does not converge prematurely if acceptable cost has not been reached and fewer than 500
 *       iterations have run.
 *   <li>Standard convergence: cost improvement below tolerance thresholds.
 * </ul>
 */
final class AdaptiveConvergenceChecker implements ConvergenceChecker<PointValuePair> {

  /**
   * Generations a run must clear before it may converge.
   *
   * <p>Because rule 3 already holds any run still above {@code acceptableCost} to 500 generations,
   * this floor only governs runs that have <b>already reached</b> the acceptable cost: it is what
   * keeps a well-seeded run (one that starts at or below the threshold) from declaring victory at its
   * start point and returning the seed verbatim, angles unexplored (see {@code CMAESRunExecutor}).
   *
   * <p><b>{@code 50} since OPT-1 / B2</b> (spec {@code docs/optimization/09-conception-B2.md}, mesures
   * {@code 10-mesures-B2.md}). The bench sweep found the GT search stalls around generation 40-50, so
   * the historical {@code 100} spent ~14 % of its evaluations spinning past a converged, acceptable
   * solution. {@code 50} captures the full gain (the sweep plateaus below it) while staying clear of
   * the seed-verbatim trap (evaluations stayed at full search depth, no collapse), verdict-neutral
   * (largest move 1.4 km on the PRECISE apogee, well under REL-18). The bench overrides it through
   * {@link #MIN_CONVERGE_ITERS_PROPERTY}, defaulting here otherwise.
   */
  static final int DEFAULT_MIN_ITERS_BEFORE_CONVERGE = 50;

  /** System property the B2 bench sweep sets to override {@link #DEFAULT_MIN_ITERS_BEFORE_CONVERGE}. */
  static final String MIN_CONVERGE_ITERS_PROPERTY = "orbitlab.opt.minConvergeIters";

  private static final int BAD_BASIN_MIN_ITERS = 300;
  private static final double BAD_BASIN_KILL_THRESHOLD = 1.0;

  private final boolean earlyKill;
  private final double acceptableCost;
  private final double absoluteTolerance;
  private final double relativeTolerance;
  private final int minItersBeforeConverge;

  private int iterationCount = 0;

  /**
   * Creates a new adaptive convergence checker.
   *
   * @param earlyKill whether to enable early termination of runs stuck in bad basins
   * @param acceptableCost cost threshold below which a solution is considered acceptable
   * @param absoluteTolerance absolute improvement threshold for convergence
   * @param relativeTolerance relative improvement threshold for convergence
   */
  AdaptiveConvergenceChecker(
      boolean earlyKill,
      double acceptableCost,
      double absoluteTolerance,
      double relativeTolerance) {
    this.earlyKill = earlyKill;
    this.acceptableCost = acceptableCost;
    this.absoluteTolerance = absoluteTolerance;
    this.relativeTolerance = relativeTolerance;
    this.minItersBeforeConverge = resolveMinIters();
  }

  /**
   * Optional system-property override for the convergence floor, used only by the OPT-1 / B2 bench
   * sweep ({@link #MIN_CONVERGE_ITERS_PROPERTY}). Absent in production, where {@link
   * #DEFAULT_MIN_ITERS_BEFORE_CONVERGE} wins. A present-but-invalid value fails fast rather than
   * silently defaulting, so a typo in a sweep cannot corrupt a measured row.
   */
  private static int resolveMinIters() {
    String raw = System.getProperty(MIN_CONVERGE_ITERS_PROPERTY);
    if (raw == null) {
      return DEFAULT_MIN_ITERS_BEFORE_CONVERGE;
    }
    int value;
    try {
      value = Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid " + MIN_CONVERGE_ITERS_PROPERTY + " override '" + raw + "': not an integer", e);
    }
    if (value < 1) {
      throw new IllegalArgumentException(
          "Invalid " + MIN_CONVERGE_ITERS_PROPERTY + " override " + value + ": must be >= 1");
    }
    return value;
  }

  @Override
  public boolean converged(int iteration, PointValuePair previous, PointValuePair current) {
    iterationCount++;

    // Never converge too early
    if (iterationCount < minItersBeforeConverge) return false;

    // Early kill: abort runs stuck in bad basins
    if (earlyKill
        && iterationCount > BAD_BASIN_MIN_ITERS
        && current.getValue() > BAD_BASIN_KILL_THRESHOLD) {
      return true;
    }

    // Don't converge prematurely if we haven't reached acceptable cost yet
    if (current.getValue() > acceptableCost && iterationCount < 500) {
      return false;
    }

    // Standard convergence: cost stopped improving
    double diff = FastMath.abs(previous.getValue() - current.getValue());
    return diff <= absoluteTolerance
        || diff <= relativeTolerance * FastMath.abs(current.getValue());
  }
}
